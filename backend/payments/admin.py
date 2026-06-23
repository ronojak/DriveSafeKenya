from django.contrib import admin
from .models import PaymentPlan, Payment, Subscription, PaymentAuditLog


@admin.register(PaymentPlan)
class PaymentPlanAdmin(admin.ModelAdmin):
    list_display = ["code", "name", "amount", "duration_days", "is_active"]
    list_filter = ["is_active"]
    list_editable = ["is_active", "amount"]


@admin.register(Payment)
class PaymentAdmin(admin.ModelAdmin):
    list_display = [
        "id", "user", "plan", "amount", "phone_number",
        "status", "mpesa_receipt_number", "paid_at", "created_at",
    ]
    list_filter = ["status", "provider"]
    search_fields = ["phone_number", "mpesa_receipt_number", "user__email"]
    readonly_fields = ["raw_callback"]


@admin.register(Subscription)
class SubscriptionAdmin(admin.ModelAdmin):
    list_display = ["user", "plan", "status", "start_date", "expiry_date", "source"]
    list_filter = ["status"]
    search_fields = ["user__email"]


@admin.register(PaymentAuditLog)
class PaymentAuditLogAdmin(admin.ModelAdmin):
    list_display = ["payment", "event_type", "old_status", "new_status", "created_at"]
    list_filter = ["event_type"]
    readonly_fields = ["raw_payload"]
