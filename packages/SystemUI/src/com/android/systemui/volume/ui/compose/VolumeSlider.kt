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
package com.android.systemui.volume.ui.compose

import android.content.Context
import android.os.UserHandle
import android.provider.Settings
import android.view.MotionEvent
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.compose.modifiers.padding
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.systemui.Flags
import com.android.systemui.biometrics.Utils.toBitmap
import com.android.systemui.brightness.ui.compose.AnimationSpecs
import com.android.systemui.brightness.ui.compose.Dimensions
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.haptics.slider.SeekableSliderTrackerConfig
import com.android.systemui.haptics.slider.SliderHapticFeedbackConfig
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.ui.compose.borderOnFocus
import com.android.systemui.volume.ui.viewmodel.VolumeDrag
import com.android.systemui.volume.ui.viewmodel.VolumeSliderViewModel
import platform.test.motion.compose.values.MotionTestValueKey
import platform.test.motion.compose.values.motionTestValues

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
@VisibleForTesting
fun VolumeSlider(
    volumeValue: Int,
    valueRange: IntRange,
    isMuted: Boolean,
    iconResProvider: (Float, Boolean) -> Int,
    imageLoader: suspend (Int, Context) -> com.android.systemui.common.shared.model.Icon.Loaded,
    onDrag: (Int) -> Unit,
    onStop: (Int) -> Unit,
    onIconClick: suspend () -> Unit,
    modifier: Modifier = Modifier,
    hapticsViewModelFactory: SliderHapticsViewModel.Factory,
) {
    val context = LocalContext.current
    val shapeMode = rememberSliderShapeMode()
    val trackCornerDp: Dp = when (shapeMode) {
        1 -> 24.dp
        2 -> 12.dp
        3 -> 0.dp
        else -> Dimensions.SliderTrackRoundedCorner
    }

    var value by remember(volumeValue) { mutableIntStateOf(volumeValue) }
    
    LaunchedEffect(volumeValue) {
        if (!isMuted && volumeValue != value) {
            value = volumeValue
        }
    }
    
    val animatedValue by animateFloatAsState(targetValue = value.toFloat(), label = "VolumeSliderAnimatedValue")
    val floatValueRange = valueRange.first.toFloat()..valueRange.last.toFloat()
    val interactionSource = remember { MutableInteractionSource() }
    val hapticsViewModel: SliderHapticsViewModel? = if (Flags.hapticsForComposeSliders()) {
        rememberViewModel(traceName = "VolumeSliderHapticsViewModel") {
            hapticsViewModelFactory.create(
                interactionSource,
                floatValueRange,
                Orientation.Horizontal,
                SliderHapticFeedbackConfig(maxVelocityToScale = 1f),
                SeekableSliderTrackerConfig(),
            )
        }
    } else null

    val colors = volumeColors()
    val iconRes by remember(value, valueRange, isMuted) {
        derivedStateOf {
            val percentage = (value - valueRange.first) * 100f / (valueRange.last - valueRange.first)
            iconResProvider(percentage, isMuted)
        }
    }

    val painter: Painter by produceState<Painter>(
        initialValue = ColorPainter(Color.Transparent),
        key1 = iconRes,
        key2 = context,
    ) {
        val icon = imageLoader(iconRes, context)
        val bitmap = icon.drawable.toBitmap()!!.asImageBitmap()
        this@produceState.value = BitmapPainter(bitmap)
    }

    val activeIconColor = colors.activeTickColor
    val inactiveIconColor = colors.inactiveTickColor
    val trackIcon: DrawScope.(Offset, Color, Float) -> Unit = remember {
        { offset, color, alpha ->
            translate(offset.x + Dimensions.IconPadding.toPx(), offset.y) {
                with(painter) {
                    draw(Dimensions.IconSize.toSize(), colorFilter = ColorFilter.tint(color), alpha = alpha)
                }
            }
        }
    }

    Row(modifier = modifier) {
        Slider(
            value = animatedValue,
            valueRange = floatValueRange,
            colors = colors,
            onValueChange = {
                hapticsViewModel?.onValueChange(it)
                value = it.toInt()
                onDrag(value)
            },
            onValueChangeFinished = {
                hapticsViewModel?.onValueChangeEnded()
                onStop(value)
            },
            modifier = modifier
                .weight(1f)
                .sysuiResTag("volume_slider"),
            interactionSource = interactionSource,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    thumbSize = DpSize(4.dp, 52.dp),
                    colors = colors,
                )
            },
            track = { sliderState ->
                var showIconActive by remember { mutableStateOf(true) }
                val iconActiveAlphaAnimatable = remember {
                    Animatable(initialValue = 1f, typeConverter = Float.VectorConverter)
                }
                val iconInactiveAlphaAnimatable = remember {
                    Animatable(initialValue = 0f, typeConverter = Float.VectorConverter)
                }

                LaunchedEffect(iconActiveAlphaAnimatable, iconInactiveAlphaAnimatable, showIconActive) {
                    if (showIconActive) {
                        launch { iconActiveAlphaAnimatable.appear() }
                        launch { iconInactiveAlphaAnimatable.disappear() }
                    } else {
                        launch { iconActiveAlphaAnimatable.disappear() }
                        launch { iconInactiveAlphaAnimatable.appear() }
                    }
                }

                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier
                        .motionTestValues {
                            (iconActiveAlphaAnimatable.isRunning || iconInactiveAlphaAnimatable.isRunning) exportAs VolumeSliderMotionTestKeys.AnimatingIcon
                            iconActiveAlphaAnimatable.value exportAs VolumeSliderMotionTestKeys.ActiveIconAlpha
                            iconInactiveAlphaAnimatable.value exportAs VolumeSliderMotionTestKeys.InactiveIconAlpha
                        }
                        .height(40.dp)
                        .drawWithContent {
                            drawContent()
                            val yOffset = size.height / 2 - Dimensions.IconSize.toSize().height / 2
                            val activeTrackStart = 0f
                            val activeTrackEnd = size.width * sliderState.coercedValueAsFraction - Dimensions.ThumbTrackGapSize.toPx()
                            val inactiveTrackStart = activeTrackEnd + Dimensions.ThumbTrackGapSize.toPx() * 2
                            val inactiveTrackEnd = size.width
                            val activeTrackWidth = activeTrackEnd - activeTrackStart
                            val inactiveTrackWidth = inactiveTrackEnd - inactiveTrackStart

                            if (Dimensions.IconSize.toSize().width < activeTrackWidth - Dimensions.IconPadding.toPx() * 2) {
                                showIconActive = true
                                trackIcon(Offset(activeTrackStart, yOffset), activeIconColor, iconActiveAlphaAnimatable.value)
                            } else if (Dimensions.IconSize.toSize().width < inactiveTrackWidth - Dimensions.IconPadding.toPx() * 2) {
                                showIconActive = false
                                trackIcon(Offset(inactiveTrackStart, yOffset), inactiveIconColor, iconInactiveAlphaAnimatable.value)
                            }
                        },
                    trackCornerSize = trackCornerDp,
                    trackInsideCornerSize = 2.dp,
                    drawStopIndicator = null,
                    thumbTrackGapSize = Dimensions.ThumbTrackGapSize,
                    colors = colors,
                )
            }
        )
    }
}

@Composable
private fun rememberSliderShapeMode(): Int {
    val context = LocalContext.current
    return remember {
        val cr = context.contentResolver
        try {
            Settings.System.getIntForUser(cr, Settings.System.QS_BRIGHTNESS_SLIDER_SHAPE, 0, UserHandle.USER_CURRENT)
        } catch (_: Throwable) { 0 }
    }
}

@Composable
fun VolumeSliderContainer(
    viewModel: VolumeSliderViewModel,
    modifier: Modifier = Modifier,
    containerColors: com.android.systemui.brightness.ui.compose.ContainerColors,
) {
    val volume = viewModel.currentVolume
    val isMuted = volume == 0
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }

    val shapeMode = rememberSliderShapeMode()
    val trackCornerDp: Dp = when (shapeMode) {
        1 -> 24.dp
        2 -> 12.dp
        3 -> 0.dp
        else -> Dimensions.SliderTrackRoundedCorner
    }
    val bgCornerDp: Dp = when (shapeMode) {
        1 -> 50.dp
        2 -> 24.dp
        3 -> 0.dp
        else -> Dimensions.SliderBackgroundRoundedCorner
    }

    val containerColor by animateColorAsState(
        if (dragging) containerColors.mirrorColor else containerColors.idleColor
    )

    Box(
        modifier = modifier
            .padding(vertical = { Dimensions.SliderBackgroundFrameSize.height.roundToPx() })
            .fillMaxWidth()
            .sysuiResTag("volume_slider")
    ) {
        VolumeSlider(
            volumeValue = volume,
            valueRange = viewModel.minVolume..viewModel.maxVolume,
            isMuted = isMuted,
            iconResProvider = VolumeSliderViewModel::getIconForPercentage,
            imageLoader = viewModel::loadImage,
            onDrag = {
                dragging = true
                coroutineScope.launch { viewModel.onDrag(VolumeDrag.Dragging(it)) }
            },
            onStop = {
                dragging = false
                coroutineScope.launch { viewModel.onDrag(VolumeDrag.Stopped(it)) }
            },
            onIconClick = { viewModel.onIconClick() },
            modifier = Modifier
                .borderOnFocus(
                    color = MaterialTheme.colorScheme.secondary,
                    cornerSize = CornerSize(trackCornerDp),
                )
                .sliderBackground(containerColor, bgCornerDp)
                .fillMaxWidth()
                .pointerInteropFilter {
                    if (it.actionMasked == MotionEvent.ACTION_UP || it.actionMasked == MotionEvent.ACTION_CANCEL) {
                        viewModel.emitVolumeTouchForFalsing()
                    }
                    false
                },
            hapticsViewModelFactory = viewModel.hapticsViewModelFactory,
        )
    }
}

private fun Modifier.sliderBackground(color: Color, corner: Dp) = drawWithCache {
    val offsetAround = Dimensions.SliderBackgroundFrameSize.toSize()
    val newSize = Size(size.width + 2 * offsetAround.width, size.height + 2 * offsetAround.height)
    val offset = Offset(-offsetAround.width, -offsetAround.height)
    val cornerRadius = CornerRadius(corner.toPx())
    onDrawBehind {
        drawRoundRect(color = color, topLeft = offset, size = newSize, cornerRadius = cornerRadius)
    }
}

@VisibleForTesting
object VolumeSliderMotionTestKeys {
    val AnimatingIcon = MotionTestValueKey<Boolean>("animatingIcon")
    val ActiveIconAlpha = MotionTestValueKey<Float>("activeIconAlpha")
    val InactiveIconAlpha = MotionTestValueKey<Float>("inactiveIconAlpha")
}

@Composable
private fun volumeColors(): SliderColors {
    return SliderDefaults.colors().copy(
        inactiveTrackColor = LocalAndroidColorScheme.current.surfaceEffect2,
        activeTickColor = MaterialTheme.colorScheme.onPrimary,
        inactiveTickColor = MaterialTheme.colorScheme.onSurface,
    )
}

private suspend fun Animatable<Float, *>.appear() = 
    animateTo(targetValue = 1f, animationSpec = AnimationSpecs.IconAppearSpec)

private suspend fun Animatable<Float, *>.disappear() = 
    animateTo(targetValue = 0f, animationSpec = AnimationSpecs.IconDisappearSpec)
