from django.contrib import admin

from .models import PolicePresenceAlert


@admin.register(PolicePresenceAlert)
class PolicePresenceAlertAdmin(admin.ModelAdmin):
    list_display = ["id", "latitude", "longitude", "status", "reported_at", "expires_at"]
    list_filter = ["status"]
    readonly_fields = ["id", "reported_at", "created_at", "updated_at"]
