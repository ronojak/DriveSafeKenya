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
        self.alert = PolicePresenceAlert.objects.create(
            latitude=-1.2921, longitude=36.8219, reported_by_device_hash="reporter-device"
        )

    def _confirm(self, device_hash, present, lat=-1.2921, lon=36.8219):
        return self.client.post(
            f"/api/police-presence/{self.alert.id}/confirm",
            {"latitude": lat, "longitude": lon, "device_hash": device_hash, "present": present},
            format="json",
        )

    # NOTE: the confirm rate limit (_is_confirm_rate_limited) is a process-level
    # Django cache keyed only by device_hash, with a 120s timeout — it is NOT
    # reset between test methods the way the DB is (TestCase only rolls back
    # the DB transaction). Every device_hash below is therefore unique per test
    # method so no two tests can spuriously rate-limit or duplicate-block each
    # other, matching the existing convention in ReportViewTest above (which
    # uses "abc123"/"device-rate-test"/"d1"/"d2" — one unique hash per test,
    # never reused across unrelated test methods).

    def test_present_confirmation_sets_status_and_last_confirmed_at(self):
        res = self._confirm("present-basic-1", True)
        self.assertEqual(res.status_code, 200)
        self.alert.refresh_from_db()
        self.assertEqual(self.alert.status, PolicePresenceAlert.STATUS_CONFIRMED_PRESENT)
        self.assertEqual(self.alert.present_confirmations, 1)
        self.assertIsNotNone(self.alert.last_confirmed_at)

    def test_two_distinct_devices_saying_gone_clears_the_alert(self):
        res1 = self._confirm("gone-clears-1", False)
        self.assertEqual(res1.status_code, 200)
        res2 = self._confirm("gone-clears-2", False)
        self.assertEqual(res2.status_code, 200)
        self.alert.refresh_from_db()
        self.assertEqual(self.alert.status, PolicePresenceAlert.STATUS_NOT_PRESENT)

    def test_cleared_alert_excluded_from_active(self):
        self._confirm("excluded-active-1", False)
        self._confirm("excluded-active-2", False)
        res = self.client.get("/api/police-presence/active?lat=-1.2921&lon=36.8219")
        self.assertEqual(len(res.data["alerts"]), 0)

    def test_present_vote_resets_gone_counter(self):
        self._confirm("reset-counter-1", False)
        self._confirm("reset-counter-2", True)
        self.alert.refresh_from_db()
        self.assertEqual(self.alert.not_present_confirmations, 0)
        self.assertEqual(self.alert.status, PolicePresenceAlert.STATUS_CONFIRMED_PRESENT)

    def test_reporter_cannot_confirm_own_alert(self):
        res = self._confirm("reporter-device", True)
        self.assertEqual(res.status_code, 400)

    def test_confirm_rejected_when_more_than_500m_away(self):
        # ~2km north of the alert
        res = self._confirm("too-far-1", True, lat=-1.2741, lon=36.8219)
        self.assertEqual(res.status_code, 400)

    def test_device_cannot_confirm_the_same_alert_twice(self):
        first = self._confirm("duplicate-vote-1", True)
        self.assertEqual(first.status_code, 200)
        second = self._confirm("duplicate-vote-1", False)
        self.assertEqual(second.status_code, 429)

    def test_confirm_requires_device_hash(self):
        res = self.client.post(
            f"/api/police-presence/{self.alert.id}/confirm",
            {"latitude": -1.2921, "longitude": 36.8219, "present": True},
            format="json",
        )
        self.assertEqual(res.status_code, 400)

    def test_confirm_on_unknown_alert_returns_404(self):
        res = self.client.post(
            "/api/police-presence/00000000-0000-0000-0000-000000000000/confirm",
            {"latitude": -1.2921, "longitude": 36.8219, "device_hash": "unknown-alert-1", "present": True},
            format="json",
        )
        self.assertEqual(res.status_code, 404)

    def test_confirm_on_already_cleared_alert_returns_400(self):
        self._confirm("already-cleared-1", False)
        self._confirm("already-cleared-2", False)
        res = self._confirm("already-cleared-3", True)
        self.assertEqual(res.status_code, 400)


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
