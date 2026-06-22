package com.drivesafe.kenya.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationService(context: Context) {

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _speedKmh = MutableStateFlow<Double?>(null)
    val speedKmh: StateFlow<Double?> = _speedKmh.asStateFlow()

    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val userLocation: StateFlow<Pair<Double, Double>?> = _userLocation.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        INTERVAL_MS
    )
        .setMinUpdateDistanceMeters(MIN_DISTANCE_METERS)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            _speedKmh.value = if (location.hasSpeed()) {
                location.speed.toDouble() * MS_TO_KMH
            } else {
                null
            }
            _userLocation.value = Pair(location.latitude, location.longitude)
        }
    }

    @SuppressLint("MissingPermission")
    fun startUpdates() {
        client.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        _isActive.value = true
        Log.d(TAG, "Location updates started")
    }

    fun stopUpdates() {
        client.removeLocationUpdates(locationCallback)
        _isActive.value = false
        _speedKmh.value = null
        _userLocation.value = null
        Log.d(TAG, "Location updates stopped")
    }

    companion object {
        private const val TAG = "LocationService"
        private const val INTERVAL_MS = 2000L
        private const val MIN_DISTANCE_METERS = 5f
        private const val MS_TO_KMH = 3.6
    }
}
