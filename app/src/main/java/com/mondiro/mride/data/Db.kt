package com.mondiro.mride.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "trips", indices = [Index(value = ["uuid"], unique = true)])
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val startTime: Long,
    val endTime: Long,
    val distanceM: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val pointCount: Int = 0,
    val active: Boolean = true,
    val synced: Boolean = false,
    val name: String = "",
    val preview: String = "",
)

@Entity(
    tableName = "points",
    indices = [Index("tripId")],
    foreignKeys = [ForeignKey(
        entity = Trip::class,
        parentColumns = ["id"],
        childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val time: Long,
    val lat: Double,
    val lon: Double,
    val speedKmh: Float,
    val accuracy: Float,
)

@Dao
interface RideDao {
    @Query("SELECT * FROM trips ORDER BY startTime DESC")
    fun observeTrips(): Flow<List<Trip>>

    @Query("SELECT * FROM points WHERE tripId IN (SELECT id FROM trips WHERE startTime >= :from AND startTime < :to) ORDER BY time")
    fun observePointsBetween(from: Long, to: Long): Flow<List<TrackPoint>>

    @Query("SELECT * FROM points WHERE tripId IN (:ids) ORDER BY time")
    suspend fun pointsFor(ids: List<Long>): List<TrackPoint>

    @Query("SELECT * FROM points WHERE tripId = :id ORDER BY time")
    suspend fun points(id: Long): List<TrackPoint>

    @Query("SELECT * FROM trips WHERE id IN (:ids) ORDER BY startTime")
    suspend fun tripsByIds(ids: List<Long>): List<Trip>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun trip(id: Long): Trip?

    @Query("SELECT * FROM trips WHERE active = 1 LIMIT 1")
    suspend fun activeTrip(): Trip?

    @Query("SELECT * FROM trips WHERE active = 0 AND synced = 0")
    suspend fun unsynced(): List<Trip>

    @Query("SELECT uuid FROM trips")
    suspend fun allUuids(): List<String>

    @Query("UPDATE trips SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Insert
    suspend fun insertTrip(t: Trip): Long

    @Update
    suspend fun updateTrip(t: Trip)

    @Insert
    suspend fun insertPoint(p: TrackPoint): Long

    @Insert
    suspend fun insertPoints(p: List<TrackPoint>)

    @Query("DELETE FROM points WHERE tripId = :tripId AND time > :after")
    suspend fun trimPoints(tripId: Long, after: Long)

    @Query("DELETE FROM points WHERE tripId = :id")
    suspend fun deletePoints(id: Long)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTripRow(id: Long)

    @Transaction
    suspend fun deleteTrip(id: Long) {
        deletePoints(id)
        deleteTripRow(id)
    }
}

@Database(entities = [Trip::class, TrackPoint::class], version = 1, exportSchema = false)
abstract class RideDb : RoomDatabase() {
    abstract fun dao(): RideDao

    companion object {
        @Volatile private var inst: RideDb? = null
        fun get(ctx: Context): RideDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, RideDb::class.java, "mride.db")
                .build().also { inst = it }
        }
    }
}
