from django.urls import path
from . import views

urlpatterns = [
    path("plans", views.payment_plans_view),
    path("subscription", views.subscription_status_view),
    path("status", views.payment_status_view),
    path("mpesa/stk-push", views.mpesa_stk_push_view),
    path("mpesa/callback", views.mpesa_callback_view),
    path("admin/list", views.admin_payments_list_view),
    path("admin/manual-approval", views.admin_manual_approval_view),
]
