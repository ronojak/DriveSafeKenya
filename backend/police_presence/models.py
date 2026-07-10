import uuid
from django.db import models
from django.utils import timezone


def _fifteen_minutes_from_now():
    return timezone.now() + timezone.timedelta(minutes=15)


def _thirty_minutes_from_now():
    return timezone.now() + timezone.timedelta(minutes=30)


def _fifty_minutes_from_now():
    return timezone.now() + timezone.timedelta(minutes=50)


class PolicePresenceAlert(models.Model):
    STATUS_ACTIVE = "active"
    STATUS_NEEDS_CONFIRMATION = "needs_confirmation"
    STATUS_CONFIRMED_PRESENT = "confirmed_present"
    STATUS_NOT_PRESENT = "not_present"
    STATUS_EXPIRED = "expired"

    STATUS_CHOICES = [
        (STATUS_ACTIVE, "Active"),
        (STATUS_NEEDS_CONFIRMATION, "Needs Confirmation"),
        (STATUS_CONFIRMED_PRESENT, "Confirmed Present"),
        (STATUS_NOT_PRESENT, "Not Present"),
        (STATUS_EXPIRED, "Expired"),
    ]

    # Two distinct-device "gone" votes marks the alert as cleared
    NOT_PRESENT_THRESHOLD = 2

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    latitude = models.FloatField()
    longitude = models.FloatField()
    reported_at = models.DateTimeField(auto_now_add=True)
    confirmation_required_after = models.DateTimeField(default=_fifteen_minutes_from_now)
    expires_at = models.DateTimeField(default=_fifty_minutes_from_now)
    last_confirmed_at = models.DateTimeField(null=True, blank=True)
    status = models.CharField(max_length=30, choices=STATUS_CHOICES, default=STATUS_ACTIVE)
    present_confirmations = models.IntegerField(default=0)
    not_present_confirmations = models.IntegerField(default=0)
    source = models.CharField(max_length=100, default="anonymous_community_report")
    reported_by_device_hash = models.CharField(max_length=64, blank=True, default="")
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["-reported_at"]

    def __str__(self):
        return f"Police at ({self.latitude:.4f}, {self.longitude:.4f}) — {self.status}"

    def refresh_status(self):
        """Compute and persist the current status based on time and confirmations."""
        now = timezone.now()
        if self.status in (self.STATUS_EXPIRED, self.STATUS_NOT_PRESENT):
            return
        if now > self.expires_at:
            self.status = self.STATUS_EXPIRED
        elif self.not_present_confirmations >= self.NOT_PRESENT_THRESHOLD:
            self.status = self.STATUS_NOT_PRESENT
        elif now > self.confirmation_required_after and self.status == self.STATUS_ACTIVE:
            self.status = self.STATUS_NEEDS_CONFIRMATION
        self.save(update_fields=["status", "updated_at"])


class PolicePresenceConfirmation(models.Model):
    """One device's vote on whether a checkpoint is still present. A device
    may vote at most once per alert (see Meta.unique_together)."""

    alert = models.ForeignKey(
        PolicePresenceAlert, related_name="confirmations", on_delete=models.CASCADE
    )
    device_hash = models.CharField(max_length=64)
    present = models.BooleanField()
    latitude = models.FloatField()
    longitude = models.FloatField()
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ("alert", "device_hash")

    def __str__(self):
        vote = "present" if self.present else "gone"
        return f"{vote} vote on {self.alert_id} by {self.device_hash[:8]}"
