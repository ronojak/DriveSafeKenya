from django.test import TestCase
from rest_framework.test import APIClient

from .models import CameraZone, DatasetVersion


class HealthEndpointTest(TestCase):
    def test_health(self):
        response = self.client.get("/api/health")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"status": "ok"})


class CameraZonesVersionTest(TestCase):
    def test_version_empty(self):
        response = self.client.get("/api/camera-zones/version")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertIsNone(data["version"])
        self.assertEqual(data["zoneCount"], 0)

    def test_version_with_data(self):
        DatasetVersion.objects.create(version="1.0", is_active=True)
        CameraZone.objects.create(
            id="test_001", road_name="Test Road", location_name="Test",
            last_updated="2026-06-22", status="active",
        )
        response = self.client.get("/api/camera-zones/version")
        data = response.json()
        self.assertEqual(data["version"], "1.0")
        self.assertEqual(data["zoneCount"], 1)


class CameraZonesListTest(TestCase):
    def setUp(self):
        DatasetVersion.objects.create(version="1.0", is_active=True)
        CameraZone.objects.create(
            id="test_001", road_name="Test Road", location_name="Test Location",
            latitude=-1.2, longitude=36.8, speed_limit_kmh=80,
            warning_radius_meters=700, camera_type="monitored_zone",
            status="active", verified=False, source="test",
            last_updated="2026-06-22",
        )
        CameraZone.objects.create(
            id="test_002", road_name="Inactive Road", location_name="Inactive",
            last_updated="2026-06-22", status="inactive",
        )

    def test_returns_active_only(self):
        response = self.client.get("/api/camera-zones")
        data = response.json()
        self.assertEqual(len(data["zones"]), 1)
        self.assertEqual(data["zones"][0]["id"], "test_001")

    def test_camel_case_fields(self):
        response = self.client.get("/api/camera-zones")
        zone = response.json()["zones"][0]
        self.assertIn("roadName", zone)
        self.assertIn("locationName", zone)
        self.assertIn("speedLimitKmh", zone)
        self.assertIn("warningRadiusMeters", zone)
        self.assertIn("cameraType", zone)
        self.assertIn("lastUpdated", zone)
        self.assertNotIn("road_name", zone)

    def test_data_version_included(self):
        response = self.client.get("/api/camera-zones")
        data = response.json()
        self.assertEqual(data["dataVersion"], "1.0")
        self.assertIn("lastUpdated", data)


class DatasetVersionSingleActiveTest(TestCase):
    def test_only_one_active(self):
        v1 = DatasetVersion.objects.create(version="1.0", is_active=True)
        v2 = DatasetVersion.objects.create(version="2.0", is_active=True)
        v1.refresh_from_db()
        self.assertFalse(v1.is_active)
        self.assertTrue(v2.is_active)
