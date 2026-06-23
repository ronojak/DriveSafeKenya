package com.drivesafe.kenya.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drivesafe.kenya.R
import com.drivesafe.kenya.data.payment.PaymentPlanDto

data class PaymentUiState(
    val plans: List<PaymentPlanDto> = emptyList(),
    val selectedPlanCode: String = "",
    val phoneNumber: String = "",
    val isLoading: Boolean = false,
    val phase: PaymentPhase = PaymentPhase.SELECT_PLAN,
    val message: String? = null,
    val error: String? = null,
    val receiptNumber: String? = null,
    val expiryDate: String? = null
)

enum class PaymentPhase {
    SELECT_PLAN,
    WAITING_FOR_PIN,
    POLLING,
    SUCCESS,
    FAILED
}

@Composable
fun PaymentScreen(
    state: PaymentUiState,
    onPhoneChanged: (String) -> Unit,
    onPlanSelected: (String) -> Unit,
    onPay: () -> Unit,
    onRetry: () -> Unit,
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
            text = stringResource(R.string.payment_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.payment_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        when (state.phase) {
            PaymentPhase.SELECT_PLAN -> {
                SelectPlanContent(
                    plans = state.plans,
                    selectedPlanCode = state.selectedPlanCode,
                    phoneNumber = state.phoneNumber,
                    isLoading = state.isLoading,
                    error = state.error,
                    onPlanSelected = onPlanSelected,
                    onPhoneChanged = onPhoneChanged,
                    onPay = onPay
                )
            }
            PaymentPhase.WAITING_FOR_PIN -> {
                WaitingContent(
                    message = state.message ?: stringResource(R.string.payment_check_phone)
                )
            }
            PaymentPhase.POLLING -> {
                WaitingContent(
                    message = stringResource(R.string.payment_processing)
                )
            }
            PaymentPhase.SUCCESS -> {
                SuccessContent(
                    receiptNumber = state.receiptNumber,
                    expiryDate = state.expiryDate,
                    onBack = onBack
                )
            }
            PaymentPhase.FAILED -> {
                FailedContent(
                    error = state.error ?: stringResource(R.string.payment_failed),
                    onRetry = onRetry
                )
            }
        }
    }
}

@Composable
private fun SelectPlanContent(
    plans: List<PaymentPlanDto>,
    selectedPlanCode: String,
    phoneNumber: String,
    isLoading: Boolean,
    error: String?,
    onPlanSelected: (String) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onPay: () -> Unit
) {
    Text(
        text = stringResource(R.string.payment_select_plan),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(12.dp))

    plans.forEach { plan ->
        val isSelected = plan.code == selectedPlanCode
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onPlanSelected(plan.code) },
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            ),
            border = if (isSelected)
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = plan.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = plan.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "KES ${plan.amount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (plans.isEmpty()) {
        Text(
            text = stringResource(R.string.payment_no_plans),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(modifier = Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = stringResource(R.string.payment_phone_label),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = phoneNumber,
        onValueChange = onPhoneChanged,
        label = { Text(stringResource(R.string.payment_phone_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    if (error != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onPay,
        enabled = !isLoading && selectedPlanCode.isNotBlank() && phoneNumber.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.height(20.dp)
            )
        } else {
            Text(stringResource(R.string.payment_pay_button))
        }
    }
}

@Composable
private fun WaitingContent(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SuccessContent(
    receiptNumber: String?,
    expiryDate: String?,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.payment_success),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (receiptNumber != null) {
            Text(
                text = stringResource(R.string.payment_receipt, receiptNumber),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (expiryDate != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.payment_expires, expiryDate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.payment_continue))
        }
    }
}

@Composable
private fun FailedContent(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.payment_retry))
        }
    }
}
