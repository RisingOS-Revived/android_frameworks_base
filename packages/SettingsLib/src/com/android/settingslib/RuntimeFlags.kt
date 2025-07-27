package com.android.settingslib

import android.app.ActivityThread
import android.os.UserHandle
import android.provider.Settings

/**
 * Helper for reading custom runtime flags from Settings.System.
 */
object RuntimeFlags {

    private const val NEW_STATUS_BAR_ICONS = "new_status_bar_icons_enabled"

    @JvmStatic
    fun newStatusBarIcons(): Boolean {
        val context = ActivityThread.currentApplication() ?: return false
        val enabled = Settings.System.getIntForUser(
                context.contentResolver, NEW_STATUS_BAR_ICONS, 0, UserHandle.USER_CURRENT
        )
        return enabled == 1
    }
}
