package com.drivesafe.kenya.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drivesafe.kenya.R
import com.drivesafe.kenya.data.SyncMetadata
import com.drivesafe.kenya.data.UserSettings

@Composable
fun SettingsScreen(
    settings: UserSettings,
    syncMetadata: SyncMetadata?,
    syncStatus: String,
    isSyncing: Boolean,
    onVoiceAlertsChanged: (Boolean) -> Unit,
    onVibrationAlertsChanged: (Boolean) -> Unit,
    onWarningDistanceChanged: (Int) -> Unit,
    onOverspeedToleranceChanged: (Int) -> Unit,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onUpgrade: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        OutlinedButton(onClick = onBack) {
            Text(text = "← Back")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(stringResource(R.string.settings_alerts))
        Spacer(modifier = Modifier.height(8.dp))
        SwitchRow(
            label = stringResource(R.string.settings_voice_alerts),
            checked = settings.voiceAlertsEnabled,
            onCheckedChange = onVoiceAlertsChanged
        )
        Spacer(modifier = Modifier.height(8.dp))
        SwitchRow(
            label = stringResource(R.string.settings_vibration_alerts),
            checked = settings.vibrationAlertsEnabled,
            onCheckedChange = onVibrationAlertsChanged
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(stringResource(R.string.settings_detection))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_speed_tolerance),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        RadioGroup(
            options = listOf(5 to "5 km/h", 10 to "10 km/h"),
            selected = settings.overspeedToleranceKmh,
            onSelected = onOverspeedToleranceChanged
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(stringResource(R.string.settings_display))
        Spacer(modifier = Modifier.height(8.dp))
        SwitchRow(
            label = stringResource(R.string.settings_keep_screen_on),
            checked = settings.keepScreenOn,
            onCheckedChange = onKeepScreenOnChanged
        )
        Text(
            text = stringResource(R.string.settings_keep_screen_on_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(stringResource(R.string.settings_data_updates))
        Spacer(modifier = Modifier.height(12.dp))
        if (syncMetadata != null) {
            Text(
                text = stringResource(R.string.settings_data_version, syncMetadata.dataVersion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_zone_count, syncMetadata.zoneCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_data_source, syncMetadata.source),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onCheckForUpdates,
            enabled = !isSyncing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (isSyncing) stringResource(R.string.sync_checking) else stringResource(R.string.settings_check_updates))
        }
        if (syncStatus.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = syncStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(stringResource(R.string.settings_premium))
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onUpgrade,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.settings_upgrade))
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(stringResource(R.string.auth_login_title))
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.auth_logout))
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RadioGroup(
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelected: (Int) -> Unit
) {
    Column {
        options.forEach { (value, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelected(value) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == value,
                    onClick = { onSelected(value) }
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
