package com.dessalines.thumbkey.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "macros", indices = [Index(value = ["key_text"])])
data class MacroEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "key_text") val key: String,
    @ColumnInfo(name = "case_sensitive") val caseSensitive: Boolean,
    val mode: String,
)

@Entity(
    tableName = "macro_expansions",
    primaryKeys = ["macro_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = MacroEntity::class,
            parentColumns = ["id"],
            childColumns = ["macro_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("macro_id")],
)
data class MacroExpansionEntity(
    @ColumnInfo(name = "macro_id") val macroId: Long,
    val position: Int,
    val text: String,
)

data class MacroWithExpansions(
    @Embedded val macro: MacroEntity,
    @Relation(parentColumn = "id", entityColumn = "macro_id") val expansions: List<MacroExpansionEntity>,
)

@Dao
interface MacroDao {
    @Transaction
    @Query("SELECT * FROM macros ORDER BY id")
    fun observeAll(): Flow<List<MacroWithExpansions>>

    @Transaction
    @Query("SELECT * FROM macros ORDER BY id")
    suspend fun getAll(): List<MacroWithExpansions>

    @Insert suspend fun insert(entity: MacroEntity): Long

    @Update suspend fun update(entity: MacroEntity)

    @Insert suspend fun insertExpansions(entities: List<MacroExpansionEntity>)

    @Query("DELETE FROM macro_expansions WHERE macro_id = :macroId")
    suspend fun deleteExpansions(macroId: Long)

    @Query("DELETE FROM macros WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(version = 1, entities = [MacroEntity::class, MacroExpansionEntity::class], exportSchema = true)
abstract class MacroDB : RoomDatabase() {
    abstract fun macroDao(): MacroDao

    companion object {
        @Volatile private var instance: MacroDB? = null

        fun getDatabase(context: Context): MacroDB =
            instance ?: synchronized(this) {
                instance
                    ?: Room.databaseBuilder(context.applicationContext, MacroDB::class.java, "macros_db").build().also { instance = it }
            }
    }
}
