from django.urls import path

from . import views

urlpatterns = [
    path("health", views.health),
    path("camera-zones/version", views.camera_zones_version),
    path("camera-zones", views.camera_zones_list),
]
