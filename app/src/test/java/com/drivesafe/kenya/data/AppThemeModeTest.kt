package com.drivesafe.kenya.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeModeTest {

    @Test
    fun fromStoredValue_defaultsToLightWhenValueIsMissing() {
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromStoredValue(null))
    }

    @Test
    fun fromStoredValue_defaultsToLightWhenValueIsUnknown() {
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromStoredValue("SYSTEM"))
    }

    @Test
    fun fromStoredValue_restoresDarkWhenStoredValueIsDark() {
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromStoredValue("DARK"))
    }
}
