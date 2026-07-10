from rest_framework import serializers

from .models import PolicePresenceAlert


class PolicePresenceAlertSerializer(serializers.ModelSerializer):
    id = serializers.UUIDField(read_only=True)
    reported_at = serializers.DateTimeField(read_only=True)
    confirmation_required_after = serializers.DateTimeField(read_only=True)
    expires_at = serializers.DateTimeField(read_only=True)
    last_confirmed_at = serializers.DateTimeField(read_only=True, allow_null=True)
    present_confirmations = serializers.IntegerField(read_only=True)
    not_present_confirmations = serializers.IntegerField(read_only=True)

    class Meta:
        model = PolicePresenceAlert
        fields = [
            "id",
            "latitude",
            "longitude",
            "reported_at",
            "confirmation_required_after",
            "expires_at",
            "last_confirmed_at",
            "status",
            "present_confirmations",
            "not_present_confirmations",
            "source",
        ]
        read_only_fields = [
            "id",
            "reported_at",
            "confirmation_required_after",
            "expires_at",
            "last_confirmed_at",
            "status",
            "present_confirmations",
            "not_present_confirmations",
        ]
