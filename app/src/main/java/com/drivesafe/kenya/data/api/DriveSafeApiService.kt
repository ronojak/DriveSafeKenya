package com.drivesafe.kenya.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

interface DriveSafeApiService {

    @GET("api/camera-zones/version")
    suspend fun getVersion(): VersionResponse

    @GET("api/camera-zones")
    suspend fun getCameraZones(): CameraZonesResponse

    companion object {
        fun create(baseUrl: String = ApiConfig.BASE_URL): DriveSafeApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(DriveSafeApiService::class.java)
        }
    }
}
