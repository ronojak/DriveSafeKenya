from rest_framework import serializers

from .models import CameraZone, DatasetVersion


class CameraZoneSerializer(serializers.ModelSerializer):
    roadName = serializers.CharField(source="road_name")
    locationName = serializers.CharField(source="location_name")
    speedLimitKmh = serializers.IntegerField(source="speed_limit_kmh", allow_null=True)
    minSpeedLimitKmh = serializers.IntegerField(source="min_speed_limit_kmh", allow_null=True)
    maxSpeedLimitKmh = serializers.IntegerField(source="max_speed_limit_kmh", allow_null=True)
    warningRadiusMeters = serializers.IntegerField(source="warning_radius_meters")
    cameraType = serializers.CharField(source="camera_type")
    lastUpdated = serializers.DateField(source="last_updated")

    class Meta:
        model = CameraZone
        fields = [
            "id", "roadName", "locationName", "latitude", "longitude",
            "speedLimitKmh", "minSpeedLimitKmh", "maxSpeedLimitKmh",
            "warningRadiusMeters", "cameraType", "direction", "status",
            "verified", "source", "lastUpdated",
        ]


class DatasetVersionSerializer(serializers.ModelSerializer):
    publishedAt = serializers.DateTimeField(source="published_at")
    isActive = serializers.BooleanField(source="is_active")

    class Meta:
        model = DatasetVersion
        fields = ["version", "publishedAt", "notes", "isActive"]
