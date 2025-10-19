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
package com.android.systemui.volume.domain.interactor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

@SysUISingleton
class VolumeInteractor @Inject constructor(
    @Application private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    fun getVolumeFlow(streamType: Int): Flow<Int> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val stream = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                    if (stream == streamType || stream == -1) {
                        trySend(audioManager.getStreamVolume(streamType))
                    }
                }
            }
        }
        
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        context.registerReceiver(receiver, filter)
        
        trySend(audioManager.getStreamVolume(streamType))
        
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    fun getRingerModeFlow(): Flow<Int> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.RINGER_MODE_CHANGED_ACTION) {
                    trySend(audioManager.ringerMode)
                }
            }
        }
        
        val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
        context.registerReceiver(receiver, filter)
        
        trySend(audioManager.ringerMode)
        
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    fun getMaxVolume(streamType: Int): Int = audioManager.getStreamMaxVolume(streamType)

    fun setTemporaryVolume(streamType: Int, volume: Int) {
        audioManager.setStreamVolume(streamType, volume, 0)
    }

    fun setVolume(streamType: Int, volume: Int) {
        audioManager.setStreamVolume(streamType, volume, 0)
    }

    fun toggleMute(streamType: Int) {
        val currentVolume = audioManager.getStreamVolume(streamType)
        if (currentVolume == 0) {
            val defaultVolume = (getMaxVolume(streamType) * 0.5f).toInt()
            audioManager.setStreamVolume(streamType, defaultVolume, 0)
        } else {
            audioManager.setStreamVolume(streamType, 0, 0)
        }
    }

    fun getRingerMode(): Int = audioManager.ringerMode

    fun setRingerMode(mode: Int) {
        audioManager.ringerMode = mode
    }

    fun toggleRingerMode() {
        val currentMode = audioManager.ringerMode
        val nextMode = when (currentMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            AudioManager.RINGER_MODE_SILENT -> AudioManager.RINGER_MODE_NORMAL
            else -> AudioManager.RINGER_MODE_NORMAL
        }

        try {
            val method = audioManager.javaClass.getMethod("setRingerModeInternal", Int::class.javaPrimitiveType)
            method.invoke(audioManager, nextMode)
        } catch (e: Exception) {
            audioManager.ringerMode = nextMode
        }
    }
}
