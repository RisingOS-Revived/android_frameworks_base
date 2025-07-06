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

import com.android.systemui.util.WeakListenerManager

class MistouchInteractor private constructor() {

    interface MistouchEvents {
        fun onAffordanceLongClick() {}
        fun onDoubleTapPowerGesture() {}
        fun onEmergencyButtonClick() {}
    }

    private val listenerManager = WeakListenerManager<MistouchEvents>()

    fun addListener(listener: MistouchEvents) = listenerManager.addListener(listener)
    fun removeListener(listener: MistouchEvents) = listenerManager.removeListener(listener)

    fun handleEmergencyButtonClick() {
        listenerManager.notify { it.onEmergencyButtonClick() }
    }

    fun handleDoubleTapPowerGesture() {
        listenerManager.notify { it.onDoubleTapPowerGesture() }
    }

    fun handleAffordanceLongClick() {
        listenerManager.notify { it.onAffordanceLongClick() }
    }

    companion object {
        @Volatile
        private var instance: MistouchInteractor? = null

        @JvmStatic
        fun get(): MistouchInteractor {
            return instance ?: synchronized(this) {
                instance ?: MistouchInteractor().also { instance = it }
            }
        }
    }
}
