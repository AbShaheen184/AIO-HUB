package com.movies.premium.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val overview: String? = null,
    val rating: Double = 0.0,
    val releaseDate: String? = null,
    /**
     * Whether TMDB flagged the title as an adult one.
     *
     * The original's "Include Adult in Favourites" switch
     * (`include_adult_in_watchlist`) decides whether such a title is shown in
     * the favourites, so the flag has to travel with the row.
     */
    val isAdult: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val key: String,
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String? = null,
    val episodeNumber: Int? = null,
    val seasonNumber: Int? = null,
    val progressMs: Long = 0,
    val durationMs: Long = 0,
    /**
     * Whether TMDB flagged the title as an adult one.
     *
     * The original's "Include Adult in History" switch
     * (`include_adult_in_history`) decides whether such a title is shown in the
     * watch history.
     */
    val isAdult: Boolean = false,
    val watchedAt: Long = System.currentTimeMillis(),
)

/**
 * A download row. Mirrors the original's `data.local.entity.a` exactly — 21 columns,
 * a `Long` primary key and a lowercase `status` string.
 *
 * Status values used by the app: `pending`, `downloading`, `completed`, `failed`,
 * `paused`, `cancelled`.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val url: String,
    val fileName: String,
    val title: String,
    val contentType: String,
    val contentId: String,
    val posterPath: String? = null,
    val quality: String,
    val size: String,
    val status: String = STATUS_PENDING,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val filePath: String? = null,
    val headers: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val tmdbId: Int = 0,
    val season: Int = 0,
    val episode: Int = 0,
    val failureReason: String? = null,
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
        const val STATUS_PAUSED = "paused"
        const val STATUS_CANCELLED = "cancelled"
    }
}

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String? = null,
    val overview: String? = null,
    val rating: Double = 0.0,
    val releaseDate: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "continue_watching")
data class ContinueWatchingEntity(
    @PrimaryKey val key: String,
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String? = null,
    val episodeNumber: Int? = null,
    val seasonNumber: Int? = null,
    val progressMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * A single IPTV channel row.
 *
 * Mirrors the original's `com.app.mlounge.data.local.entity.d` (R8 name `d`)
 * exactly — table `iptv_channels`, an auto-generated `Long` primary key and the
 * six payload columns in the original's order (`name`, `url`, `logo`,
 * `groupName`, `provider`, `tvgId`).
 *
 * The original's DDL is:
 * ```
 * CREATE TABLE IF NOT EXISTS `iptv_channels` (
 *   `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
 *   `name` TEXT NOT NULL,
 *   `url` TEXT NOT NULL,
 *   `logo` TEXT,
 *   `groupName` TEXT,
 *   `provider` TEXT NOT NULL,
 *   `tvgId` TEXT
 * )
 * ```
 */
@Entity(
    tableName = "iptv_channels",
    indices = [
        Index(value = ["provider"]),
        Index(value = ["provider", "groupName"]),
        Index(value = ["name"]),
    ],
)
data class IptvChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val url: String,
    val logo: String? = null,
    val groupName: String? = null,
    val provider: String,
    val tvgId: String? = null,
)
