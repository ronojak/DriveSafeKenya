package com.drivesafe.kenya.data

import android.content.res.AssetManager
import android.util.Log
import com.google.gson.Gson
import java.io.IOException

class LocalJsonDataSource(private val assets: AssetManager) {

    fun loadCameraZones(): List<CameraZone> {
        return try {
            val json = assets.open("camera_zones.json")
                .bufferedReader()
                .use { it.readText() }
            val zones = Gson().fromJson(json, Array<CameraZone>::class.java)
            zones?.toList() ?: emptyList()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read camera_zones.json", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse camera_zones.json", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "LocalJsonDataSource"
    }
}
