package com.dessalines.thumbkey.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DictationSettingsMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDB::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test fun migration29To30PreservesExistingSettingsAndAddsSafeDefaults() {
        helper.createDatabase(TEST_DB, 29).apply {
            execSQL("INSERT INTO AppSettings (id) VALUES (1)")
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 30, true, MIGRATION_29_30).use { db ->
            db.query("SELECT dictation_enabled, dictation_language, dictation_model FROM AppSettings WHERE id = 1").use { cursor ->
                cursor.moveToFirst()
                assertEquals(DEFAULT_DICTATION_ENABLED, cursor.getInt(0))
                assertEquals(DEFAULT_DICTATION_LANGUAGE, cursor.getString(1))
                assertEquals(DEFAULT_DICTATION_MODEL, cursor.getString(2))
            }
        }
    }

    private companion object {
        const val TEST_DB = "dictation-migration-test"
    }
}
