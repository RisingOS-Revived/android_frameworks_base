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
package com.android.systemui.volume.ui.viewmodel

import android.content.Context
import android.media.AudioManager
import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import com.android.systemui.classifier.Classifier
import com.android.systemui.classifier.domain.interactor.FalsingInteractor
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.shared.model.asIcon
import com.android.systemui.graphics.ImageLoader
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.res.R
import com.android.systemui.volume.domain.interactor.VolumeInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class VolumeSliderViewModel
@AssistedInject
constructor(
    private val volumeInteractor: VolumeInteractor,
    val hapticsViewModelFactory: SliderHapticsViewModel.Factory,
    private val falsingInteractor: FalsingInteractor,
    private val imageLoader: ImageLoader,
    @Assisted private val streamType: Int = AudioManager.STREAM_MUSIC,
) : ExclusiveActivatable() {

    private val hydrator = Hydrator("VolumeSliderViewModel.hydrator")

    val currentVolume by hydrator.hydratedStateOf(
        "currentVolume",
        0,
        volumeInteractor.getVolumeFlow(streamType)
    )

    val currentRingerMode by hydrator.hydratedStateOf(
        "currentRingerMode",
        AudioManager.RINGER_MODE_NORMAL,
        volumeInteractor.getRingerModeFlow()
    )

    val maxVolume = volumeInteractor.getMaxVolume(streamType)
    val minVolume = 0

    fun emitVolumeTouchForFalsing() {
        falsingInteractor.isFalseTouch(Classifier.VOLUME_SLIDER)
    }

    suspend fun loadImage(@DrawableRes resId: Int, context: Context): Icon.Loaded {
        return imageLoader.loadDrawable(
            android.graphics.drawable.Icon.createWithResource(context, resId),
            maxHeight = 200,
            maxWidth = 200
        )!!.asIcon(null, resId)
    }

    suspend fun onDrag(drag: VolumeDrag) {
        when (drag) {
            is VolumeDrag.Dragging -> volumeInteractor.setTemporaryVolume(streamType, drag.volume)
            is VolumeDrag.Stopped -> volumeInteractor.setVolume(streamType, drag.volume)
        }
    }

    fun onIconClick() {
        volumeInteractor.toggleMute(streamType)
    }

    fun onRingerToggle() {
        volumeInteractor.toggleRingerMode()
    }

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    @AssistedFactory
    interface Factory {
        fun create(streamType: Int = AudioManager.STREAM_MUSIC): VolumeSliderViewModel
    }

    companion object {
        private val icons = VolumeIcons(
            volumeOff = R.drawable.ic_volume_off,
            volumeLow = R.drawable.ic_volume_down,
            volumeHigh = R.drawable.ic_volume_up
        )

        @DrawableRes
        fun getIconForPercentage(percentage: Float, isMuted: Boolean): Int {
            return when {
                isMuted || percentage == 0f -> icons.volumeOff
                percentage <= 50f -> icons.volumeLow
                else -> icons.volumeHigh
            }
        }
    }
}

sealed interface VolumeDrag {
    val volume: Int
    @JvmInline value class Dragging(override val volume: Int) : VolumeDrag
    @JvmInline value class Stopped(override val volume: Int) : VolumeDrag
}

private data class VolumeIcons(
    @DrawableRes val volumeOff: Int,
    @DrawableRes val volumeLow: Int,
    @DrawableRes val volumeHigh: Int,
)
