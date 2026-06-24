package com.drivesafe.kenya.data.payment

import com.drivesafe.kenya.data.auth.SessionManager
import kotlinx.coroutines.delay

sealed class PaymentResult {
    data class StkPushSent(val paymentId: String, val message: String) : PaymentResult()
    data class Paid(val receiptNumber: String?, val expiryDate: String?) : PaymentResult()
    data class Failed(val message: String) : PaymentResult()
    data object Cancelled : PaymentResult()
    data object Timeout : PaymentResult()
}

class PaymentRepository(
    private val api: PaymentApi,
    private val session: SessionManager
) {

    private suspend fun bearer(): String? = session.token()?.let { "Bearer $it" }

    suspend fun getPlans(): List<PaymentPlanDto> {
        return try {
            api.getPlans()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun initiateStkPush(phoneNumber: String, planId: String): PaymentResult {
        val b = bearer() ?: return PaymentResult.Failed("Not logged in")
        return try {
            val resp = api.stkPush(b, StkPushRequest(phoneNumber, planId))
            if (resp.success) {
                PaymentResult.StkPushSent(resp.paymentId, resp.message ?: "Check your phone")
            } else {
                PaymentResult.Failed("Could not initiate payment")
            }
        } catch (e: retrofit2.HttpException) {
            when (e.code()) {
                429 -> PaymentResult.Failed("Please wait before trying again")
                400 -> PaymentResult.Failed("Invalid phone number or plan")
                else -> PaymentResult.Failed("Payment initiation failed")
            }
        } catch (e: Exception) {
            PaymentResult.Failed(e.message ?: "Connection failed")
        }
    }

    suspend fun pollPaymentStatus(paymentId: String): PaymentResult {
        val b = bearer() ?: return PaymentResult.Failed("Not logged in")
        val startTime = System.currentTimeMillis()
        val timeoutMs = 45_000L

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                val resp = api.paymentStatus(b, paymentId)
                when (resp.status) {
                    "PAID" -> return PaymentResult.Paid(resp.receiptNumber, resp.expiryDate)
                    "FAILED" -> return PaymentResult.Failed("Payment failed")
                    "CANCELLED" -> return PaymentResult.Cancelled
                    "EXPIRED" -> return PaymentResult.Failed("Payment expired")
                }
            } catch (_: Exception) { }
            delay(3000)
        }
        return PaymentResult.Timeout
    }

    suspend fun getSubscription(): SubscriptionResponse? {
        val b = bearer() ?: return null
        return try {
            api.subscription(b)
        } catch (_: Exception) {
            null
        }
    }
}
