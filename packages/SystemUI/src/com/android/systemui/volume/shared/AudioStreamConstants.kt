/*
 * Copyright (C) 2025 the RisingOS Revived Android Project
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
package com.android.systemui.volume.shared

import android.media.AudioManager

object AudioStreamConstants {
    const val STREAM_MUSIC = AudioManager.STREAM_MUSIC
    const val STREAM_RING = AudioManager.STREAM_RING
    const val STREAM_NOTIFICATION = AudioManager.STREAM_NOTIFICATION
    const val STREAM_ALARM = AudioManager.STREAM_ALARM
    const val STREAM_VOICE_CALL = AudioManager.STREAM_VOICE_CALL
}
