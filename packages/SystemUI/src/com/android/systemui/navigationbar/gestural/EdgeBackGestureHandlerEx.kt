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
package com.android.systemui.navigationbar.gestural

import android.content.Context
import android.content.res.Resources
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import com.android.systemui.res.R
import java.util.concurrent.Executor

class EdgeBackGestureHandlerEx private constructor() {

    companion object {
        private const val RESET_DURATION_MS = 2000L
        private const val MISTOUCH_PREVENTION = "nt_game_mode_mistouch_prevention"

        @Volatile
        private var instance: EdgeBackGestureHandlerEx? = null

        fun get(): EdgeBackGestureHandlerEx {
            return instance ?: synchronized(this) {
                instance ?: EdgeBackGestureHandlerEx().also { instance = it }
            }
        }
    }

    private var lastBackGestureTime: Long = 0L
    private var shouldInterceptBack: Boolean = false
    private var currentToast: Toast? = null

    fun resetBackIntercept() {
        shouldInterceptBack = false
    }

    fun getIfNeedInterceptBack(): Boolean = shouldInterceptBack

    fun setBackInterceptTime() {
        lastBackGestureTime = SystemClock.uptimeMillis()
    }

    fun getBackIntercepTime(): Long = lastBackGestureTime

    fun shouldInterceptBack(context: Context) {
        val elapsed = SystemClock.uptimeMillis() - lastBackGestureTime
        val mistouchPreventionEnabled = Settings.Secure.getInt(
            context.contentResolver,
            MISTOUCH_PREVENTION,
            0
        ) == 1

        shouldInterceptBack = mistouchPreventionEnabled && elapsed >= RESET_DURATION_MS
    }

    fun showToast(executor: Executor, context: Context) {
        executor.execute {
            try {
                currentToast = Toast.makeText(context, R.string.swipe_again, Toast.LENGTH_SHORT).apply {
                    show()
                }
            } catch (_: Resources.NotFoundException) {}
        }
    }

    fun hideToast(executor: Executor) {
        executor.execute {
            currentToast?.cancel()
            currentToast = null
        }
    }
}
