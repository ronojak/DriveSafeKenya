import math

from django.core.cache import cache
from django.utils import timezone
from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status

from .models import PolicePresenceAlert
from .serializers import PolicePresenceAlertSerializer

# ── Kenya bounding box ──────────────────────────────────────────────────────
KENYA_LAT_MIN, KENYA_LAT_MAX = -5.0, 5.5
KENYA_LON_MIN, KENYA_LON_MAX = 33.0, 42.5


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


def _has_already_acted(device_hash: str, alert_id: str, action: str) -> bool:
    """Prevent a device from voting on the same alert twice."""
    if not device_hash:
        return False
    key = f"pp_{action}_{alert_id}_{device_hash}"
    if cache.get(key):
        return True
    cache.set(key, True, timeout=3600)
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
def confirm_police_present(request, alert_id):
    device_hash = (request.data.get("device_hash") or "").strip()[:64]

    try:
        alert = PolicePresenceAlert.objects.get(pk=alert_id)
    except (PolicePresenceAlert.DoesNotExist, Exception):
        return Response({"error": "Alert not found"}, status=status.HTTP_404_NOT_FOUND)

    if alert.status in (PolicePresenceAlert.STATUS_EXPIRED, PolicePresenceAlert.STATUS_NOT_PRESENT):
        return Response({"error": "Alert is no longer active"}, status=status.HTTP_400_BAD_REQUEST)

    if _has_already_acted(device_hash, str(alert_id), "present"):
        return Response({"error": "Already confirmed"}, status=status.HTTP_429_TOO_MANY_REQUESTS)

    alert.present_confirmations += 1
    alert.status = PolicePresenceAlert.STATUS_CONFIRMED_PRESENT
    # Extend by 15 more minutes from now
    now = timezone.now()
    alert.confirmation_required_after = now + timezone.timedelta(minutes=15)
    alert.expires_at = now + timezone.timedelta(minutes=30)
    alert.save()
    return Response(PolicePresenceAlertSerializer(alert).data)


@api_view(["POST"])
def confirm_police_not_present(request, alert_id):
    device_hash = (request.data.get("device_hash") or "").strip()[:64]

    try:
        alert = PolicePresenceAlert.objects.get(pk=alert_id)
    except (PolicePresenceAlert.DoesNotExist, Exception):
        return Response({"error": "Alert not found"}, status=status.HTTP_404_NOT_FOUND)

    if alert.status in (PolicePresenceAlert.STATUS_EXPIRED, PolicePresenceAlert.STATUS_NOT_PRESENT):
        return Response({"error": "Alert is no longer active"}, status=status.HTTP_400_BAD_REQUEST)

    if _has_already_acted(device_hash, str(alert_id), "not_present"):
        return Response({"error": "Already voted"}, status=status.HTTP_429_TOO_MANY_REQUESTS)

    alert.not_present_confirmations += 1
    if alert.not_present_confirmations >= PolicePresenceAlert.NOT_PRESENT_THRESHOLD:
        alert.status = PolicePresenceAlert.STATUS_NOT_PRESENT
    alert.save()
    return Response(PolicePresenceAlertSerializer(alert).data)
