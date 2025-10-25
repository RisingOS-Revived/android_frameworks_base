/*
 * Copyright (C) 2025 Rising-Revived OSS
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

package com.android.systemui.media.ui.viewmodel

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.systemui.dagger.SysUISingleton
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaState(
    val title: String = "Open player",
    val artist: String = "No active media",
    val isPlaying: Boolean = false,
    val hasActiveMedia: Boolean = false,
    val packageName: String? = null
)

class MiniPlayerViewModel @AssistedInject constructor(
    private val context: Context,
    private val mediaSessionManager: MediaSessionManager,
) : ViewModel() {

    private val _mediaState = MutableStateFlow(MediaState())
    val mediaState: StateFlow<MediaState> = _mediaState.asStateFlow()

    private var activeController: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateMediaState()
        }

        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            updateMediaState()
        }
    }

    private val sessionListener = object : MediaSessionManager.OnActiveSessionsChangedListener {
        override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
            updateActiveController(controllers)
        }
    }

    init {
        try {
            val controllers = mediaSessionManager.getActiveSessions(null)
            updateActiveController(controllers)
        } catch (e: SecurityException) {
            _mediaState.value = MediaState()
        }

        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener, null)
        } catch (e: SecurityException) {
        }
    }

    private fun updateActiveController(controllers: MutableList<MediaController>?) {
        activeController?.unregisterCallback(controllerCallback)

        activeController = controllers?.firstOrNull()

        activeController?.registerCallback(controllerCallback)

        updateMediaState()
    }

    private fun updateMediaState() {
        val controller = activeController
        if (controller != null) {
            val metadata = controller.metadata
            val playbackState = controller.playbackState

            _mediaState.value = MediaState(
                title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                    ?: "Unknown Track",
                artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
                    ?: "Unknown Artist",
                isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING,
                hasActiveMedia = true,
                packageName = controller.packageName
            )
        } else {
            _mediaState.value = MediaState()
        }
    }

    fun playPause() {
        val controller = activeController ?: return
        val playbackState = controller.playbackState?.state

        when (playbackState) {
            PlaybackState.STATE_PLAYING -> controller.transportControls.pause()
            PlaybackState.STATE_PAUSED -> controller.transportControls.play()
            else -> controller.transportControls.play()
        }
    }

    fun skipToNext() {
        activeController?.transportControls?.skipToNext()
    }

    fun skipToPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }

    override fun onCleared() {
        super.onCleared()
        activeController?.unregisterCallback(controllerCallback)
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (e: Exception) {
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): MiniPlayerViewModel
    }
}
