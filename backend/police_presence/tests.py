from django.test import TestCase
from django.utils import timezone
from django.urls import reverse
from rest_framework.test import APIClient

from .models import PolicePresenceAlert, PolicePresenceConfirmation


def _nairobi_coords():
    return {"latitude": -1.2921, "longitude": 36.8219}


class PolicePresenceModelTest(TestCase):

    def test_default_status_is_active(self):
        alert = PolicePresenceAlert.objects.create(latitude=-1.2921, longitude=36.8219)
        self.assertEqual(alert.status, PolicePresenceAlert.STATUS_ACTIVE)

    def test_refresh_status_sets_expired_when_past_expires_at(self):
        now = timezone.now()
        alert = PolicePresenceAlert.objects.create(
            latitude=-1.2921, longitude=36.8219,
            expires_at=now - timezone.timedelta(minutes=1)
        )
        alert.refresh_status()
        alert.refresh_from_db()
        self.assertEqual(alert.status, PolicePresenceAlert.STATUS_EXPIRED)

    def test_refresh_status_sets_needs_confirmation(self):
        now = timezone.now()
        alert = PolicePresenceAlert.objects.create(
            latitude=-1.2921, longitude=36.8219,
            confirmation_required_after=now - timezone.timedelta(minutes=1),
            expires_at=now + timezone.timedelta(minutes=10)
        )
        alert.refresh_status()
        alert.refresh_from_db()
        self.assertEqual(alert.status, PolicePresenceAlert.STATUS_NEEDS_CONFIRMATION)

    def test_default_expiry_is_fifty_minutes(self):
        before = timezone.now()
        alert = PolicePresenceAlert.objects.create(latitude=-1.2921, longitude=36.8219)
        expected_min = before + timezone.timedelta(minutes=49)
        expected_max = before + timezone.timedelta(minutes=51)
        self.assertTrue(expected_min <= alert.expires_at <= expected_max)

    def test_last_confirmed_at_defaults_to_none(self):
        alert = PolicePresenceAlert.objects.create(latitude=-1.2921, longitude=36.8219)
        self.assertIsNone(alert.last_confirmed_at)


class ReportViewTest(TestCase):

    def setUp(self):
        self.client = APIClient()

    def test_report_returns_201(self):
        res = self.client.post("/api/police-presence/report", {**_nairobi_coords(), "device_hash": "abc123"}, format="json")
        self.assertEqual(res.status_code, 201)
        self.assertIn("id", res.data)

    def test_report_rejects_outside_kenya(self):
        res = self.client.post("/api/police-presence/report", {"latitude": 51.5, "longitude": -0.1}, format="json")
        self.assertEqual(res.status_code, 400)

    def test_report_rejects_zero_coords(self):
        res = self.client.post("/api/police-presence/report", {"latitude": 0.0, "longitude": 0.0}, format="json")
        self.assertEqual(res.status_code, 400)

    def test_rate_limit_blocks_second_report(self):
        data = {**_nairobi_coords(), "device_hash": "device-rate-test"}
        self.client.post("/api/police-presence/report", data, format="json")
        res = self.client.post("/api/police-presence/report", data, format="json")
        self.assertEqual(res.status_code, 429)

    def test_rate_limit_does_not_block_different_device(self):
        self.client.post("/api/police-presence/report", {**_nairobi_coords(), "device_hash": "d1"}, format="json")
        res = self.client.post("/api/police-presence/report", {**_nairobi_coords(), "device_hash": "d2"}, format="json")
        self.assertEqual(res.status_code, 201)


class ActiveViewTest(TestCase):

    def setUp(self):
        self.client = APIClient()

    def test_active_requires_lat_lon(self):
        res = self.client.get("/api/police-presence/active")
        self.assertEqual(res.status_code, 400)

    def test_active_returns_nearby_alert(self):
        PolicePresenceAlert.objects.create(latitude=-1.2921, longitude=36.8219)
        res = self.client.get("/api/police-presence/active?lat=-1.2921&lon=36.8219")
        self.assertEqual(res.status_code, 200)
        self.assertEqual(len(res.data["alerts"]), 1)

    def test_active_excludes_expired_alerts(self):
        PolicePresenceAlert.objects.create(
            latitude=-1.2921, longitude=36.8219,
            expires_at=timezone.now() - timezone.timedelta(minutes=1)
        )
        res = self.client.get("/api/police-presence/active?lat=-1.2921&lon=36.8219")
        self.assertEqual(len(res.data["alerts"]), 0)

    def test_active_excludes_alerts_beyond_radius(self):
        # ~15 km north
        PolicePresenceAlert.objects.create(latitude=-1.157, longitude=36.8219)
        res = self.client.get("/api/police-presence/active?lat=-1.2921&lon=36.8219&radius_meters=10000")
        self.assertEqual(len(res.data["alerts"]), 0)


class ConfirmViewTest(TestCase):

    def setUp(self):
        self.client = APIClient()
        self.alert = PolicePresenceAlert.objects.create(latitude=-1.2921, longitude=36.8219)

    def test_confirm_present_increments_counter(self):
        res = self.client.post(
            f"/api/police-presence/{self.alert.id}/confirm",
            {"device_hash": "tester1"}, format="json"
        )
        self.assertEqual(res.status_code, 200)
        self.alert.refresh_from_db()
        self.assertEqual(self.alert.present_confirmations, 1)

    def test_confirm_not_present_twice_sets_not_present_status(self):
        self.client.post(f"/api/police-presence/{self.alert.id}/not-present", {"device_hash": "t1"}, format="json")
        res = self.client.post(f"/api/police-presence/{self.alert.id}/not-present", {"device_hash": "t2"}, format="json")
        self.assertEqual(res.status_code, 200)
        self.alert.refresh_from_db()
        self.assertEqual(self.alert.status, PolicePresenceAlert.STATUS_NOT_PRESENT)


class PolicePresenceConfirmationModelTest(TestCase):

    def test_unique_together_alert_and_device_hash(self):
        alert = PolicePresenceAlert.objects.create(latitude=-1.2921, longitude=36.8219)
        PolicePresenceConfirmation.objects.create(
            alert=alert, device_hash="dev1", present=True,
            latitude=-1.2921, longitude=36.8219
        )
        with self.assertRaises(Exception):
            PolicePresenceConfirmation.objects.create(
                alert=alert, device_hash="dev1", present=False,
                latitude=-1.2921, longitude=36.8219
            )
