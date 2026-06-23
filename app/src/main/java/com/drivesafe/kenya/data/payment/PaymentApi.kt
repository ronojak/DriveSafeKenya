package com.drivesafe.kenya.data.payment

import com.drivesafe.kenya.data.api.ApiConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class PaymentPlanDto(
    val code: String,
    val name: String,
    val amount: Int,
    val durationDays: Int,
    val description: String
)

data class StkPushRequest(
    val phoneNumber: String,
    val planId: String
)

data class StkPushResponse(
    val success: Boolean,
    val paymentId: String,
    val merchantRequestId: String?,
    val checkoutRequestId: String?,
    val status: String,
    val message: String?
)

data class PaymentStatusResponse(
    val paymentId: String,
    val status: String,
    val planId: String?,
    val amount: Int?,
    val receiptNumber: String?,
    val accessActive: Boolean,
    val expiryDate: String?
)

data class SubscriptionResponse(
    val active: Boolean,
    val plan: String?,
    val expiryDate: String?,
    val startDate: String?
)

interface PaymentApi {
    @GET("api/payments/plans")
    suspend fun getPlans(): List<PaymentPlanDto>

    @POST("api/payments/mpesa/stk-push")
    suspend fun stkPush(
        @Header("Authorization") bearer: String,
        @Body req: StkPushRequest
    ): StkPushResponse

    @GET("api/payments/status")
    suspend fun paymentStatus(
        @Header("Authorization") bearer: String,
        @Query("paymentId") paymentId: String
    ): PaymentStatusResponse

    @GET("api/payments/subscription")
    suspend fun subscription(
        @Header("Authorization") bearer: String
    ): SubscriptionResponse

    companion object {
        fun create(baseUrl: String = ApiConfig.BASE_URL): PaymentApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PaymentApi::class.java)
        }
    }
}
