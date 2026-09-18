package com.example.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// Entities for offline capability (safe caching)
@Entity(tableName = "draft_notes")
data class DraftNoteEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val chiefComplaint: String,
    val history: String,
    val examination: String,
    val assessment: String,
    val diagnosis: String,
    val plan: String,
    val lastUpdated: Long
)

@Dao
interface DraftNoteDao {
    @Query("SELECT * FROM draft_notes WHERE patientId = :patientId")
    fun getDraftsForPatient(patientId: String): Flow<List<DraftNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: DraftNoteEntity)

    @Query("DELETE FROM draft_notes WHERE id = :id")
    suspend fun deleteDraft(id: String)
}

@Database(entities = [DraftNoteEntity::class], version = 1, exportSchema = false)
abstract class ChettinadDatabase : RoomDatabase() {
    abstract fun draftNoteDao(): DraftNoteDao
}
