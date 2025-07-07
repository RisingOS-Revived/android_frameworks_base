/*
 * Copyright (C) 2025 The AxionAOSP Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.keyguard

import android.content.*
import android.database.ContentObserver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings
import android.view.*
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.KeyguardUpdateMonitorCallback
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.BiometricUnlockController
import com.android.systemui.statusbar.policy.KeyguardStateController

class MistouchPreventionWindowController constructor(
    private val context: Context,
    private val keyguardStateController: KeyguardStateController,
    private val keyguardUpdateMonitor: KeyguardUpdateMonitor,
    private val biometricUnlockController: BiometricUnlockController,
): MistouchInteractor.MistouchEvents {

    companion object {
        private const val TAG = "MistouchPreventionWindowController"
        private const val POCKET_SENSOR = Sensor.TYPE_PROXIMITY
        private const val DELAY_DISABLE_TP_DURATION = 200L
        private const val DISABLE_DURATION = 5000L
        private const val TALKBACK_SERVICE = "com.google.android.marvin.talkback.TalkBackService"
        private const val KEY_MISTOUCH_PREVENTION = "nt_mistouch_prevention_enable"
        private const val KEY_TALKBACK = "enabled_accessibility_services"

        private val URI_MISTOUCH_PREVENTION: Uri = Settings.Secure.getUriFor(KEY_MISTOUCH_PREVENTION)
        private val URI_TALKBACK: Uri = Settings.Secure.getUriFor(KEY_TALKBACK)
    }

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val proximitySensor = sensorManager?.getDefaultSensor(POCKET_SENSOR, true)
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val contentResolver = context.contentResolver

    private val preventionView: MistouchPreventionView = LayoutInflater.from(context)
        .inflate(R.layout.keyguard_mistouch_prevention_layout, null) as MistouchPreventionView

    private var registered = false
    private var windowAdded = false
    private var talkbackEnabled = false
    private var mistouchPreventionEnabled = false
    private var disableEventInMillis = 0L
    private var sensorNearWhenSleep = false
    private var keyguardVisible = false

    private val volumeKeyCallback = object : MistouchPreventionView.VolumeKeyCallback {
        override fun onVolumeUpPressed() {
            disable()
        }
    }

    private val contentObserver = object : ContentObserver(Handler()) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            when (uri) {
                URI_MISTOUCH_PREVENTION -> {
                    mistouchPreventionEnabled = Settings.Secure.getIntForUser(
                        contentResolver, KEY_MISTOUCH_PREVENTION, 1, UserHandle.USER_CURRENT
                    ) == 1
                }
                URI_TALKBACK -> {
                    val services = Settings.Secure.getStringForUser(contentResolver, KEY_TALKBACK, UserHandle.USER_CURRENT)
                    talkbackEnabled = services?.contains(TALKBACK_SERVICE) == true
                    if (talkbackEnabled) disable()
                }
            }
        }
    }

    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event ?: return
            if (event.sensor.type == POCKET_SENSOR) {
                val distance = event.values[0]
                if (distance == 0f) showWindow() else hideWindow()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val keyguardCallback = object : KeyguardUpdateMonitorCallback() {
        override fun onKeyguardVisibilityChanged(visible: Boolean) {
            keyguardVisible = visible
            val occluded = keyguardStateController.isOccluded ?: false

            if (visible && keyguardUpdateMonitor.isDeviceInteractive == true) {
                enable()
            } else if (!visible && !occluded) {
                disable()
            } else if (occluded && System.currentTimeMillis() - disableEventInMillis < DISABLE_DURATION) {
                disable()
            }
        }

        override fun onStartedWakingUp() {
            val isVisible = keyguardUpdateMonitor.isKeyguardVisible ?: false
            val isWakeAndUnlock = biometricUnlockController.isWakeAndUnlock() ?: false

            if (isVisible && !isWakeAndUnlock) {
                enable()
            }
            sensorNearWhenSleep = false
        }

        override fun onStartedGoingToSleep(why: Int) {
            sensorNearWhenSleep = windowAdded
            disable()
        }

        override fun onUserSwitching(userId: Int) {
            sensorNearWhenSleep = false
            disable()
        }

        override fun onUserSwitchComplete(userId: Int) {
            contentObserver.onChange(true, URI_MISTOUCH_PREVENTION)
            contentObserver.onChange(true, URI_TALKBACK)
            if (mistouchPreventionEnabled && !talkbackEnabled && keyguardUpdateMonitor.isKeyguardVisible == true) {
                enable()
            }
        }
    }

    fun init() {
        preventionView.setVisibility(View.INVISIBLE)
        preventionView.addCallback(volumeKeyCallback)
        registerListeners()
    }

    private fun registerListeners() {
        if (proximitySensor != null) {
            keyguardUpdateMonitor.registerCallback(keyguardCallback)
        }
        contentResolver.registerContentObserver(URI_MISTOUCH_PREVENTION, true, contentObserver, UserHandle.USER_ALL)
        contentResolver.registerContentObserver(URI_TALKBACK, true, contentObserver, UserHandle.USER_ALL)
        contentObserver.onChange(true, URI_MISTOUCH_PREVENTION)
        contentObserver.onChange(true, URI_TALKBACK)
    }

    private fun enable() {
        if (proximitySensor == null) return

        if (!mistouchPreventionEnabled || talkbackEnabled || registered) return

        MistouchInteractor.get().removeListener(this)
        sensorManager?.registerListener(proximityListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        registered = true
        addViewToWindow()
    }

    private fun disable() {
        if (proximitySensor == null) return

        removeViewFromWindow()

        if (registered) {
            sensorManager?.unregisterListener(proximityListener)
            MistouchInteractor.get().addListener(this)
            registered = false
        }
    }

    private fun addViewToWindow() {
        if (windowAdded) {
            return
        }

        preventionView.visibility = View.INVISIBLE

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            2026,
            524544,
            -2
        ).apply {
            privateFlags = privateFlags or 80
            privateFlags = privateFlags or 536870912
            layoutInDisplayCutoutMode = 3
            fitInsetsTypes = 0
            setTitle("MistouchPreve")
        }

        windowManager?.addView(preventionView, layoutParams)
        windowAdded = true
    }

    private fun removeViewFromWindow() {
        if (windowAdded) {
            windowManager?.removeView(preventionView)
            windowAdded = false
        }
    }

    private fun showWindow() {
        preventionView.addCallback(volumeKeyCallback)
        preventionView.visibility = View.VISIBLE
        preventionView.requestFocus()
    }

    private fun hideWindow() {
        preventionView.visibility = View.INVISIBLE
        preventionView.removeCallback(volumeKeyCallback)
    }

    override fun onDoubleTapPowerGesture() {
        if (keyguardVisible) {
            disableEventInMillis = System.currentTimeMillis()
        }
    }

    override fun onAffordanceLongClick() {
        if (keyguardVisible) {
            disableEventInMillis = System.currentTimeMillis()
        }
    }

    override fun onEmergencyButtonClick() {
        if (keyguardVisible) {
            disableEventInMillis = System.currentTimeMillis()
        }
    }
}
