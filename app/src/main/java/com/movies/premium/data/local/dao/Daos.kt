package com.movies.premium.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.movies.premium.data.local.entity.ContinueWatchingEntity
import com.movies.premium.data.local.entity.DownloadEntity
import com.movies.premium.data.local.entity.FavoriteEntity
import com.movies.premium.data.local.entity.HistoryEntity
import com.movies.premium.data.local.entity.IptvChannelEntity
import com.movies.premium.data.local.entity.WatchlistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist WHERE tmdbId = :tmdbId AND mediaType = :mediaType LIMIT 1")
    fun observeOne(tmdbId: Int, mediaType: String): Flow<WatchlistEntity?>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE tmdbId = :tmdbId AND mediaType = :mediaType)")
    suspend fun contains(tmdbId: Int, mediaType: String): Boolean

    @Upsert
    suspend fun upsert(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    suspend fun delete(tmdbId: Int, mediaType: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY watchedAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE `key` = :key LIMIT 1")
    suspend fun find(key: String): HistoryEntity?

    @Upsert
    suspend fun upsert(item: HistoryEntity)

    @Query("DELETE FROM history WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM history")
    suspend fun clear()
}

/**
 * Mirrors the original's `data.local.dao.d`. The list query is `ORDER BY addedAt DESC`
 * (the original's DAO order — note the *manager* picks the oldest pending row separately,
 * so the two are not in conflict).
 */
@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun find(id: Long): DownloadEntity?

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadEntity): Long

    @Query(
        "UPDATE downloads SET status = :status, progress = :progress, " +
            "downloadedBytes = :downloadedBytes WHERE id = :id"
    )
    suspend fun updateProgress(id: Long, status: String, progress: Int, downloadedBytes: Long)

    @Query(
        "UPDATE downloads SET status = 'completed', progress = 100, completedAt = :completedAt, " +
            "filePath = :filePath, totalBytes = :totalBytes, downloadedBytes = :downloadedBytes WHERE id = :id"
    )
    suspend fun markCompleted(
        id: Long,
        completedAt: Long,
        filePath: String,
        totalBytes: Long,
        downloadedBytes: Long,
    )

    @Query("UPDATE downloads SET status = 'failed', failureReason = :reason WHERE id = :id")
    suspend fun markFailed(id: Long, reason: String)

    @Query("UPDATE downloads SET status = 'paused' WHERE id = :id")
    suspend fun markPaused(id: Long)

    @Query("UPDATE downloads SET status = 'pending' WHERE id = :id")
    suspend fun markPending(id: Long)

    @Query("UPDATE downloads SET status = 'cancelled' WHERE id = :id")
    suspend fun markCancelled(id: Long)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE tmdbId = :tmdbId AND mediaType = :mediaType)")
    suspend fun contains(tmdbId: Int, mediaType: String): Boolean

    @Upsert
    suspend fun upsert(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    suspend fun delete(tmdbId: Int, mediaType: String)
}

@Dao
interface ContinueWatchingDao {
    @Query("SELECT * FROM continue_watching ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ContinueWatchingEntity>>

    @Upsert
    suspend fun upsert(item: ContinueWatchingEntity)

    @Query("DELETE FROM continue_watching WHERE `key` = :key")
    suspend fun delete(key: String)
}

/**
 * Mirrors the original's `com.app.mlounge.data.local.dao.h` / `dao.i` (the
 * channel DAO). The SQL below is copied verbatim from the original APK:
 *
 * - `DELETE FROM iptv_channels WHERE provider = ?`
 * - `SELECT DISTINCT groupName FROM iptv_channels WHERE provider = ? ORDER BY groupName ASC`
 * - `SELECT * FROM iptv_channels WHERE provider = ? AND (? IS NULL OR groupName = ?) ORDER BY name ASC`
 * - `SELECT * FROM iptv_channels WHERE name LIKE '%' || ? || '%' LIMIT ?`
 *
 * The bulk insert is `INSERT OR REPLACE` over the auto-generated `Long` key,
 * which Room 2.8 compiles to
 * `INSERT OR REPLACE INTO \`iptv_channels\` (...) VALUES (nullif(?, 0),?,?,?,?,?,?)`.
 */
@Dao
interface IptvChannelDao {
    @Query("DELETE FROM iptv_channels WHERE provider = :provider")
    suspend fun deleteByProvider(provider: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<IptvChannelEntity>)

    @Query("SELECT DISTINCT groupName FROM iptv_channels WHERE provider = :provider ORDER BY groupName ASC")
    suspend fun getGroups(provider: String): List<String?>

    @Query("SELECT DISTINCT groupName FROM iptv_channels WHERE provider = :provider ORDER BY groupName ASC")
    fun observeGroups(provider: String): Flow<List<String?>>

    @Query("SELECT * FROM iptv_channels WHERE provider = :provider AND (:groupName IS NULL OR groupName = :groupName) ORDER BY name ASC")
    suspend fun getChannels(provider: String, groupName: String?): List<IptvChannelEntity>

    @Query("SELECT * FROM iptv_channels WHERE provider = :provider AND (:groupName IS NULL OR groupName = :groupName) ORDER BY name ASC")
    fun observeChannels(provider: String, groupName: String?): Flow<List<IptvChannelEntity>>

    @Query("SELECT * FROM iptv_channels WHERE provider = :provider ORDER BY name ASC")
    suspend fun getByProvider(provider: String): List<IptvChannelEntity>

    @Query("SELECT * FROM iptv_channels WHERE provider = :provider ORDER BY name ASC")
    fun observeByProvider(provider: String): Flow<List<IptvChannelEntity>>

    @Query("SELECT * FROM iptv_channels ORDER BY name ASC")
    fun observeAll(): Flow<List<IptvChannelEntity>>

    @Query("SELECT * FROM iptv_channels WHERE name LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun search(query: String, limit: Int): List<IptvChannelEntity>

    @Query("SELECT * FROM iptv_channels WHERE id = :id LIMIT 1")
    suspend fun find(id: Long): IptvChannelEntity?

    @Query("SELECT COUNT(*) FROM iptv_channels WHERE provider = :provider")
    suspend fun countForProvider(provider: String): Int

    @Query("SELECT COUNT(*) FROM iptv_channels")
    suspend fun count(): Int

    @Query("DELETE FROM iptv_channels")
    suspend fun clear()
}
