import math

from django.core.cache import cache
from django.db import IntegrityError
from django.utils import timezone
from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status

from .models import PolicePresenceAlert, PolicePresenceConfirmation
from .serializers import PolicePresenceAlertSerializer

# ── Kenya bounding box ──────────────────────────────────────────────────────
KENYA_LAT_MIN, KENYA_LAT_MAX = -5.0, 5.5
KENYA_LON_MIN, KENYA_LON_MAX = 33.0, 42.5

CONFIRM_PROXIMITY_METERS = 500
CONFIRM_RATE_LIMIT_SECONDS = 120
ALERT_TTL_MINUTES = 50


def _validate_kenya_coordinates(lat, lon):
    if lat is None or lon is None:
        return False, "latitude and longitude are required"
    try:
        lat, lon = float(lat), float(lon)
    except (TypeError, ValueError):
        return False, "latitude and longitude must be numbers"
    if lat == 0.0 and lon == 0.0:
        return False, "coordinates cannot be 0.0, 0.0"
    if not (KENYA_LAT_MIN <= lat <= KENYA_LAT_MAX):
        return False, f"latitude must be between {KENYA_LAT_MIN} and {KENYA_LAT_MAX} (Kenya)"
    if not (KENYA_LON_MIN <= lon <= KENYA_LON_MAX):
        return False, f"longitude must be between {KENYA_LON_MIN} and {KENYA_LON_MAX} (Kenya)"
    return True, None


def _haversine_meters(lat1, lon1, lat2, lon2):
    R = 6_371_000
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * R * math.atan2(math.sqrt(a), math.sqrt(1 - a))


# ── Rate limiting via Django cache ──────────────────────────────────────────

def _is_report_rate_limited(device_hash: str) -> bool:
    """Block more than one report per device every 2 minutes."""
    if not device_hash:
        return False
    key = f"pp_report_{device_hash}"
    if cache.get(key):
        return True
    cache.set(key, True, timeout=120)
    return False


def _is_confirm_rate_limited(device_hash: str) -> bool:
    """Block more than one confirmation per device every 2 minutes, across alerts."""
    if not device_hash:
        return False
    key = f"pp_confirm_rl_{device_hash}"
    if cache.get(key):
        return True
    cache.set(key, True, timeout=CONFIRM_RATE_LIMIT_SECONDS)
    return False


# ── Views ───────────────────────────────────────────────────────────────────

@api_view(["POST"])
def report_police_presence(request):
    lat = request.data.get("latitude")
    lon = request.data.get("longitude")
    device_hash = (request.data.get("device_hash") or "").strip()[:64]

    ok, err = _validate_kenya_coordinates(lat, lon)
    if not ok:
        return Response({"error": err}, status=status.HTTP_400_BAD_REQUEST)

    if _is_report_rate_limited(device_hash):
        return Response(
            {"error": "Too many reports. Please wait before reporting again."},
            status=status.HTTP_429_TOO_MANY_REQUESTS,
        )

    alert = PolicePresenceAlert.objects.create(
        latitude=float(lat),
        longitude=float(lon),
        reported_by_device_hash=device_hash,
    )
    return Response(PolicePresenceAlertSerializer(alert).data, status=status.HTTP_201_CREATED)


@api_view(["GET"])
def active_police_presence(request):
    try:
        lat = float(request.query_params["lat"])
        lon = float(request.query_params["lon"])
        radius = float(request.query_params.get("radius_meters", 10_000))
    except (KeyError, ValueError, TypeError):
        return Response(
            {"error": "lat, lon are required numeric query parameters"},
            status=status.HTTP_400_BAD_REQUEST,
        )

    ok, err = _validate_kenya_coordinates(lat, lon)
    if not ok:
        return Response({"error": err}, status=status.HTTP_400_BAD_REQUEST)

    now = timezone.now()
    candidates = PolicePresenceAlert.objects.exclude(
        status__in=[PolicePresenceAlert.STATUS_NOT_PRESENT, PolicePresenceAlert.STATUS_EXPIRED]
    ).filter(expires_at__gt=now)

    # Refresh time-based status in place and collect those within radius
    nearby = []
    for alert in candidates:
        if alert.not_present_confirmations >= PolicePresenceAlert.NOT_PRESENT_THRESHOLD:
            alert.status = PolicePresenceAlert.STATUS_NOT_PRESENT
            alert.save(update_fields=["status", "updated_at"])
            continue
        if now > alert.confirmation_required_after and alert.status == PolicePresenceAlert.STATUS_ACTIVE:
            alert.status = PolicePresenceAlert.STATUS_NEEDS_CONFIRMATION
            alert.save(update_fields=["status", "updated_at"])

        dist = _haversine_meters(lat, lon, alert.latitude, alert.longitude)
        if dist <= radius:
            nearby.append(alert)

    serializer = PolicePresenceAlertSerializer(nearby, many=True)
    return Response({"alerts": serializer.data})


@api_view(["POST"])
def confirm_police_presence(request, alert_id):
    lat = request.data.get("latitude")
    lon = request.data.get("longitude")
    device_hash = (request.data.get("device_hash") or "").strip()[:64]
    present = request.data.get("present")

    if not device_hash:
        return Response({"error": "device_hash is required"}, status=status.HTTP_400_BAD_REQUEST)

    if not isinstance(present, bool):
        return Response({"error": "present must be a boolean"}, status=status.HTTP_400_BAD_REQUEST)

    ok, err = _validate_kenya_coordinates(lat, lon)
    if not ok:
        return Response({"error": err}, status=status.HTTP_400_BAD_REQUEST)

    try:
        alert = PolicePresenceAlert.objects.get(pk=alert_id)
    except (PolicePresenceAlert.DoesNotExist, Exception):
        return Response({"error": "Alert not found"}, status=status.HTTP_404_NOT_FOUND)

    if alert.status in (PolicePresenceAlert.STATUS_EXPIRED, PolicePresenceAlert.STATUS_NOT_PRESENT):
        return Response({"error": "Alert is no longer active"}, status=status.HTTP_400_BAD_REQUEST)

    if device_hash == alert.reported_by_device_hash:
        return Response(
            {"error": "The reporting device cannot confirm its own report"},
            status=status.HTTP_400_BAD_REQUEST,
        )

    distance = _haversine_meters(float(lat), float(lon), alert.latitude, alert.longitude)
    if distance > CONFIRM_PROXIMITY_METERS:
        return Response(
            {"error": f"You must be within {CONFIRM_PROXIMITY_METERS}m of the location to confirm"},
            status=status.HTTP_400_BAD_REQUEST,
        )

    if PolicePresenceConfirmation.objects.filter(alert=alert, device_hash=device_hash).exists():
        return Response({"error": "Already confirmed"}, status=status.HTTP_429_TOO_MANY_REQUESTS)

    if _is_confirm_rate_limited(device_hash):
        return Response(
            {"error": "Too many confirmations. Please wait before confirming again."},
            status=status.HTTP_429_TOO_MANY_REQUESTS,
        )

    try:
        PolicePresenceConfirmation.objects.create(
            alert=alert,
            device_hash=device_hash,
            present=present,
            latitude=float(lat),
            longitude=float(lon),
        )
    except IntegrityError:
        return Response({"error": "Already confirmed"}, status=status.HTTP_429_TOO_MANY_REQUESTS)

    now = timezone.now()
    if present:
        alert.present_confirmations += 1
        alert.not_present_confirmations = 0
        alert.status = PolicePresenceAlert.STATUS_CONFIRMED_PRESENT
        alert.last_confirmed_at = now
    else:
        alert.not_present_confirmations += 1
        if alert.not_present_confirmations >= PolicePresenceAlert.NOT_PRESENT_THRESHOLD:
            alert.status = PolicePresenceAlert.STATUS_NOT_PRESENT
    alert.expires_at = now + timezone.timedelta(minutes=ALERT_TTL_MINUTES)
    alert.save()

    return Response(PolicePresenceAlertSerializer(alert).data)
