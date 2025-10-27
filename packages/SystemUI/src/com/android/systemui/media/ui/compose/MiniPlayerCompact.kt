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

package com.android.systemui.media.ui.compose

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.media.ui.viewmodel.MiniPlayerViewModel
import kotlin.math.roundToInt

@Composable
fun MiniPlayerCompact(
    viewModel: MiniPlayerViewModel,
    compact: Boolean = true,
    expansionProgress: Float = if (compact) 0f else 1f,
                      modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mediaState by viewModel.mediaState.collectAsStateWithLifecycle()

    // Animations
    val animatedHeight by animateDpAsState(
        targetValue = if (compact) 70.dp else 95.dp,
                                           animationSpec = tween(durationMillis = 300),
                                           label = "player_height"
    )

    val alpha by animateFloatAsState(
        targetValue = when {
            compact && expansionProgress < 0.3f -> 1f - (expansionProgress / 0.3f)
            !compact && expansionProgress > 0.7f -> (expansionProgress - 0.7f) / 0.3f
            compact && expansionProgress >= 0.3f -> 0f
            !compact && expansionProgress <= 0.7f -> 0f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 200),
                                     label = "player_alpha"
    )

    val offsetY by animateFloatAsState(
        targetValue = when {
            compact -> expansionProgress * 100f // expand to down
            else -> 0f
        },
        animationSpec = tween(durationMillis = 300),
                                       label = "player_offset"
    )

    // background opacity
    val alphaDisabled = if (mediaState.hasActiveMedia) 1f else 0.55f
    val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alphaDisabled)
    val iconTint = MaterialTheme.colorScheme.onSurface.copy(alpha = alphaDisabled)

    Surface(
        modifier = modifier
        .fillMaxWidth()
        .height(animatedHeight)
        .graphicsLayer {
            this.alpha = alpha
            this.translationY = offsetY
        }
        .clickable {
            if (mediaState.hasActiveMedia && mediaState.packageName != null) {
                openMediaApp(context, mediaState.packageName!!)
            } else {
                launchDefaultPlayer(context)
            }
        },
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(14.dp))
            .padding(
                horizontal = 12.dp,
                vertical = if (compact) 12.dp else 10.dp
            ),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Title and artist
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mediaState.title,
                         color = textColor,
                         fontSize = if (compact) 14.sp else 16.sp,
                         maxLines = 1,
                         overflow = TextOverflow.Ellipsis
                    )
                    if (compact) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Text(
                        text = mediaState.artist,
                         color = textColor.copy(alpha = 0.85f),
                         fontSize = if (compact) 12.sp else 12.sp,
                         maxLines = 1,
                         overflow = TextOverflow.Ellipsis
                    )
                }

                // Media Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        enabled = mediaState.hasActiveMedia,
                        onClick = { viewModel.skipToPrevious() },
                               modifier = Modifier.size(if (compact) 40.dp else 44.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                             contentDescription = "Previous",
                             tint = iconTint,
                             modifier = Modifier.size(if (compact) 24.dp else 26.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (mediaState.hasActiveMedia) {
                                viewModel.playPause()
                            } else {
                                launchDefaultPlayer(context)
                            }
                        },
                        modifier = Modifier.size(if (compact) 40.dp else 44.dp)
                    ) {
                        Icon(
                            imageVector = if (mediaState.isPlaying) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                             contentDescription = "Play/Pause",
                             tint = iconTint,
                             modifier = Modifier.size(if (compact) 24.dp else 26.dp)
                        )
                    }

                    IconButton(
                        enabled = mediaState.hasActiveMedia,
                        onClick = { viewModel.skipToNext() },
                               modifier = Modifier.size(if (compact) 40.dp else 44.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                             contentDescription = "Next",
                             tint = iconTint,
                             modifier = Modifier.size(if (compact) 24.dp else 26.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun openMediaApp(context: Context, pkg: String) {
    runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

private fun launchDefaultPlayer(context: Context) {
    runCatching {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MUSIC)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
