package com.example.darkmusic.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.darkmusic.data.local.dao.RecentSearchDao
import com.example.darkmusic.data.local.dao.SongDao
import com.example.darkmusic.data.local.entity.RecentSearchEntity
import com.example.darkmusic.data.local.entity.SongEntity

@Database(entities = [SongEntity::class, RecentSearchEntity::class], version = 3, exportSchema = false)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun recentSearchDao(): RecentSearchDao
}
