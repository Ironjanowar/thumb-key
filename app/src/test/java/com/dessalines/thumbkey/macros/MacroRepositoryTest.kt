package com.dessalines.thumbkey.macros

import android.content.Context
import androidx.room.Room
import com.dessalines.thumbkey.db.MacroDB
import com.dessalines.thumbkey.db.MacroRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MacroRepositoryTest {
    private lateinit var db: MacroDB
    private lateinit var repository: MacroRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MacroDB::class.java).allowMainThreadQueries().build()
        repository = MacroRepository(db)
    }

    @After fun tearDown() = db.close()

    @Test fun `save observes ordered expansions and update removes old children`() = runBlocking {
        val id = repository.save(MacroDefinition(key = "saludo", expansions = listOf("Hola", "Buenas")))
        assertEquals(listOf("Hola", "Buenas"), repository.macros.first().single().expansions)
        repository.save(MacroDefinition(id, "saludo", listOf("Qué tal")))
        assertEquals(listOf("Qué tal"), repository.macros.first().single().expansions)
    }

    @Test fun `invalid conflict is rejected and delete cascades`() = runBlocking {
        repository.save(MacroDefinition(key = "JJ", expansions = listOf("x")))
        try {
            repository.save(MacroDefinition(key = "jj", expansions = listOf("y")))
            fail("Expected a key conflict")
        } catch (_: IllegalArgumentException) {
        }
        repository.delete(repository.macros.first().single().id)
        assertEquals(emptyList<MacroDefinition>(), repository.macros.first())
    }

    @Test fun `named database survives close and reopen`() = runBlocking {
        db.close()
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("macro-test")
        var named = Room.databaseBuilder(context, MacroDB::class.java, "macro-test").allowMainThreadQueries().build()
        MacroRepository(named).save(MacroDefinition(key = "x", expansions = listOf("🙂\ntext")))
        named.close()
        named = Room.databaseBuilder(context, MacroDB::class.java, "macro-test").allowMainThreadQueries().build()
        assertEquals("🙂\ntext", MacroRepository(named).macros.first().single().expansions.single())
        named.close()
        context.deleteDatabase("macro-test")
        Unit
    }
}
