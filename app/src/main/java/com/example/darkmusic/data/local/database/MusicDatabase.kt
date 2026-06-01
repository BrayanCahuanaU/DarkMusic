package com.example.darkmusic.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.darkmusic.data.local.dao.SongDao
import com.example.darkmusic.data.local.entity.SongEntity

@Database(entities = [SongEntity::class], version = 2, exportSchema = false)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
}
