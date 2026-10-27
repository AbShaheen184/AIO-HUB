package com.movies.premium.data.anime

import com.movies.premium.data.remote.model.HiAnimeTrack

/**
 * The subtitle tracks of the anime episode that is about to be played.
 *
 * The original keeps them in a process-wide singleton
 * (`com.app.mlounge.data.anime.AnimeSubtitleStore`): the sources screen stores
 * the tracks of the picked episode and the player takes them out again. The
 * replica does the same, because the player route only carries the url and
 * the headers.
 */
object AnimeSubtitleStore {

    @Volatile
    private var tracks: List<HiAnimeTrack> = emptyList()

    /** Stores the tracks of the episode that is about to be played. */
    fun set(newTracks: List<HiAnimeTrack>) {
        tracks = newTracks
    }

    /** Takes the stored tracks out and forgets them. */
    fun consume(): List<HiAnimeTrack> {
        val current = tracks
        tracks = emptyList()
        return current
    }
}
