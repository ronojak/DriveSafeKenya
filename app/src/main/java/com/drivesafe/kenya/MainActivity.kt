package com.drivesafe.kenya

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.drivesafe.kenya.alerts.AlertManager
import com.drivesafe.kenya.alerts.CameraProximityDetector
import com.drivesafe.kenya.alerts.OverspeedDetector
import com.drivesafe.kenya.data.CameraZoneRepository
import com.drivesafe.kenya.data.DriveSafeDatabase
import com.drivesafe.kenya.data.LocalJsonDataSource
import com.drivesafe.kenya.data.SettingsRepository
import com.drivesafe.kenya.data.SyncMetadata
import com.drivesafe.kenya.data.SyncResult
import com.drivesafe.kenya.data.UserSettings
import com.drivesafe.kenya.data.api.DriveSafeApiService
import com.drivesafe.kenya.location.LocationService
import com.drivesafe.kenya.ui.DrivingScreen
import com.drivesafe.kenya.ui.HomeScreen
import com.drivesafe.kenya.ui.SettingsScreen
import com.drivesafe.kenya.ui.theme.DriveSafeKenyaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var locationService: LocationService
    private lateinit var alertManager: AlertManager
    private lateinit var settingsRepo: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = DriveSafeDatabase.getInstance(applicationContext)
        val apiService = DriveSafeApiService.create()
        val repository = CameraZoneRepository(
            dao = db.cameraZoneDao(),
            syncDao = db.syncMetadataDao(),
            jsonDataSource = LocalJsonDataSource(assets),
            apiService = apiService
        )
        locationService = LocationService(this)
        alertManager = AlertManager(applicationContext)
        settingsRepo = SettingsRepository(applicationContext)

        enableEdgeToEdge()
        setContent {
            DriveSafeKenyaTheme {
                val isDriving by locationService.isActive.collectAsState()
                val speedKmh by locationService.speedKmh.collectAsState()
                val userLocation by locationService.userLocation.collectAsState()
                val settings by settingsRepo.settings.collectAsState(initial = UserSettings())
                val cameraZones by repository.activeZones
                    .collectAsState(initial = emptyList())
                val currentSyncMetadata by repository.syncMetadata
                    .collectAsState(initial = null)
                var syncStatus by remember { mutableStateOf("") }
                var isSyncing by remember { mutableStateOf(false) }
                var permissionDenied by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    repository.seedIfEmpty()
                }

                val nearbyCamera = userLocation?.let { (lat, lng) ->
                    CameraProximityDetector.findNearest(
                        lat, lng, cameraZones, settings.warningDistanceMeters
                    )
                }

                val overspeedResult = if (speedKmh != null && nearbyCamera != null) {
                    OverspeedDetector.check(
                        speedKmh!!, nearbyCamera.zone, settings.overspeedToleranceKmh
                    )
                } else {
                    null
                }

                LaunchedEffect(userLocation) {
                    if (isDriving && userLocation != null) {
                        alertManager.evaluate(
                            nearbyCamera, overspeedResult, speedKmh,
                            settings.voiceAlertsEnabled, settings.vibrationAlertsEnabled
                        )
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        permissionDenied = false
                        locationService.startUpdates()
                    } else {
                        permissionDenied = true
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when {
                        isDriving -> {
                            DrivingScreen(
                                speedKmh = speedKmh,
                                nearbyCamera = nearbyCamera,
                                overspeedResult = overspeedResult,
                                cameraZoneCount = cameraZones.size,
                                keepScreenOn = settings.keepScreenOn,
                                onStopDriving = {
                                    locationService.stopUpdates()
                                    alertManager.reset()
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        showSettings -> {
                            SettingsScreen(
                                settings = settings,
                                syncMetadata = currentSyncMetadata,
                                syncStatus = syncStatus,
                                isSyncing = isSyncing,
                                onVoiceAlertsChanged = { scope.launch { settingsRepo.setVoiceAlertsEnabled(it) } },
                                onVibrationAlertsChanged = { scope.launch { settingsRepo.setVibrationAlertsEnabled(it) } },
                                onWarningDistanceChanged = { scope.launch { settingsRepo.setWarningDistance(it) } },
                                onOverspeedToleranceChanged = { scope.launch { settingsRepo.setOverspeedTolerance(it) } },
                                onKeepScreenOnChanged = { scope.launch { settingsRepo.setKeepScreenOn(it) } },
                                onCheckForUpdates = {
                                    scope.launch {
                                        isSyncing = true
                                        syncStatus = ""
                                        val result = repository.syncFromApi()
                                        syncStatus = when (result) {
                                            is SyncResult.AlreadyUpToDate -> getString(R.string.sync_up_to_date)
                                            is SyncResult.Updated -> getString(R.string.sync_updated, result.version, result.zoneCount)
                                            is SyncResult.Failed -> getString(R.string.sync_failed)
                                        }
                                        isSyncing = false
                                    }
                                },
                                onBack = { showSettings = false },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        else -> {
                            HomeScreen(
                                cameraZoneCount = cameraZones.size,
                                permissionDenied = permissionDenied,
                                onStartDriving = {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        locationService.startUpdates()
                                    } else {
                                        permissionLauncher.launch(
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    }
                                },
                                onSettings = { showSettings = true },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (locationService.isActive.value) {
            locationService.stopUpdates()
        }
    }

    override fun onDestroy() {
        alertManager.shutdown()
        super.onDestroy()
    }
}
