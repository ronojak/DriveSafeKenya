from django.db import models
from django.conf import settings


class PaymentPlan(models.Model):
    code = models.CharField(max_length=32, unique=True)
    name = models.CharField(max_length=120)
    amount = models.IntegerField()
    duration_days = models.IntegerField()
    description = models.TextField(blank=True)
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return f"{self.name} — KES {self.amount}"


class Payment(models.Model):
    STATUS_CHOICES = [
        ("PENDING", "PENDING"),
        ("PAID", "PAID"),
        ("FAILED", "FAILED"),
        ("CANCELLED", "CANCELLED"),
        ("EXPIRED", "EXPIRED"),
    ]

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="payments"
    )
    plan = models.ForeignKey(
        PaymentPlan, on_delete=models.SET_NULL, null=True, blank=True
    )
    provider = models.CharField(max_length=16, default="mpesa")
    amount = models.IntegerField(default=0)
    currency = models.CharField(max_length=8, default="KES")
    phone_number = models.CharField(max_length=32, blank=True)
    status = models.CharField(max_length=16, choices=STATUS_CHOICES, default="PENDING")

    merchant_request_id = models.CharField(max_length=64, blank=True)
    checkout_request_id = models.CharField(max_length=64, blank=True, db_index=True)
    mpesa_receipt_number = models.CharField(
        max_length=64, blank=True, db_index=True
    )
    result_code = models.IntegerField(null=True, blank=True)
    result_description = models.TextField(blank=True)
    raw_callback = models.JSONField(null=True, blank=True)
    failure_reason = models.TextField(blank=True)

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    paid_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        indexes = [
            models.Index(fields=["merchant_request_id"]),
            models.Index(fields=["user", "-created_at"]),
        ]

    def __str__(self):
        return f"{self.provider}:{self.status}:{self.id}"


class Subscription(models.Model):
    STATUS_CHOICES = [
        ("active", "active"),
        ("expired", "expired"),
        ("cancelled", "cancelled"),
    ]

    user = models.OneToOneField(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="subscription"
    )
    plan = models.ForeignKey(
        PaymentPlan, on_delete=models.SET_NULL, null=True, blank=True
    )
    payment = models.ForeignKey(
        Payment, on_delete=models.SET_NULL, null=True, blank=True
    )
    status = models.CharField(max_length=16, choices=STATUS_CHOICES, default="active")
    start_date = models.DateTimeField()
    expiry_date = models.DateTimeField(null=True, blank=True)
    source = models.CharField(max_length=32, default="mpesa")
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return f"{self.user.email}:{self.status}"


class PaymentAuditLog(models.Model):
    payment = models.ForeignKey(
        Payment, on_delete=models.CASCADE, related_name="audit_logs"
    )
    event_type = models.CharField(max_length=32)
    old_status = models.CharField(max_length=16, blank=True)
    new_status = models.CharField(max_length=16, blank=True)
    message = models.TextField(blank=True)
    raw_payload = models.JSONField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"{self.event_type}:{self.payment_id}"
