package com.movies.premium.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.movies.premium.data.local.dao.ContinueWatchingDao
import com.movies.premium.data.local.dao.DownloadDao
import com.movies.premium.data.local.dao.FavoriteDao
import com.movies.premium.data.local.dao.HistoryDao
import com.movies.premium.data.local.dao.IptvChannelDao
import com.movies.premium.data.local.dao.WatchlistDao
import com.movies.premium.data.local.entity.ContinueWatchingEntity
import com.movies.premium.data.local.entity.DownloadEntity
import com.movies.premium.data.local.entity.FavoriteEntity
import com.movies.premium.data.local.entity.HistoryEntity
import com.movies.premium.data.local.entity.IptvChannelEntity
import com.movies.premium.data.local.entity.WatchlistEntity

@Database(
    entities = [
        WatchlistEntity::class,
        HistoryEntity::class,
        DownloadEntity::class,
        FavoriteEntity::class,
        ContinueWatchingEntity::class,
        IptvChannelEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun continueWatchingDao(): ContinueWatchingDao
    abstract fun iptvChannelDao(): IptvChannelDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "cinemahq.db")
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // The original sets a busy timeout to avoid SQLITE_BUSY under concurrent writes.
                        try {
                            db.execSQL("PRAGMA busy_timeout = 10000")
                        } catch (_: Exception) {
                        }
                    }
                })
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()

        /**
         * Adds the adult flag to the watchlist and the history.
         *
         * The columns are added with a default of `0`, so the rows that are
         * already there stay and the user's favourites and watch history are
         * not lost when the app is updated.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `watchlist` ADD COLUMN `isAdult` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `history` ADD COLUMN `isAdult` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
