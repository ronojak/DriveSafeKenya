from django.contrib import admin

from .models import CameraZone, DatasetVersion


@admin.register(CameraZone)
class CameraZoneAdmin(admin.ModelAdmin):
    list_display = [
        "id", "road_name", "location_name", "speed_limit_kmh",
        "status", "verified", "last_updated",
    ]
    list_filter = ["status", "verified", "road_name", "camera_type"]
    search_fields = ["road_name", "location_name", "id"]
    list_editable = ["status", "verified"]


@admin.register(DatasetVersion)
class DatasetVersionAdmin(admin.ModelAdmin):
    list_display = ["version", "published_at", "is_active", "notes"]
    list_filter = ["is_active"]
