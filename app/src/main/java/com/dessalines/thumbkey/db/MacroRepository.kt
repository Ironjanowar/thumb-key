package com.dessalines.thumbkey.db

import androidx.room.withTransaction
import com.dessalines.thumbkey.macros.MacroDefinition
import com.dessalines.thumbkey.macros.MacroMode
import com.dessalines.thumbkey.macros.MacroValidation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MacroRepository(
    private val database: MacroDB,
) {
    private val dao = database.macroDao()
    val macros: Flow<List<MacroDefinition>> = dao.observeAll().map { rows -> rows.map(::toDomain) }

    suspend fun save(macro: MacroDefinition): Long =
        database.withTransaction {
            val existing = dao.getAll().map(::toDomain)
            require(MacroValidation.validate(macro, existing).isValid) { "Invalid macro definition" }
            val entity = MacroEntity(macro.id, macro.key, macro.caseSensitive, macro.mode.name)
            val id = if (macro.id == 0L) dao.insert(entity) else macro.id.also { dao.update(entity) }
            dao.deleteExpansions(id)
            dao.insertExpansions(macro.expansions.mapIndexed { position, text -> MacroExpansionEntity(id, position, text) })
            id
        }

    suspend fun delete(id: Long) = database.withTransaction { dao.delete(id) }

    private fun toDomain(row: MacroWithExpansions): MacroDefinition =
        MacroDefinition(
            id = row.macro.id,
            key = row.macro.key,
            expansions = row.expansions.sortedBy { it.position }.map { it.text },
            caseSensitive = row.macro.caseSensitive,
            mode = MacroMode.valueOf(row.macro.mode),
        )
}
