/*
 * Copyright (C) 2025 the RisingOS Revived Android Project
 * Copyright (C) 2023-2024 the risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.notifications.ui

import android.app.INotificationManager
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ServiceManager
import android.os.UserHandle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.View
import java.util.concurrent.CopyOnWriteArraySet

class PeekDisplayViewController private constructor() {

    companion object {
        private const val TAG = "PeekDisplayViewController"
        private const val NOTIFICATION_REFRESH_INTERVAL = 2000L // 2 seconds
        
        @Volatile
        private var instance: PeekDisplayViewController? = null

        fun getInstance(): PeekDisplayViewController {
            return instance ?: synchronized(this) {
                instance ?: PeekDisplayViewController().also { instance = it }
            }
        }
    }

    private val peekDisplayViews: MutableSet<PeekDisplayView> = CopyOnWriteArraySet()
    private lateinit var mContext: Context
    private var notificationManager: INotificationManager? = null
    private var refreshHandler: Handler = Handler(Looper.getMainLooper())

    private val settingKeys = listOf(
        "peek_display_style",
        "peek_display_notifications",
        "peek_display_location",
        Settings.Secure.LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS
    )

    private var mCallbacksRegistered = false
    private var mDozing = false
    private var mPeekDisplayEnabled = false
    private var currentNotifications: List<StatusBarNotification> = emptyList()

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (mPeekDisplayEnabled && peekDisplayViews.isNotEmpty()) {
                fetchAndUpdateNotifications()
                refreshHandler.postDelayed(this, NOTIFICATION_REFRESH_INTERVAL)
            }
        }
    }

    private val settingsObserver: ContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            updatePeekDisplayState()
        }
    }

    fun addPeekDisplayView(view: PeekDisplayView) {
        if (peekDisplayViews.isEmpty()) {
            mContext = view.context.applicationContext
            initializeNotificationManager()
            registerCallbacks()
        }
        peekDisplayViews.add(view)
        Log.d(TAG, "Added peek display view, total views: ${peekDisplayViews.size}")
    }

    fun removePeekDisplayView(view: PeekDisplayView) {
        peekDisplayViews.remove(view)
        Log.d(TAG, "Removed peek display view, remaining views: ${peekDisplayViews.size}")
        if (peekDisplayViews.isEmpty()) {
            unregisterCallbacks()
            stopNotificationRefresh()
        }
    }

    private fun initializeNotificationManager() {
        try {
            val service = ServiceManager.getService("notification")
            notificationManager = INotificationManager.Stub.asInterface(service)
            Log.d(TAG, "Notification manager initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize notification manager", e)
        }
    }

    private fun fetchAndUpdateNotifications() {
        try {
            notificationManager?.let { nm ->
                val activeNotifications = nm.getActiveNotifications(mContext.packageName)
                val statusBarNotifications = activeNotifications?.toList() ?: emptyList()
                
                if (statusBarNotifications != currentNotifications) {
                    currentNotifications = statusBarNotifications
                    updateAllViews(statusBarNotifications)
                    Log.d(TAG, "Fetched ${statusBarNotifications.size} notifications")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch notifications", e)
            if (currentNotifications.isNotEmpty()) {
                currentNotifications = emptyList()
                updateAllViews(emptyList())
            }
        }
    }

    private fun updateAllViews(notifications: List<StatusBarNotification>) {
        peekDisplayViews.forEach { view ->
            try {
                view.updateNotificationShelf(notifications)
            } catch (e: Exception) {
                Log.w(TAG, "Error updating view with notifications", e)
            }
        }
    }

    private fun registerCallbacks() {
        if (mCallbacksRegistered) return
        
        try {
            settingKeys.map { Settings.Secure.getUriFor(it) }.forEach { uri ->
                mContext.contentResolver.registerContentObserver(uri, false, settingsObserver)
            }
            mCallbacksRegistered = true
            updatePeekDisplayState()
            Log.d(TAG, "Callbacks registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register callbacks", e)
        }
    }
    
    private fun updatePeekDisplayState() {
        try {
            val wasEnabled = mPeekDisplayEnabled
            mPeekDisplayEnabled = Settings.Secure.getIntForUser(mContext.contentResolver,
                "peek_display_notifications", 0, UserHandle.USER_CURRENT) == 1
            
            Log.d(TAG, "Peek display enabled: $mPeekDisplayEnabled")
            
            peekDisplayViews.forEach { view ->
                view.updatePeekDisplayState()
            }
            
            if (mPeekDisplayEnabled && !wasEnabled) {
                startNotificationRefresh()
            } else if (!mPeekDisplayEnabled && wasEnabled) {
                stopNotificationRefresh()
            }
            
            updateVisibility()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating peek display state", e)
        }
    }

    private fun startNotificationRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable)
        if (mPeekDisplayEnabled && peekDisplayViews.isNotEmpty()) {
            refreshHandler.post(refreshRunnable)
            Log.d(TAG, "Started notification refresh")
        }
    }

    private fun stopNotificationRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable)
        Log.d(TAG, "Stopped notification refresh")
    }

    private fun unregisterCallbacks() {
        if (!mCallbacksRegistered) return
        
        try {
            mContext.contentResolver.unregisterContentObserver(settingsObserver)
            mCallbacksRegistered = false
            Log.d(TAG, "Callbacks unregistered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister callbacks", e)
        }
    }

    fun removeCurrentNotification(sbn: StatusBarNotification) {
        try {
            notificationManager?.cancelNotificationWithTag(
                sbn.packageName,
                sbn.opPkg ?: sbn.packageName,
                sbn.tag,
                sbn.id,
                sbn.userId
            )
            Log.d(TAG, "Removed notification: ${sbn.key}")
            
            currentNotifications = currentNotifications.filter { it.key != sbn.key }
            updateAllViews(currentNotifications)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove notification", e)
            peekDisplayViews.forEach { view ->
                view.hideNotificationCard()
            }
        }
    }
    
    fun clearAllNotifications() {
        try {
            notificationManager?.cancelAllNotifications(mContext.packageName, UserHandle.myUserId())
            Log.d(TAG, "Cleared all notifications")
            
            currentNotifications = emptyList()
            updateAllViews(emptyList())
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all notifications", e)
            updateAllViews(emptyList())
        }
    }

    fun resetShelves() {
        peekDisplayViews.forEach { view ->
            view.hideNotificationCard()
            view.resetNotificationShelf()
        }
        Log.d(TAG, "Reset all shelves")
    }

    fun hidePeekDisplayView() {
        peekDisplayViews.forEach { view ->
            view.visibility = View.GONE
            view.hideNotificationCard()
            view.resetNotificationShelf()
        }
        stopNotificationRefresh()
        Log.d(TAG, "Hidden all peek display views")
    }

    fun showPeekDisplayView() {
        peekDisplayViews.forEach { view ->
            if (mPeekDisplayEnabled) {
                view.visibility = View.VISIBLE
            }
        }
        if (mPeekDisplayEnabled) {
            startNotificationRefresh()
        }
        Log.d(TAG, "Shown all peek display views")
    }

    fun setAlpha(alpha: Float) {
        peekDisplayViews.forEach { view ->
            view.post { view.alpha = alpha }
        }
    }
    
    fun updateVisibility() {
        peekDisplayViews.forEach { view ->
            view.visibility = if (mPeekDisplayEnabled) View.VISIBLE else View.GONE
        }
        
        if (mPeekDisplayEnabled) {
            startNotificationRefresh()
        } else {
            stopNotificationRefresh()
        }
    }

    fun setDozing(dozing: Boolean) {
        mDozing = dozing
        Log.d(TAG, "Dozing state changed: $dozing")
        updateVisibility()
    }

    fun isEnabled(): Boolean = mPeekDisplayEnabled
    
    fun getCurrentNotifications(): List<StatusBarNotification> = currentNotifications

    fun forceRefresh() {
        if (mPeekDisplayEnabled) {
            fetchAndUpdateNotifications()
        }
    }
}
