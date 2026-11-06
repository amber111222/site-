package com.neonchess.trainer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val id: String,
    val source: String,
    val white: String,
    val black: String,
    val result: String,
    val playedAt: Long,
    val pgn: String
)

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY playedAt DESC")
    fun observeAll(): Flow<List<GameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(games: List<GameEntity>)
}

@Database(entities = [GameEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
}
