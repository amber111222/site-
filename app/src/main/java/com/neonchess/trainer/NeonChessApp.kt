package com.neonchess.trainer

import android.app.Application
import androidx.room.Room
import com.neonchess.trainer.data.AppDatabase

class NeonChessApp : Application() {
    val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "neon-chess.db")
            .fallbackToDestructiveMigration(false)
            .build()
    }
}
