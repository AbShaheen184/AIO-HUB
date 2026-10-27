package com.movies.premium

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.movies.premium.util.Logger

/**
 * Re-arms the app's scheduled work after a reboot.
 *
 * The original's boot receiver also restarted the Appsalt and Hopmn money
 * SDKs; those are deliberately absent from this build.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Logger.i(TAG, "Received boot broadcast: ${intent.action}")
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // Nothing to restore: the app holds no persistent background work.
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
