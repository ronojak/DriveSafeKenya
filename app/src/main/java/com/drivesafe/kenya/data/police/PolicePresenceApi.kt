package com.drivesafe.kenya.data.police

import com.drivesafe.kenya.data.api.ApiConfig
import com.google.gson.GsonBuilder
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PolicePresenceApi {

    @POST("police-presence/report")
    suspend fun report(
        @Body body: ReportPolicePresenceRequest
    ): Response<PolicePresenceAlertDto>

    @GET("police-presence/active")
    suspend fun getActive(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius_meters") radiusMeters: Int = 10_000
    ): Response<ActiveAlertsDto>

    @POST("police-presence/{alertId}/confirm")
    suspend fun confirmPresent(
        @Path("alertId") alertId: String,
        @Body body: ConfirmRequest
    ): Response<PolicePresenceAlertDto>

    @POST("police-presence/{alertId}/not-present")
    suspend fun confirmNotPresent(
        @Path("alertId") alertId: String,
        @Body body: ConfirmRequest
    ): Response<PolicePresenceAlertDto>

    companion object {
        fun create(): PolicePresenceApi = Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(PolicePresenceApi::class.java)
    }
}
