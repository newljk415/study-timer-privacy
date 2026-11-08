package com.newljk415.studytimer

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun versionIsConfigured() {
        assertEquals("1.0.4", BuildConfig.VERSION_NAME)
        assertEquals(5, BuildConfig.VERSION_CODE)
    }
}
