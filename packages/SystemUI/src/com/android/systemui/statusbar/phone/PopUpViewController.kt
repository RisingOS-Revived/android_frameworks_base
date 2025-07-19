/*
 * Copyright (C) 2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.phone

import android.content.Context
import android.content.res.Configuration
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.UserHandle

import com.android.internal.util.android.PopUpSettingsHelper

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.util.settings.SystemSettings

import java.util.concurrent.Executor

import javax.inject.Inject

import android.provider.Settings.System.POP_UP_NOTIFICATION_JUMP_PORTRAIT
import android.provider.Settings.System.POP_UP_NOTIFICATION_JUMP_LANDSCAPE

@SysUISingleton
class PopUpViewController @Inject constructor(
    private val context: Context,
    @Main private val mainExecutor: Executor,
    @Main mainHandler: Handler,
    private val configurationController: ConfigurationController,
    private val systemSettings: SystemSettings,
    private val userTracker: UserTracker
) {

    private var notificationJumpPortrait = false
    private var notificationJumpLandscape = false

    private var isLandscape: Boolean

    init {
        val settingsObserver = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                when (uri?.lastPathSegment) {
                    POP_UP_NOTIFICATION_JUMP_PORTRAIT -> updateNotificationJumpPort()
                    POP_UP_NOTIFICATION_JUMP_LANDSCAPE -> updateNotificationJumpLand()
                }
            }
        }
        systemSettings.registerContentObserverForUserSync(
                POP_UP_NOTIFICATION_JUMP_PORTRAIT,
                settingsObserver, UserHandle.USER_ALL)
        systemSettings.registerContentObserverForUserSync(
                POP_UP_NOTIFICATION_JUMP_LANDSCAPE,
                settingsObserver, UserHandle.USER_ALL)
        updateSettings()

        userTracker.addCallback(object : UserTracker.Callback {
            override fun onUserChanged(newUser: Int, userContext: Context) {
                updateSettings()
            }
        }, mainExecutor)

        isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        configurationController.addCallback(object : ConfigurationController.ConfigurationListener {
            override fun onConfigChanged(newConfig: Configuration?) {
                isLandscape = newConfig?.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
        })
    }

    private fun updateSettings() {
        updateNotificationJumpPort()
        updateNotificationJumpLand()
    }

    private fun updateNotificationJumpPort() {
        notificationJumpPortrait = PopUpSettingsHelper.isNotificationJumpEnabled(
                context, false, userTracker.userId)
    }

    private fun updateNotificationJumpLand() {
        notificationJumpLandscape = PopUpSettingsHelper.isNotificationJumpEnabled(
                context, true, userTracker.userId)
    }

    fun shouldJumpNotificationWithPopUp(): Boolean {
        return if (isLandscape) notificationJumpLandscape else notificationJumpPortrait
    }
}
