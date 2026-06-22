package com.drivesafe.kenya.data

sealed class SyncResult {
    data object AlreadyUpToDate : SyncResult()
    data class Updated(val zoneCount: Int, val version: String) : SyncResult()
    data class Failed(val message: String) : SyncResult()
}
