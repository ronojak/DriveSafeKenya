package com.drivesafe.kenya.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drivesafe.kenya.R
import com.drivesafe.kenya.alerts.NearbyCameraResult
import com.drivesafe.kenya.alerts.OverspeedResult
import com.drivesafe.kenya.alerts.OverspeedStatus
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DrivingScreen(
    speedKmh: Double?,
    nearbyCamera: NearbyCameraResult?,
    overspeedResult: OverspeedResult?,
    cameraZoneCount: Int,
    keepScreenOn: Boolean,
    onStopDriving: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (keepScreenOn) {
        KeepScreenOn()
    }
    val isOverspeed = overspeedResult?.status == OverspeedStatus.OVERSPEED

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.current_speed_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (speedKmh != null) {
                stringResource(R.string.speed_value, speedKmh.roundToInt())
            } else {
                stringResource(R.string.speed_unavailable)
            },
            style = MaterialTheme.typography.displayLarge,
            color = if (isOverspeed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
        if (isOverspeed) {
            Spacer(modifier = Modifier.height(12.dp))
            OverspeedBanner(overspeedResult!!)
        }
        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))
        NearbyZoneSection(nearbyCamera)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.camera_zones_loaded, cameraZoneCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(
            onClick = onStopDriving,
            modifier = Modifier.fillMaxWidth(0.7f),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text(text = stringResource(R.string.stop_driving))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OverspeedBanner(result: OverspeedResult) {
    Text(
        text = stringResource(R.string.overspeed_warning),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.error
    )
    val limit = result.applicableLimitKmh
    val tolerance = result.toleranceKmh
    if (limit != null && tolerance != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.overspeed_detail, limit, tolerance),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun NearbyZoneSection(nearbyCamera: NearbyCameraResult?) {
    if (nearbyCamera == null) {
        Text(
            text = stringResource(R.string.no_camera_zone),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val statusColor = if (nearbyCamera.isInWarningRadius) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = if (nearbyCamera.isInWarningRadius) {
            stringResource(R.string.camera_zone_nearby)
        } else {
            stringResource(R.string.nearest_camera_zone)
        },
        style = MaterialTheme.typography.titleMedium,
        color = statusColor
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "${nearbyCamera.zone.roadName} — ${nearbyCamera.zone.locationName}",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(4.dp))

    val speedLimitText = buildSpeedLimitText(nearbyCamera)
    val isVariable = isVariableZone(nearbyCamera.zone)
    Text(
        text = if (isVariable) {
            stringResource(R.string.variable_limit_label, speedLimitText)
        } else {
            stringResource(R.string.speed_limit_label, speedLimitText)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))

    val distanceFormatted = NumberFormat.getIntegerInstance(Locale.getDefault())
        .format(nearbyCamera.distanceMeters.roundToInt())
    Text(
        text = stringResource(R.string.distance_label, distanceFormatted),
        style = MaterialTheme.typography.bodyMedium,
        color = statusColor
    )
}

private fun isVariableZone(zone: com.drivesafe.kenya.data.CameraZone): Boolean {
    val min = zone.minSpeedLimitKmh
    val max = zone.maxSpeedLimitKmh
    return min != null && max != null && min != max
}

private fun buildSpeedLimitText(result: NearbyCameraResult): String {
    val zone = result.zone
    val min = zone.minSpeedLimitKmh
    val max = zone.maxSpeedLimitKmh
    return if (min != null && max != null && min != max) {
        "$min–$max km/h"
    } else {
        "${zone.speedLimitKmh ?: "—"} km/h"
    }
}

@Composable
private fun KeepScreenOn() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(Unit) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
