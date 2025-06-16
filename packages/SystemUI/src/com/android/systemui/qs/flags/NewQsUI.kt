/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.systemui.qs.flags

import android.content.Context
import android.provider.Settings
import com.android.systemui.Flags
import com.android.systemui.flags.FlagToken
import com.android.systemui.flags.RefactorFlagUtils

/** Helper for reading or using the new QS UI flag state. */
@Suppress("NOTHING_TO_INLINE")
object NewQsUI {
    /** The aconfig flag name */
    const val FLAG_NAME = Flags.FLAG_QS_UI_REFACTOR
    
    /** Settings key for runtime control */
    const val SETTINGS_KEY = "qs_refactor_enabled"
    
    /** Default value when settings key is not set */
    private const val DEFAULT_ENABLED = 1
    
    /** A token used for dependency declaration */
    val token: FlagToken
        get() = FlagToken(FLAG_NAME, isEnabled)
    
    /** Is the refactor enabled */
    @JvmStatic
    val isEnabled: Boolean
        get() = Flags.qsUiRefactor() && getSettingsValue()
    
    /**
     * Get the current settings value for QS refactor
     */
    private fun getSettingsValue(): Boolean {
        return try {
            val context = getContext()
            val settingsValue = Settings.Secure.getInt(
                context.contentResolver,
                SETTINGS_KEY,
                DEFAULT_ENABLED
            )
            settingsValue == 1
        } catch (e: Exception) {
            // Fallback to default if context not available
            DEFAULT_ENABLED == 1
        }
    }
    
    /**
     * Enable or disable the QS UI refactor via Settings
     */
    @JvmStatic
    fun setEnabled(context: Context, enabled: Boolean) {
        Settings.Secure.putInt(
            context.contentResolver,
            SETTINGS_KEY,
            if (enabled) 1 else 0
        )
    }
    
    /**
     * Called to ensure code is only run when the flag is enabled. This protects users from the
     * unintended behaviors caused by accidentally running new logic, while also crashing on an eng
     * build to ensure that the refactor author catches issues in testing.
     */
    @JvmStatic
    inline fun isUnexpectedlyInLegacyMode() =
        RefactorFlagUtils.isUnexpectedlyInLegacyMode(isEnabled, FLAG_NAME)
    
    /**
     * Called to ensure code is only run when the flag is disabled. This will throw an exception if
     * the flag is enabled to ensure that the refactor author catches issues in testing.
     */
    @JvmStatic
    inline fun assertInLegacyMode() = RefactorFlagUtils.assertInLegacyMode(isEnabled, FLAG_NAME)
    
    /**
     * Get application context - this should be set by SystemUI initialization
     */
    private fun getContext(): Context {
        // In SystemUI, you can get context from ActivityThread or use a static reference
        // This is a simplified approach - you might need to adapt based on your setup
        return try {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as Context
        } catch (e: Exception) {
            throw IllegalStateException("Cannot get application context", e)
        }
    }
}
