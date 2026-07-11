package com.drivesafe.kenya.ui

import android.app.Activity
import android.graphics.Paint
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivesafe.kenya.R
import com.drivesafe.kenya.alerts.NearbyCameraResult
import com.drivesafe.kenya.alerts.NearbyPolicePresenceResult
import com.drivesafe.kenya.alerts.OverspeedResult
import com.drivesafe.kenya.alerts.OverspeedStatus
import com.drivesafe.kenya.alerts.PolicePresenceAlert
import com.drivesafe.kenya.data.AppThemeMode
import com.drivesafe.kenya.data.CameraZone
import com.drivesafe.kenya.ui.theme.DriveSafeKenyaTheme
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val GaugeMaxSpeedKmh = 140f
private const val CONFIRM_ACTIONABLE_RADIUS_METERS = 500f

@Composable
fun DrivingScreen(
    speedKmh: Double?,
    isLocationAvailable: Boolean,
    nearbyCamera: NearbyCameraResult?,
    overspeedResult: OverspeedResult?,
    cameraZoneCount: Int,
    keepScreenOn: Boolean,
    themeMode: AppThemeMode,
    onToggleTheme: () -> Unit,
    onStopDriving: () -> Unit,
    nearbyPoliceAlert: NearbyPolicePresenceResult? = null,
    onReportPolicePresence: () -> Unit = {},
    policeReportMessage: String? = null,
    onConfirmPolicePresent: (String) -> Unit = {},
    onConfirmPoliceNotPresent: (String) -> Unit = {},
    driveThroughPrompt: PolicePresenceAlert? = null,
    onDriveThroughAnswer: (String, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    if (keepScreenOn) KeepScreenOn()

    DriveSafeHomeScreen(
        speedKmh = speedKmh,
        isLocationAvailable = isLocationAvailable,
        nearbyCamera = nearbyCamera,
        overspeedResult = overspeedResult,
        cameraZoneCount = cameraZoneCount,
        themeMode = themeMode,
        onToggleTheme = onToggleTheme,
        onStopDriving = onStopDriving,
        nearbyPoliceAlert = nearbyPoliceAlert,
        onReportPolicePresence = onReportPolicePresence,
        policeReportMessage = policeReportMessage,
        onConfirmPolicePresent = onConfirmPolicePresent,
        onConfirmPoliceNotPresent = onConfirmPoliceNotPresent,
        driveThroughPrompt = driveThroughPrompt,
        onDriveThroughAnswer = onDriveThroughAnswer,
        modifier = modifier
    )
}

@Composable
fun DriveSafeHomeScreen(
    speedKmh: Double?,
    isLocationAvailable: Boolean,
    nearbyCamera: NearbyCameraResult?,
    overspeedResult: OverspeedResult?,
    cameraZoneCount: Int,
    themeMode: AppThemeMode,
    onToggleTheme: () -> Unit,
    onStopDriving: () -> Unit,
    nearbyPoliceAlert: NearbyPolicePresenceResult? = null,
    onReportPolicePresence: () -> Unit = {},
    policeReportMessage: String? = null,
    onConfirmPolicePresent: (String) -> Unit = {},
    onConfirmPoliceNotPresent: (String) -> Unit = {},
    driveThroughPrompt: PolicePresenceAlert? = null,
    onDriveThroughAnswer: (String, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val isDark = themeMode == AppThemeMode.DARK
    val colors = dashboardColors(isDark)
    val speedLimitKmh = effectiveSpeedLimit(nearbyCamera, overspeedResult)
    val isOverspeed = overspeedResult?.status == OverspeedStatus.OVERSPEED
    val formattedDistance = formatDistanceParts(nearbyCamera?.distanceMeters)
    val formattedLimit = formatSpeedLimitParts(nearbyCamera)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            DriveSafeTopBar(
                isLocationAvailable = isLocationAvailable,
                themeMode = themeMode,
                onToggleTheme = onToggleTheme,
                colors = colors
            )

            SpeedGauge(
                speedKmh = speedKmh,
                speedLimitKmh = speedLimitKmh,
                isOverspeed = isOverspeed,
                colors = colors,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp, max = 340.dp)
                    .aspectRatio(1.22f)
            )

            CameraZoneCard(
                nearbyCamera = nearbyCamera,
                colors = colors
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    label = "Limit",
                    value = formattedLimit.value,
                    unit = formattedLimit.unit,
                    icon = Icons.Filled.Speed,
                    iconContentDescription = "Speed limit",
                    colors = colors,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Distance",
                    value = formattedDistance.value,
                    unit = formattedDistance.unit,
                    icon = Icons.Filled.LocationOn,
                    iconContentDescription = "Distance to camera zone",
                    colors = colors,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Zones Loaded",
                    value = cameraZoneCount.toString(),
                    unit = "zones",
                    icon = Icons.Filled.Shield,
                    iconContentDescription = "Camera zones loaded",
                    colors = colors,
                    modifier = Modifier.weight(1f)
                )
            }

            LocationStatusCard(
                isLocationAvailable = isLocationAvailable,
                colors = colors
            )

            if (nearbyPoliceAlert != null && nearbyPoliceAlert.isWithinWarningRadius) {
                if (nearbyPoliceAlert.needsConfirmation &&
                    nearbyPoliceAlert.distanceMeters <= CONFIRM_ACTIONABLE_RADIUS_METERS
                ) {
                    PoliceConfirmCard(
                        result = nearbyPoliceAlert,
                        onConfirmPresent = { onConfirmPolicePresent(nearbyPoliceAlert.alert.id) },
                        onConfirmNotPresent = { onConfirmPoliceNotPresent(nearbyPoliceAlert.alert.id) },
                        colors = colors
                    )
                } else {
                    PoliceCheckpointCard(result = nearbyPoliceAlert, colors = colors)
                }
            }

            SafetyDisclaimer(colors = colors)
        }

        if (driveThroughPrompt != null) {
            DriveThroughConfirmOverlay(
                alert = driveThroughPrompt,
                onAnswer = { present -> onDriveThroughAnswer(driveThroughPrompt.id, present) }
            )
        }

        // Pinned footer: stays on-screen regardless of scroll position above.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (policeReportMessage != null) {
                InlineStatusMessage(message = policeReportMessage, colors = colors)
            }

            DrivingActionButtons(
                onStopDriving = onStopDriving,
                onReportPolicePresence = onReportPolicePresence,
                colors = colors
            )
        }
    }
}

@Composable
private fun DriveSafeTopBar(
    isLocationAvailable: Boolean,
    themeMode: AppThemeMode,
    onToggleTheme: () -> Unit,
    colors: DashboardColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {}, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Open menu",
                tint = colors.primaryText
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.primaryText,
                maxLines = 1
            )
            Text(
                text = "Driver awareness dashboard",
                style = MaterialTheme.typography.bodySmall,
                color = colors.mutedText,
                maxLines = 1
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GpsStatusChip(isLocationAvailable = isLocationAvailable, colors = colors)
            ThemeToggleButton(themeMode = themeMode, onToggleTheme = onToggleTheme, colors = colors)
        }
    }
}

@Composable
private fun ThemeToggleButton(
    themeMode: AppThemeMode,
    onToggleTheme: () -> Unit,
    colors: DashboardColors
) {
    val isLight = themeMode == AppThemeMode.LIGHT
    IconButton(
        onClick = onToggleTheme,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colors.card)
            .border(BorderStroke(1.dp, colors.border), CircleShape)
    ) {
        Icon(
            imageVector = if (isLight) Icons.Filled.WbSunny else Icons.Filled.DarkMode,
            contentDescription = if (isLight) "Switch to dark mode" else "Switch to light mode",
            tint = if (isLight) colors.warning else colors.cameraAccent
        )
    }
}

@Composable
private fun GpsStatusChip(
    isLocationAvailable: Boolean,
    colors: DashboardColors
) {
    val background = if (isLocationAvailable) colors.successContainer else colors.warningContainer
    val foreground = if (isLocationAvailable) colors.success else colors.warning
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .border(BorderStroke(1.dp, foreground.copy(alpha = 0.24f)), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = if (isLocationAvailable) Icons.Filled.GpsFixed else Icons.Filled.GpsNotFixed,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (isLocationAvailable) "GPS Active" else "GPS Searching",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
            maxLines = 1
        )
    }
}

@Composable
private fun SpeedGauge(
    speedKmh: Double?,
    speedLimitKmh: Int?,
    isOverspeed: Boolean,
    colors: DashboardColors,
    modifier: Modifier = Modifier
) {
    val speedText = speedKmh?.roundToInt()?.toString() ?: "--"
    val progress = ((speedKmh ?: 0.0) / GaugeMaxSpeedKmh).toFloat().coerceIn(0f, 1f)
    val nearLimit = speedKmh != null &&
        speedLimitKmh != null &&
        !isOverspeed &&
        speedKmh >= speedLimitKmh - 5
    val centerColor = when {
        isOverspeed -> colors.danger
        nearLimit -> colors.warning
        speedKmh == null -> colors.mutedText
        else -> colors.primaryText
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 18.dp.toPx()
            val gaugePadding = 34.dp.toPx()
            val radius = min(size.width / 2f - gaugePadding, size.height - 70.dp.toPx())
            val center = Offset(size.width / 2f, size.height - 44.dp.toPx())
            val arcSize = Size(radius * 2f, radius * 2f)
            val arcTopLeft = Offset(center.x - radius, center.y - radius)

            drawArc(
                color = colors.gaugeTrack,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawActiveGaugeSegment(
                progress = progress,
                segmentStart = 0f,
                segmentEnd = 0.58f,
                color = colors.success,
                topLeft = arcTopLeft,
                size = arcSize,
                strokeWidth = strokeWidth
            )
            drawActiveGaugeSegment(
                progress = progress,
                segmentStart = 0.58f,
                segmentEnd = 0.82f,
                color = colors.warning,
                topLeft = arcTopLeft,
                size = arcSize,
                strokeWidth = strokeWidth
            )
            drawActiveGaugeSegment(
                progress = progress,
                segmentStart = 0.82f,
                segmentEnd = 1f,
                color = colors.danger,
                topLeft = arcTopLeft,
                size = arcSize,
                strokeWidth = strokeWidth
            )

            for (speed in 0..GaugeMaxSpeedKmh.toInt() step 10) {
                val isMajor = speed % 20 == 0
                val angle = 180f + (speed / GaugeMaxSpeedKmh) * 180f
                val tickLength = if (isMajor) 13.dp.toPx() else 7.dp.toPx()
                val outer = pointOnGauge(center, radius - 1.dp.toPx(), angle)
                val inner = pointOnGauge(center, radius - tickLength, angle)
                drawLine(
                    color = if (isMajor) colors.tickMajor else colors.tickMinor,
                    start = outer,
                    end = inner,
                    strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.tickLabel.toArgb()
                textAlign = Paint.Align.CENTER
                textSize = 11.sp.toPx()
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            for (speed in 0..GaugeMaxSpeedKmh.toInt() step 20) {
                val angle = 180f + (speed / GaugeMaxSpeedKmh) * 180f
                val labelPoint = pointOnGauge(center, radius - 33.dp.toPx(), angle)
                drawContext.canvas.nativeCanvas.drawText(
                    speed.toString(),
                    labelPoint.x,
                    labelPoint.y + 4.dp.toPx(),
                    labelPaint
                )
            }

            val needleAngle = 180f + progress * 180f
            val needleStart = pointOnGauge(center, 10.dp.toPx(), needleAngle + 180f)
            val needleEnd = pointOnGauge(center, radius - 54.dp.toPx(), needleAngle)
            drawLine(
                color = colors.needle,
                start = needleStart,
                end = needleEnd,
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(color = colors.needle, radius = 8.dp.toPx(), center = center)
            drawCircle(color = colors.card, radius = 4.dp.toPx(), center = center)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = speedText,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.ExtraBold,
                color = centerColor,
                maxLines = 1
            )
            Text(
                text = "km/h",
                style = MaterialTheme.typography.titleMedium,
                color = colors.mutedText,
                maxLines = 1
            )
            SpeedLimitBadge(speedLimitKmh = speedLimitKmh, colors = colors)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawActiveGaugeSegment(
    progress: Float,
    segmentStart: Float,
    segmentEnd: Float,
    color: Color,
    topLeft: Offset,
    size: Size,
    strokeWidth: Float
) {
    val visibleEnd = min(progress, segmentEnd)
    if (visibleEnd <= segmentStart) return
    drawArc(
        color = color,
        startAngle = 180f + segmentStart * 180f,
        sweepAngle = (visibleEnd - segmentStart) * 180f,
        useCenter = false,
        topLeft = topLeft,
        size = size,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.pointOnGauge(
    center: Offset,
    radius: Float,
    angleDegrees: Float
): Offset {
    val radians = angleDegrees * (PI.toFloat() / 180f)
    return Offset(
        x = center.x + cos(radians) * radius,
        y = center.y + sin(radians) * radius
    )
}

@Composable
private fun SpeedLimitBadge(speedLimitKmh: Int?, colors: DashboardColors) {
    Row(
        modifier = Modifier
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.badgeBackground)
            .border(BorderStroke(1.dp, colors.cameraAccent.copy(alpha = 0.3f)), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "SPEED LIMIT",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colors.cameraAccent,
            maxLines = 1
        )
        Text(
            text = if (speedLimitKmh != null) "$speedLimitKmh km/h" else "--",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colors.primaryText,
            maxLines = 1
        )
    }
}

@Composable
private fun CameraZoneCard(
    nearbyCamera: NearbyCameraResult?,
    colors: DashboardColors
) {
    val title = if (nearbyCamera != null) "Camera Zone Ahead" else "No Camera Zone Nearby"
    val subtitle = nearbyCamera?.zone?.let { cameraZoneSubtitle(it) } ?: "Drive safely"
    val speedLimit = nearbyCamera?.let { buildSpeedLimitText(it) } ?: "--"
    val distance = nearbyCamera?.distanceMeters?.let { formatDistance(it) } ?: "--"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 5.dp),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.cameraContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = "Camera zone",
                    tint = colors.cameraAccent,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedText,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    MetricText(label = "Limit", value = speedLimit, colors = colors)
                    MetricText(label = "Distance", value = distance, colors = colors)
                }
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = colors.mutedText,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun MetricText(label: String, value: String, colors: DashboardColors) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.mutedText,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.cameraAccent,
            maxLines = 1
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    iconContentDescription: String,
    colors: DashboardColors,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(130.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 3.dp),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription,
                tint = colors.cameraAccent,
                modifier = Modifier.size(21.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.mutedText,
                    maxLines = 2
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.primaryText,
                    maxLines = 1
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.mutedText,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun LocationStatusCard(
    isLocationAvailable: Boolean,
    colors: DashboardColors
) {
    val foreground = if (isLocationAvailable) colors.success else colors.warning
    val title = if (isLocationAvailable) "Location accurate" else "Location not available"
    val message = if (isLocationAvailable) {
        "You're all set. Drive safely."
    } else {
        "Please try again in a few seconds."
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (isLocationAvailable) colors.successContainer else colors.warningContainer)
            .border(BorderStroke(1.dp, foreground.copy(alpha = 0.18f)), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (isLocationAvailable) Icons.Filled.GpsFixed else Icons.Filled.GpsNotFixed,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(28.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = foreground,
                maxLines = 1
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primaryText,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun DrivingActionButtons(
    onStopDriving: () -> Unit,
    onReportPolicePresence: () -> Unit,
    colors: DashboardColors
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onStopDriving,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(29.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.danger,
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Filled.StopCircle,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.stop_driving),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        OutlinedButton(
            onClick = onReportPolicePresence,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(29.dp),
            border = BorderStroke(1.5.dp, colors.warning),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = colors.warningContainer,
                contentColor = colors.warning
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.report_police_presence),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun InlineStatusMessage(message: String, colors: DashboardColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.card)
            .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.primaryText,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SafetyDisclaimer(colors: DashboardColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Filled.Security,
            contentDescription = null,
            tint = colors.mutedText,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = colors.mutedText,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PoliceCheckpointCard(result: NearbyPolicePresenceResult, colors: DashboardColors) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = colors.warningContainer),
        border = BorderStroke(1.dp, colors.warning.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.police_checkpoint_ahead),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.warning
                )
                Text(
                    text = formatDistance(result.distanceMeters),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.primaryText
                )
            }
        }
    }
}

@Composable
private fun PoliceConfirmCard(
    result: NearbyPolicePresenceResult,
    onConfirmPresent: () -> Unit,
    onConfirmNotPresent: () -> Unit,
    colors: DashboardColors
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = colors.warningContainer),
        border = BorderStroke(1.dp, colors.warning.copy(alpha = 0.22f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.police_checkpoint_ahead),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.warning
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatDistance(result.distanceMeters),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primaryText
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.police_confirm_question),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primaryText
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConfirmPresent,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(23.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.success)
                ) {
                    Text(text = stringResource(R.string.police_confirm_present))
                }
                OutlinedButton(
                    onClick = onConfirmNotPresent,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(23.dp),
                    border = BorderStroke(1.dp, colors.warning),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.warning)
                ) {
                    Text(text = stringResource(R.string.police_confirm_not_present))
                }
            }
        }
    }
}

@Composable
private fun DriveThroughConfirmOverlay(
    alert: PolicePresenceAlert,
    onAnswer: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2028)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.drive_through_prompt_question),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onAnswer(true) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text(
                        text = stringResource(R.string.drive_through_still_there),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = { onAnswer(false) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF667085))
                ) {
                    Text(
                        text = stringResource(R.string.drive_through_gone),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun effectiveSpeedLimit(
    nearbyCamera: NearbyCameraResult?,
    overspeedResult: OverspeedResult?
): Int? = overspeedResult?.applicableLimitKmh ?: nearbyCamera?.zone?.let { zone ->
    val min = zone.minSpeedLimitKmh
    val max = zone.maxSpeedLimitKmh
    when {
        min != null && max != null && min != max -> max
        zone.speedLimitKmh != null -> zone.speedLimitKmh
        max != null -> max
        else -> min
    }
}

private fun isVariableZone(zone: CameraZone): Boolean {
    val min = zone.minSpeedLimitKmh
    val max = zone.maxSpeedLimitKmh
    return min != null && max != null && min != max
}

private fun buildSpeedLimitText(result: NearbyCameraResult): String {
    val zone = result.zone
    val min = zone.minSpeedLimitKmh
    val max = zone.maxSpeedLimitKmh
    return if (min != null && max != null && min != max) {
        "$min-$max km/h"
    } else {
        "${zone.speedLimitKmh ?: max ?: min ?: "--"} km/h"
    }
}

private fun formatSpeedLimitParts(result: NearbyCameraResult?): ValueUnit {
    if (result == null) return ValueUnit("--", "")
    val zone = result.zone
    val min = zone.minSpeedLimitKmh
    val max = zone.maxSpeedLimitKmh
    return if (isVariableZone(zone)) {
        ValueUnit("$min-$max", "km/h")
    } else {
        ValueUnit((zone.speedLimitKmh ?: max ?: min)?.toString() ?: "--", "km/h")
    }
}

private fun formatDistanceParts(meters: Float?): ValueUnit {
    if (meters == null) return ValueUnit("--", "")
    return if (meters >= 1000f) {
        ValueUnit(String.format(Locale.US, "%.1f", meters / 1000f), "km")
    } else {
        ValueUnit(meters.roundToInt().toString(), "m")
    }
}

private fun formatDistance(meters: Float): String =
    if (meters >= 1000f) {
        "${String.format(Locale.US, "%.1f", meters / 1000f)} km"
    } else {
        "${meters.roundToInt()} m"
    }

private fun cameraZoneSubtitle(zone: CameraZone): String =
    listOf(zone.roadName, zone.locationName)
        .filter { it.isNotBlank() }
        .joinToString(" / ")
        .ifBlank { zone.cameraType.replace('_', ' ') }

private data class ValueUnit(val value: String, val unit: String)

@Composable
private fun dashboardColors(isDark: Boolean): DashboardColors =
    if (isDark) {
        DashboardColors(
            isDark = true,
            background = Color(0xFF101418),
            card = Color(0xFF1A2028),
            primaryText = Color(0xFFF7F8FA),
            mutedText = Color(0xFFB9C0CA),
            border = Color(0xFF303946),
            success = Color(0xFF66D19E),
            successContainer = Color(0x2230D158),
            warning = Color(0xFFFFB020),
            warningContainer = Color(0x22FFB020),
            danger = Color(0xFFFF5A5F),
            cameraAccent = Color(0xFF8EA7FF),
            cameraContainer = Color(0x242563EB),
            badgeBackground = Color(0xFF202733),
            gaugeTrack = Color(0xFF303946),
            tickMajor = Color(0xFFE5E7EB),
            tickMinor = Color(0xFF778190),
            tickLabel = Color(0xFFCAD1DB),
            needle = Color(0xFFF7F8FA)
        )
    } else {
        DashboardColors(
            isDark = false,
            background = Color(0xFFFAFBF7),
            card = Color.White,
            primaryText = Color(0xFF20242A),
            mutedText = Color(0xFF667085),
            border = Color(0xFFE6E9EF),
            success = Color(0xFF2E7D32),
            successContainer = Color(0xFFEAF7EE),
            warning = Color(0xFFF59E0B),
            warningContainer = Color(0xFFFFF4DC),
            danger = Color(0xFFE5484D),
            cameraAccent = Color(0xFF6554C0),
            cameraContainer = Color(0xFFEDEBFF),
            badgeBackground = Color.White,
            gaugeTrack = Color(0xFFE4E8EE),
            tickMajor = Color(0xFF344054),
            tickMinor = Color(0xFF98A2B3),
            tickLabel = Color(0xFF475467),
            needle = Color(0xFF20242A)
        )
    }

private data class DashboardColors(
    val isDark: Boolean,
    val background: Color,
    val card: Color,
    val primaryText: Color,
    val mutedText: Color,
    val border: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val cameraAccent: Color,
    val cameraContainer: Color,
    val badgeBackground: Color,
    val gaugeTrack: Color,
    val tickMajor: Color,
    val tickMinor: Color,
    val tickLabel: Color,
    val needle: Color
)

@Composable
private fun KeepScreenOn() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(Unit) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DriveSafeHomeScreenLightPreview() {
    DriveSafeKenyaTheme(darkTheme = false) {
        DriveSafeHomeScreen(
            speedKmh = 48.0,
            isLocationAvailable = true,
            nearbyCamera = previewNearbyCamera(),
            overspeedResult = OverspeedResult(
                status = OverspeedStatus.WITHIN_LIMIT,
                applicableLimitKmh = 50,
                toleranceKmh = 5
            ),
            cameraZoneCount = 20,
            themeMode = AppThemeMode.LIGHT,
            onToggleTheme = {},
            onStopDriving = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DriveSafeHomeScreenDarkPreview() {
    DriveSafeKenyaTheme(darkTheme = true) {
        DriveSafeHomeScreen(
            speedKmh = 74.0,
            isLocationAvailable = true,
            nearbyCamera = previewNearbyCamera(),
            overspeedResult = OverspeedResult(
                status = OverspeedStatus.OVERSPEED,
                applicableLimitKmh = 50,
                toleranceKmh = 5
            ),
            cameraZoneCount = 20,
            themeMode = AppThemeMode.DARK,
            onToggleTheme = {},
            onStopDriving = {}
        )
    }
}

private fun previewNearbyCamera(): NearbyCameraResult =
    NearbyCameraResult(
        zone = CameraZone(
            id = "preview",
            roadName = "Urban Roads",
            locationName = "Junctions",
            latitude = -1.2921,
            longitude = 36.8219,
            speedLimitKmh = 50,
            minSpeedLimitKmh = null,
            maxSpeedLimitKmh = 50,
            warningRadiusMeters = 1200,
            cameraType = "speed_camera_zone",
            direction = null,
            status = "active",
            verified = true,
            source = "preview",
            lastUpdated = "2026-06-30"
        ),
        distanceMeters = 821f,
        isInWarningRadius = true
    )
