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

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.res.R
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaState(
    val title: String = "",
    val artist: String = "",
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

    private val _shouldShowPlayer = MutableStateFlow(true)
    val shouldShowPlayer: StateFlow<Boolean> = _shouldShowPlayer.asStateFlow()

    private var activeController: MediaController? = null
    private val contentResolver: ContentResolver = context.contentResolver

    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            updatePlayerVisibility()
        }
    }

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
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("qs_media_always_show"),
            false,
            settingsObserver
        )

        try {
            val controllers = mediaSessionManager.getActiveSessions(null)
            updateActiveController(controllers)
        } catch (e: SecurityException) {
            _mediaState.value = MediaState(
                title = context.getString(R.string.media_default_title),
                artist = context.getString(R.string.media_default_artist)
            )
        }

        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener, null)
        } catch (e: SecurityException) {
        }
    }

    private fun updatePlayerVisibility() {
       val alwaysShow = Settings.Secure.getInt(
       contentResolver,
       "qs_media_always_show",
       1
       ) == 1

        val hasMedia = _mediaState.value.hasActiveMedia
        _shouldShowPlayer.value = alwaysShow || hasMedia
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
                    ?: context.getString(R.string.media_unknown_track),
                artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
                    ?: context.getString(R.string.media_unknown_artist),
                isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING,
                hasActiveMedia = true,
                packageName = controller.packageName
            )
        } else {
            _mediaState.value = MediaState(
                title = context.getString(R.string.media_default_title),
                artist = context.getString(R.string.media_default_artist)
            )
        }
     updatePlayerVisibility()
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
        contentResolver.unregisterContentObserver(settingsObserver)
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
