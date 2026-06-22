from rest_framework.decorators import api_view
from rest_framework.response import Response

from .models import CameraZone, DatasetVersion
from .serializers import CameraZoneSerializer


@api_view(["GET"])
def health(request):
    return Response({"status": "ok"})


@api_view(["GET"])
def camera_zones_version(request):
    version = DatasetVersion.objects.filter(is_active=True).first()
    if not version:
        return Response({"version": None, "zoneCount": 0})
    zone_count = CameraZone.objects.filter(status="active").count()
    return Response({
        "version": version.version,
        "publishedAt": version.published_at.isoformat(),
        "zoneCount": zone_count,
    })


@api_view(["GET"])
def camera_zones_list(request):
    zones = CameraZone.objects.filter(status="active")
    serializer = CameraZoneSerializer(zones, many=True)
    version = DatasetVersion.objects.filter(is_active=True).first()
    return Response({
        "dataVersion": version.version if version else "1.0",
        "lastUpdated": version.published_at.isoformat() if version else None,
        "zones": serializer.data,
    })
