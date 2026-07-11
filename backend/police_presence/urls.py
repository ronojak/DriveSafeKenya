from django.urls import path

from . import views

urlpatterns = [
    path("report", views.report_police_presence),
    path("active", views.active_police_presence),
    path("<uuid:alert_id>/confirm", views.confirm_police_presence),
]
