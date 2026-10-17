package io.github.enan511.drivingcompanion.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.enan511.drivingcompanion.core.drive.SessionSnapshot
import kotlinx.serialization.json.Json

/** One atomic checkpoint keeps history, selected car, period and choice token consistent. */
@Entity(tableName = "session_checkpoint")
data class SessionCheckpoint(@PrimaryKey val id: Int = 1, val payload: String)

@Dao
interface SessionCheckpointDao {
    @Query("SELECT * FROM session_checkpoint WHERE id = 1")
    suspend fun read(): SessionCheckpoint?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun write(checkpoint: SessionCheckpoint)
}

@Database(entities = [SessionCheckpoint::class], version = 1, exportSchema = true)
abstract class SessionDatabase : RoomDatabase() {
    abstract fun checkpointDao(): SessionCheckpointDao
}

class SessionStore(private val database: SessionDatabase) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun read(): SessionSnapshot = database.checkpointDao().read()?.let {
        json.decodeFromString<SessionSnapshot>(it.payload)
    } ?: SessionSnapshot()

    suspend fun write(snapshot: SessionSnapshot) {
        database.checkpointDao().write(SessionCheckpoint(payload = json.encodeToString(snapshot)))
    }

    companion object {
        fun create(context: Context): SessionStore = SessionStore(
            Room.databaseBuilder(context.applicationContext, SessionDatabase::class.java, "driving-sessions.db").build(),
        )
    }
}
