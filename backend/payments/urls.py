from django.conf import settings
from django.urls import path
from . import views

callback_secret = getattr(settings, "MPESA_CALLBACK_SECRET", "")
callback_path = f"mpesa/callback/{callback_secret}" if callback_secret else "mpesa/callback"

urlpatterns = [
    path("plans", views.payment_plans_view),
    path("subscription", views.subscription_status_view),
    path("status", views.payment_status_view),
    path("mpesa/stk-push", views.mpesa_stk_push_view),
    path(callback_path, views.mpesa_callback_view),
    path("admin/list", views.admin_payments_list_view),
    path("admin/manual-approval", views.admin_manual_approval_view),
]
