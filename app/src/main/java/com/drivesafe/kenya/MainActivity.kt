package com.drivesafe.kenya

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings as AndroidSettings
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.drivesafe.kenya.alerts.AlertManager
import com.drivesafe.kenya.alerts.CameraProximityDetector
import com.drivesafe.kenya.alerts.DriveThroughTracker
import com.drivesafe.kenya.alerts.OverspeedDetector
import com.drivesafe.kenya.alerts.PolicePresenceProximityDetector
import com.drivesafe.kenya.data.AppThemeMode
import com.drivesafe.kenya.data.CameraZoneRepository
import com.drivesafe.kenya.data.DriveSafeDatabase
import com.drivesafe.kenya.data.LocalJsonDataSource
import com.drivesafe.kenya.data.SettingsRepository
import com.drivesafe.kenya.data.SyncResult
import com.drivesafe.kenya.data.UserSettings
import com.drivesafe.kenya.data.api.DriveSafeApiService
import com.drivesafe.kenya.data.auth.AuthApi
import com.drivesafe.kenya.data.auth.AuthRepository
import com.drivesafe.kenya.data.auth.AuthResult
import com.drivesafe.kenya.data.auth.SessionManager
import com.drivesafe.kenya.data.payment.PaymentApi
import com.drivesafe.kenya.data.payment.PaymentRepository
import com.drivesafe.kenya.data.payment.PaymentResult
import com.drivesafe.kenya.data.police.PolicePresenceApi
import com.drivesafe.kenya.data.police.PolicePresenceRepository
import com.drivesafe.kenya.data.police.PolicePresenceRepository.ReportResult
import com.drivesafe.kenya.location.LocationService
import com.drivesafe.kenya.ui.DrivingScreen
import com.drivesafe.kenya.ui.HomeScreen
import com.drivesafe.kenya.ui.LoginScreen
import com.drivesafe.kenya.ui.PaymentPhase
import com.drivesafe.kenya.ui.PaymentScreen
import com.drivesafe.kenya.ui.PaymentUiState
import com.drivesafe.kenya.ui.RegisterScreen
import com.drivesafe.kenya.ui.SettingsScreen
import com.drivesafe.kenya.ui.theme.DriveSafeKenyaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    private lateinit var locationService: LocationService
    private lateinit var alertManager: AlertManager
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var policePresenceRepository: PolicePresenceRepository

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

        val sessionManager = SessionManager(applicationContext)
        val authApi = AuthApi.create()
        val authRepository = AuthRepository(authApi, sessionManager)
        val paymentApi = PaymentApi.create()
        val paymentRepository = PaymentRepository(paymentApi, sessionManager)

        policePresenceRepository = PolicePresenceRepository(
            api = PolicePresenceApi.create(),
            deviceHash = computeDeviceHash()
        )

        enableEdgeToEdge()
        setContent {
            val themeMode by settingsRepo.themeMode.collectAsState(initial = AppThemeMode.LIGHT)

            DriveSafeKenyaTheme(darkTheme = themeMode == AppThemeMode.DARK) {
                val isLoggedIn by sessionManager.isLoggedIn.collectAsState(initial = false)
                val isDriving by locationService.isActive.collectAsState()
                val speedKmh by locationService.speedKmh.collectAsState()
                val userLocation by locationService.userLocation.collectAsState()
                val gpsFix by locationService.gpsFix.collectAsState()
                val settings by settingsRepo.settings.collectAsState(initial = UserSettings())
                val cameraZones by repository.activeZones.collectAsState(initial = emptyList())
                val currentSyncMetadata by repository.syncMetadata.collectAsState(initial = null)
                val policeAlerts by policePresenceRepository.alerts.collectAsState()

                var syncStatus by remember { mutableStateOf("") }
                var isSyncing by remember { mutableStateOf(false) }
                var permissionDenied by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                var showPayment by remember { mutableStateOf(false) }
                var showRegister by remember { mutableStateOf(false) }
                var authLoading by remember { mutableStateOf(false) }
                var authError by remember { mutableStateOf<String?>(null) }
                var paymentState by remember { mutableStateOf(PaymentUiState()) }
                var policeReportMessage by remember { mutableStateOf<String?>(null) }
                var lastPoliceFetchTime by remember { mutableLongStateOf(0L) }
                var lastPoliceFetchLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
                val driveThroughTracker = remember { DriveThroughTracker() }
                var driveThroughPromptAlertId by remember { mutableStateOf<String?>(null) }

                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    repository.seedIfEmpty()
                }

                val nearbyCamera = userLocation?.let { (lat, lng) ->
                    CameraProximityDetector.findNearest(lat, lng, cameraZones)
                }

                val overspeedResult = if (speedKmh != null && nearbyCamera != null) {
                    OverspeedDetector.check(
                        speedKmh!!, nearbyCamera.zone, settings.overspeedToleranceKmh
                    )
                } else null

                val nearbyPoliceAlert = userLocation?.let { (lat, lng) ->
                    PolicePresenceProximityDetector.findNearest(lat, lng, policeAlerts)
                }

                // Periodically fetch police alerts while driving: every 45s or every 1 km
                LaunchedEffect(userLocation, isDriving) {
                    if (!isDriving || userLocation == null) return@LaunchedEffect
                    val (lat, lng) = userLocation!!
                    val now = System.currentTimeMillis()
                    val elapsedMs = now - lastPoliceFetchTime
                    val distanceMoved = lastPoliceFetchLocation?.let { (pLat, pLng) ->
                        sqrt((lat - pLat) * (lat - pLat) + (lng - pLng) * (lng - pLng)) * 111_000.0
                    } ?: Double.MAX_VALUE

                    if (elapsedMs >= POLICE_POLL_INTERVAL_MS || distanceMoved >= POLICE_POLL_DISTANCE_METERS) {
                        policePresenceRepository.fetchActiveAlerts(lat, lng, POLICE_FETCH_RADIUS_METERS)
                        lastPoliceFetchLocation = Pair(lat, lng)
                        lastPoliceFetchTime = now
                    }
                }

                LaunchedEffect(userLocation) {
                    if (isDriving && userLocation != null) {
                        alertManager.evaluate(
                            nearbyCamera, overspeedResult, speedKmh,
                            settings.voiceAlertsEnabled, settings.vibrationAlertsEnabled
                        )
                    }
                }

                LaunchedEffect(gpsFix) {
                    val fix = gpsFix
                    if (isDriving && fix != null) {
                        alertManager.evaluatePolicePresence(
                            fix, policeAlerts,
                            settings.voiceAlertsEnabled, settings.vibrationAlertsEnabled
                        )
                        val alreadyAlertedIds = policeAlerts
                            .filter { alertManager.hasAlertedProximity(it.id) }
                            .map { it.id }
                            .toSet()
                        val promptId = driveThroughTracker.onTick(
                            fix.latitude, fix.longitude, alreadyAlertedIds, policeAlerts
                        )
                        if (promptId != null) {
                            driveThroughPromptAlertId = promptId
                        }
                    }
                }

                LaunchedEffect(driveThroughPromptAlertId) {
                    val id = driveThroughPromptAlertId
                    if (id != null) {
                        delay(30_000L)
                        if (driveThroughPromptAlertId == id) {
                            driveThroughPromptAlertId = null
                        }
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
                        !isLoggedIn && showRegister -> {
                            RegisterScreen(
                                isLoading = authLoading,
                                error = authError,
                                onRegister = { email, password, name ->
                                    scope.launch {
                                        authLoading = true
                                        authError = null
                                        when (val res = authRepository.register(email, password, name)) {
                                            is AuthResult.Success -> {
                                                authError = null
                                                showRegister = false
                                            }
                                            is AuthResult.Error -> authError = res.message
                                        }
                                        authLoading = false
                                    }
                                },
                                onNavigateToLogin = {
                                    authError = null
                                    showRegister = false
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        !isLoggedIn -> {
                            LoginScreen(
                                isLoading = authLoading,
                                error = authError,
                                onLogin = { email, password ->
                                    scope.launch {
                                        authLoading = true
                                        authError = null
                                        when (val res = authRepository.login(email, password)) {
                                            is AuthResult.Success -> authError = null
                                            is AuthResult.Error -> authError = res.message
                                        }
                                        authLoading = false
                                    }
                                },
                                onNavigateToRegister = {
                                    authError = null
                                    showRegister = true
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        isDriving -> {
                            DrivingScreen(
                                speedKmh = speedKmh,
                                isLocationAvailable = userLocation != null,
                                nearbyCamera = nearbyCamera,
                                overspeedResult = overspeedResult,
                                cameraZoneCount = cameraZones.size,
                                keepScreenOn = settings.keepScreenOn,
                                themeMode = themeMode,
                                onToggleTheme = {
                                    scope.launch {
                                        settingsRepo.setThemeMode(
                                            if (themeMode == AppThemeMode.LIGHT) {
                                                AppThemeMode.DARK
                                            } else {
                                                AppThemeMode.LIGHT
                                            }
                                        )
                                    }
                                },
                                onStopDriving = {
                                    locationService.stopUpdates()
                                    alertManager.reset()
                                    driveThroughTracker.reset()
                                    driveThroughPromptAlertId = null
                                    policeReportMessage = null
                                },
                                nearbyPoliceAlert = nearbyPoliceAlert,
                                onReportPolicePresence = {
                                    val loc = userLocation
                                    if (loc == null) {
                                        policeReportMessage = getString(R.string.police_location_unavailable)
                                        return@DrivingScreen
                                    }
                                    scope.launch {
                                        policeReportMessage = when (val r = policePresenceRepository.report(loc.first, loc.second)) {
                                            is ReportResult.Success -> getString(R.string.police_reported_thanks)
                                            is ReportResult.Failure -> r.reason
                                        }
                                    }
                                },
                                policeReportMessage = policeReportMessage,
                                onConfirmPolicePresent = { alertId ->
                                    val loc = userLocation
                                    if (loc == null) {
                                        policeReportMessage = getString(R.string.police_location_unavailable)
                                    } else {
                                        scope.launch {
                                            when (val r = policePresenceRepository.confirm(alertId, loc.first, loc.second, present = true)) {
                                                is PolicePresenceRepository.ConfirmResult.Success -> {}
                                                is PolicePresenceRepository.ConfirmResult.Failure -> policeReportMessage = r.reason
                                            }
                                        }
                                    }
                                },
                                onConfirmPoliceNotPresent = { alertId ->
                                    val loc = userLocation
                                    if (loc == null) {
                                        policeReportMessage = getString(R.string.police_location_unavailable)
                                    } else {
                                        scope.launch {
                                            when (val r = policePresenceRepository.confirm(alertId, loc.first, loc.second, present = false)) {
                                                is PolicePresenceRepository.ConfirmResult.Success -> {}
                                                is PolicePresenceRepository.ConfirmResult.Failure -> policeReportMessage = r.reason
                                            }
                                        }
                                    }
                                },
                                driveThroughPrompt = driveThroughPromptAlertId?.let { id ->
                                    policeAlerts.firstOrNull { it.id == id }
                                },
                                onDriveThroughAnswer = { alertId, present ->
                                    val loc = userLocation
                                    driveThroughPromptAlertId = null
                                    if (loc == null) {
                                        policeReportMessage = getString(R.string.police_location_unavailable)
                                    } else {
                                        scope.launch {
                                            when (val r = policePresenceRepository.confirm(alertId, loc.first, loc.second, present)) {
                                                is PolicePresenceRepository.ConfirmResult.Success -> {}
                                                is PolicePresenceRepository.ConfirmResult.Failure -> policeReportMessage = r.reason
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        showPayment -> {
                            PaymentScreen(
                                state = paymentState,
                                onPhoneChanged = { paymentState = paymentState.copy(phoneNumber = it) },
                                onPlanSelected = { paymentState = paymentState.copy(selectedPlanCode = it) },
                                onPay = {
                                    scope.launch {
                                        paymentState = paymentState.copy(isLoading = true, error = null)
                                        val result = paymentRepository.initiateStkPush(
                                            paymentState.phoneNumber, paymentState.selectedPlanCode
                                        )
                                        when (result) {
                                            is PaymentResult.StkPushSent -> {
                                                paymentState = paymentState.copy(
                                                    isLoading = false,
                                                    phase = PaymentPhase.WAITING_FOR_PIN,
                                                    message = result.message
                                                )
                                                paymentState = paymentState.copy(phase = PaymentPhase.POLLING)
                                                val pollResult = paymentRepository.pollPaymentStatus(result.paymentId)
                                                paymentState = when (pollResult) {
                                                    is PaymentResult.Paid -> paymentState.copy(
                                                        phase = PaymentPhase.SUCCESS,
                                                        receiptNumber = pollResult.receiptNumber,
                                                        expiryDate = pollResult.expiryDate
                                                    )
                                                    is PaymentResult.Failed -> paymentState.copy(
                                                        phase = PaymentPhase.FAILED,
                                                        error = pollResult.message
                                                    )
                                                    is PaymentResult.Cancelled -> paymentState.copy(
                                                        phase = PaymentPhase.FAILED,
                                                        error = "Payment cancelled"
                                                    )
                                                    is PaymentResult.Timeout -> paymentState.copy(
                                                        phase = PaymentPhase.FAILED,
                                                        error = "Payment timed out. Check M-PESA messages."
                                                    )
                                                    else -> paymentState
                                                }
                                            }
                                            is PaymentResult.Failed -> {
                                                paymentState = paymentState.copy(
                                                    isLoading = false,
                                                    error = result.message
                                                )
                                            }
                                            else -> {
                                                paymentState = paymentState.copy(isLoading = false)
                                            }
                                        }
                                    }
                                },
                                onRetry = {
                                    paymentState = paymentState.copy(
                                        phase = PaymentPhase.SELECT_PLAN,
                                        error = null,
                                        message = null
                                    )
                                },
                                onBack = {
                                    showPayment = false
                                    paymentState = PaymentUiState(plans = paymentState.plans)
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
                                onUpgrade = {
                                    showSettings = false
                                    showPayment = true
                                    scope.launch {
                                        val plans = paymentRepository.getPlans()
                                        paymentState = paymentState.copy(plans = plans)
                                    }
                                },
                                onLogout = {
                                    scope.launch {
                                        authRepository.logout()
                                        showSettings = false
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
                                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                },
                                onSettings = { showSettings = true },
                                onUpgrade = {
                                    showPayment = true
                                    scope.launch {
                                        val plans = paymentRepository.getPlans()
                                        paymentState = paymentState.copy(plans = plans)
                                    }
                                },
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
        if (locationService.isActive.value) locationService.stopUpdates()
    }

    override fun onDestroy() {
        alertManager.shutdown()
        super.onDestroy()
    }

    private fun computeDeviceHash(): String {
        val androidId = AndroidSettings.Secure.getString(
            applicationContext.contentResolver,
            AndroidSettings.Secure.ANDROID_ID
        ) ?: "unknown"
        val bytes = MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val POLICE_POLL_INTERVAL_MS = 45_000L
        private const val POLICE_POLL_DISTANCE_METERS = 1_000.0
        private const val POLICE_FETCH_RADIUS_METERS = 12_000
    }
}
