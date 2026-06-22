from django.db import models


class CameraZone(models.Model):
    id = models.CharField(max_length=100, primary_key=True)
    road_name = models.CharField(max_length=200)
    location_name = models.CharField(max_length=200)
    latitude = models.FloatField(default=0.0)
    longitude = models.FloatField(default=0.0)
    speed_limit_kmh = models.IntegerField(null=True, blank=True)
    min_speed_limit_kmh = models.IntegerField(null=True, blank=True)
    max_speed_limit_kmh = models.IntegerField(null=True, blank=True)
    warning_radius_meters = models.IntegerField(default=700)
    camera_type = models.CharField(max_length=50, default="monitored_zone")
    direction = models.CharField(max_length=50, null=True, blank=True)
    status = models.CharField(max_length=20, default="active")
    verified = models.BooleanField(default=False)
    source = models.CharField(max_length=100, default="initial_image_dataset")
    last_updated = models.DateField()
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["road_name", "location_name"]

    def __str__(self):
        return f"{self.road_name} - {self.location_name}"


class DatasetVersion(models.Model):
    version = models.CharField(max_length=20, unique=True)
    published_at = models.DateTimeField(auto_now_add=True)
    notes = models.TextField(blank=True, default="")
    is_active = models.BooleanField(default=False)

    class Meta:
        ordering = ["-published_at"]

    def __str__(self):
        return f"v{self.version} ({'active' if self.is_active else 'inactive'})"

    def save(self, *args, **kwargs):
        if self.is_active:
            DatasetVersion.objects.filter(is_active=True).exclude(pk=self.pk).update(is_active=False)
        super().save(*args, **kwargs)
