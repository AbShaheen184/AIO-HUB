package com.movies.premium.ui.screens

/**
 * The three library screens used to live here, side by side.
 *
 * They now live in the packages that mirror the original's layout:
 *
 * | Screen | Replica | Original |
 * |---|---|---|
 * | Downloads | `com.movies.premium.ui.screens.downloads.DownloadsScreen` | `com.app.mlounge.ui.screens.downloads.a` |
 * | Watch history | `com.movies.premium.ui.screens.history.HistoryScreen` | `com.app.mlounge.ui.screens.history.a` |
 * | Watchlist | `com.movies.premium.ui.screens.watchlist.WatchlistScreen` | `com.app.mlounge.ui.screens.watchlist.*` |
 *
 * This file is kept so the old import path does not break anything that still
 * references it; it deliberately declares no composables.
 */
