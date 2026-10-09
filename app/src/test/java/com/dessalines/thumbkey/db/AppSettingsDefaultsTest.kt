package com.dessalines.thumbkey.db

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsDefaultsTest {
    @Test
    fun editingModeDefaultsToDisabled() {
        assertEquals(0, DEFAULT_EDITING_MODE_ENABLED)
    }
}
