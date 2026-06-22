import json
from pathlib import Path

from django.core.management.base import BaseCommand

from zones.models import CameraZone, DatasetVersion


class Command(BaseCommand):
    help = "Import camera zones from Android camera_zones.json"

    def add_arguments(self, parser):
        default_path = Path(__file__).resolve().parents[4] / "app" / "src" / "main" / "assets" / "camera_zones.json"
        parser.add_argument(
            "--json-path",
            type=str,
            default=str(default_path),
            help="Path to camera_zones.json",
        )

    def handle(self, *args, **options):
        json_path = Path(options["json_path"])
        if not json_path.exists():
            self.stderr.write(self.style.ERROR(f"File not found: {json_path}"))
            return

        with open(json_path) as f:
            zones_data = json.load(f)

        created = 0
        updated = 0
        for zone in zones_data:
            _, was_created = CameraZone.objects.update_or_create(
                id=zone["id"],
                defaults={
                    "road_name": zone["roadName"],
                    "location_name": zone["locationName"],
                    "latitude": zone["latitude"],
                    "longitude": zone["longitude"],
                    "speed_limit_kmh": zone.get("speedLimitKmh"),
                    "min_speed_limit_kmh": zone.get("minSpeedLimitKmh"),
                    "max_speed_limit_kmh": zone.get("maxSpeedLimitKmh"),
                    "warning_radius_meters": zone["warningRadiusMeters"],
                    "camera_type": zone["cameraType"],
                    "direction": zone.get("direction"),
                    "status": zone["status"],
                    "verified": zone["verified"],
                    "source": zone["source"],
                    "last_updated": zone["lastUpdated"],
                },
            )
            if was_created:
                created += 1
            else:
                updated += 1

        _, version_created = DatasetVersion.objects.get_or_create(
            version="1.0",
            defaults={"is_active": True, "notes": "Initial seed from camera_zones.json"},
        )

        self.stdout.write(self.style.SUCCESS(
            f"Done: {created} created, {updated} updated, {len(zones_data)} total zones"
        ))
        if version_created:
            self.stdout.write(self.style.SUCCESS("Created DatasetVersion 1.0 (active)"))
        else:
            self.stdout.write("DatasetVersion 1.0 already exists")
