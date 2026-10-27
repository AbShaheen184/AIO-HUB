package com.movies.premium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.movies.premium.data.download.DownloadManager
import com.movies.premium.ui.navigation.CinemaHQApp
import com.movies.premium.ui.theme.CinemaHQTheme
import com.movies.premium.ui.tv.FocusHighlightOverlay
import com.movies.premium.ui.tv.FocusHighlightState
import com.movies.premium.ui.tv.LocalFocusHighlightState
import com.movies.premium.ui.tv.LocalIsTv
import com.movies.premium.ui.tv.hardwareFocusWatcher
import com.movies.premium.ui.tv.isTvDevice
import com.movies.premium.ui.tv.tvUiScale
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var downloadManager: DownloadManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash screen has to be installed *before* the activity is
        // created, otherwise the system draws its own plain splash over the
        // app's. The theme of the application is Theme.AIOHub.Splash; this
        // call is what hands over to Theme.AIOHub afterwards.
        installSplashScreen()

        super.onCreate(savedInstanceState)
        // The app draws behind the system bars and the bars are painted in
        // the background colour of the app, the way the original's theme
        // does it (`android:statusBarColor` = `@color/background_dark` in
        // `decoded/res/values/styles.xml:2649`).
        //
        // The bars are **not** hidden. A hidden bar reports no inset, so
        // every screen would have to guess how much room the bar takes —
        // and on a phone that shows the bar anyway the guess is wrong: the
        // hamburger of the collapsed sidebar ended up *under* the clock,
        // where no finger can reach it.
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val context = LocalContext.current
            // A television is watched from across the room, so its interface
            // is drawn bigger — see `ui/tv/TvSupport.kt`.
            val isTv = remember { isTvDevice(context) }
            val focusState = remember { FocusHighlightState() }

            CompositionLocalProvider(
                LocalIsTv provides isTv,
                LocalFocusHighlightState provides focusState,
            ) {
                val density = LocalDensity.current
                val configuration = LocalConfiguration.current
                val scale = if (isTv) {
                    tvUiScale(configuration.screenWidthDp)
                } else {
                    1f
                }
                val scaled = Density(
                    density = density.density * scale,
                    fontScale = density.fontScale,
                )

                CompositionLocalProvider(LocalDensity provides scaled) {
                    CinemaHQTheme {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .hardwareFocusWatcher(focusState),
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background,
                            ) {
                                CinemaHQApp(downloadManager = downloadManager)
                            }

                            // The focus ring is drawn over the interface, so
                            // it is the last thing in the box. It only shows
                            // once a remote control or a keyboard has been
                            // used — a phone is driven with a finger and
                            // needs no ring.
                            FocusHighlightOverlay()
                        }
                    }
                }
            }
        }
    }
}
