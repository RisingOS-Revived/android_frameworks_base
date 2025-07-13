/*
 * Copyright (C) 2025 AxionOS Project
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
package com.android.systemui.navigationbar.views.buttons

import android.content.Context
import android.content.res.Resources
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import com.android.systemui.res.R

class KeyButtonViewEx private constructor() {

    companion object {
        private const val MISTOUCH_PREVENTION_RESET_DURATION = 2000L
        private const val SETTING_MISTOUCH_PREVENTION = "nt_game_mode_mistouch_prevention"

        @Volatile
        private var instance: KeyButtonViewEx? = null

        fun get(): KeyButtonViewEx {
            return instance ?: synchronized(this) {
                instance ?: KeyButtonViewEx().also { instance = it }
            }
        }
    }

    private var mBackInterceptTime: Long = 0
    private var mHomeInterceptTime: Long = 0
    private var mToast: Toast? = null

    fun shouldInterceptBackKey(context: Context): Boolean {
        val currentTime = SystemClock.uptimeMillis()
        if (isMistouchPreventionEnabled(context) && currentTime - mBackInterceptTime >= MISTOUCH_PREVENTION_RESET_DURATION) {
            mBackInterceptTime = currentTime
            showToast(context)
            return true
        }
        hideToast()
        return false
    }

    fun shouldInterceptHomeKey(context: Context): Boolean {
        val currentTime = SystemClock.uptimeMillis()
        if (isMistouchPreventionEnabled(context) && currentTime - mHomeInterceptTime >= MISTOUCH_PREVENTION_RESET_DURATION) {
            mHomeInterceptTime = currentTime
            showToast(context)
            return true
        }
        hideToast()
        return false
    }

    private fun isMistouchPreventionEnabled(context: Context): Boolean {
        return Settings.Secure.getInt(
            context.contentResolver,
            SETTING_MISTOUCH_PREVENTION,
            0
        ) == 1
    }

    private fun showToast(context: Context) {
        mToast?.cancel()
        mToast = Toast.makeText(context, R.string.click_again, Toast.LENGTH_SHORT).apply {
            show()
        }
    }

    private fun hideToast() {
        mToast?.cancel()
        mToast = null
    }
}
