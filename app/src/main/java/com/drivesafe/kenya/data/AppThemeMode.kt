package com.drivesafe.kenya.data

enum class AppThemeMode {
    LIGHT,
    DARK;

    companion object {
        fun fromStoredValue(value: String?): AppThemeMode =
            entries.firstOrNull { it.name == value } ?: LIGHT
    }
}
