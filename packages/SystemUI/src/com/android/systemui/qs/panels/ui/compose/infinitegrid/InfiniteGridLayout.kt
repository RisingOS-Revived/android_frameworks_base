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

package com.android.systemui.qs.panels.ui.compose.infinitegrid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.grid.ui.compose.CustomVerticalSpannedGrid
import com.android.systemui.haptics.msdl.qs.TileHapticsViewModelFactoryProvider
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.controls.ui.controller.MediaHierarchyManager.Companion.LOCATION_QS
import com.android.systemui.qs.panels.shared.model.SizedTileImpl
import com.android.systemui.qs.panels.ui.compose.PaginatableGridLayout
import com.android.systemui.qs.panels.ui.compose.TileListener
import com.android.systemui.qs.panels.ui.compose.bounceableInfo
import com.android.systemui.qs.panels.ui.compose.rememberEditListState
import com.android.systemui.qs.panels.ui.viewmodel.BounceableTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.IconTilesViewModel
import com.android.systemui.qs.panels.ui.viewmodel.InfiniteGridViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.ui.ElementKeys.toElementKey
import com.android.systemui.res.R
import javax.inject.Inject

@SysUISingleton
class InfiniteGridLayout
@Inject
constructor(
    private val detailsViewModel: DetailsViewModel,
    private val iconTilesViewModel: IconTilesViewModel,
    private val viewModelFactory: InfiniteGridViewModel.Factory,
    private val tileHapticsViewModelFactoryProvider: TileHapticsViewModelFactoryProvider,
) : PaginatableGridLayout {

    @Composable
    override fun ContentScope.TileGrid(
        tiles: List<TileViewModel>,
        modifier: Modifier,
        listening: () -> Boolean,
    ) {
        val viewModel =
            rememberViewModel(traceName = "InfiniteGridLayout.TileGrid") {
                viewModelFactory.create()
            }
        val iconTilesViewModel =
            rememberViewModel(traceName = "InfiniteGridLayout.TileGrid") {
                viewModel.dynamicIconTilesViewModelFactory.create()
            }
        val columnsWithMediaViewModel =
            rememberViewModel(traceName = "InfiniteGridLAyout.TileGrid") {
                viewModel.columnsWithMediaViewModelFactory.create(LOCATION_QS)
            }

        val columns = columnsWithMediaViewModel.columns
        val largeTiles by iconTilesViewModel.largeTilesState
        val largeTilesSpan by iconTilesViewModel.largeTilesSpanState

        val firstRowTileCount = columns
        
        var isExpanded by remember { mutableStateOf(false) }

        // Tiles or largeTiles may be updated while this is composed, so listen to any changes
        val sizedTiles =
            remember(tiles, largeTiles, largeTilesSpan, columns) {
                tiles.mapIndexed { index, tile ->
                    val isFirstRow = index < firstRowTileCount
                    val width = if (isFirstRow) {
                        1
                    } else if (largeTiles.contains(tile.spec)) {
                        largeTilesSpan
                    } else {
                        1
                    }
                    SizedTileImpl(tile, width)
                }
            }
        
        val firstRowTiles = sizedTiles.take(firstRowTileCount)
        val remainingTiles = sizedTiles.drop(firstRowTileCount)
        
        val allBounceables =
            remember(sizedTiles) { List(sizedTiles.size) { BounceableTileViewModel() } }
        val squishiness by viewModel.squishinessViewModel.squishiness.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()

        Column(modifier = modifier) {
            val firstRowSpans by remember(firstRowTiles) { 
                derivedStateOf { firstRowTiles.fastMap { it.width } } 
            }
            
            CustomVerticalSpannedGrid(
                columns = columns,
                rowSpacing = dimensionResource(R.dimen.qs_tile_margin_vertical),
                spans = firstRowSpans,
                keys = { firstRowTiles[it].tile.spec },
            ) { spanIndex, column, isFirstInColumn, isLastInColumn ->
                val it = firstRowTiles[spanIndex]
                Element(it.tile.spec.toElementKey(spanIndex), Modifier) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Tile(
                            tile = it.tile,
                            iconOnly = true,
                            squishiness = { squishiness },
                            tileHapticsViewModelFactoryProvider = tileHapticsViewModelFactoryProvider,
                            coroutineScope = scope,
                            bounceableInfo =
                                allBounceables.bounceableInfo(
                                    it,
                                    index = spanIndex,
                                    column = column,
                                    columns = columns,
                                    isFirstInRow = isFirstInColumn,
                                    isLastInRow = isLastInColumn,
                                ),
                            detailsViewModel = detailsViewModel,
                            isVisible = listening,
                            isFirstRow = true,
                        )
                    }
                }
            }
            
            if (remainingTiles.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isExpanded) {
                            // Preview of exactly 2 tiles
                            val previewTiles = remainingTiles.take(2)
                            
                            if (previewTiles.isNotEmpty()) {
                                // Display tiles in a row (for 2 tiles) or column (if only 1)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(
                                        dimensionResource(R.dimen.qs_tile_margin_horizontal),
                                        Alignment.CenterHorizontally
                                    ),
                                ) {
                                    previewTiles.forEachIndexed { index, it ->
                                        val actualIndex = firstRowTileCount + index
                                        val tileModifier = if (previewTiles.size == 2) {
                                            Modifier.weight(1f)
                                        } else {
                                            Modifier
                                        }
                                        
                                        Box(
                                            modifier = tileModifier,
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Element(it.tile.spec.toElementKey(actualIndex), Modifier) {
                                                Tile(
                                                    tile = it.tile,
                                                    iconOnly = iconTilesViewModel.isIconTile(it.tile.spec),
                                                    squishiness = { squishiness },
                                                    tileHapticsViewModelFactoryProvider = tileHapticsViewModelFactoryProvider,
                                                    coroutineScope = scope,
                                                    bounceableInfo =
                                                        allBounceables.bounceableInfo(
                                                            it,
                                                            index = actualIndex,
                                                            column = index,
                                                            columns = previewTiles.size,
                                                            isFirstInRow = index == 0,
                                                            isLastInRow = index == previewTiles.size - 1,
                                                        ),
                                                    detailsViewModel = detailsViewModel,
                                                    isVisible = listening,
                                                    isFirstRow = false,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            val remainingSpans by remember(remainingTiles) {
                                derivedStateOf { remainingTiles.fastMap { it.width } }
                            }
                            
                            CustomVerticalSpannedGrid(
                                columns = columns,
                                rowSpacing = dimensionResource(R.dimen.qs_tile_margin_vertical),
                                spans = remainingSpans,
                                keys = { remainingTiles[it].tile.spec },
                            ) { spanIndex, column, isFirstInColumn, isLastInColumn ->
                                val it = remainingTiles[spanIndex]
                                val actualIndex = firstRowTileCount + spanIndex
                                
                                Element(it.tile.spec.toElementKey(actualIndex), Modifier) {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Tile(
                                            tile = it.tile,
                                            iconOnly = iconTilesViewModel.isIconTile(it.tile.spec),
                                            squishiness = { squishiness },
                                            tileHapticsViewModelFactoryProvider = tileHapticsViewModelFactoryProvider,
                                            coroutineScope = scope,
                                            bounceableInfo =
                                                allBounceables.bounceableInfo(
                                                    it,
                                                    index = actualIndex,
                                                    column = column,
                                                    columns = columns,
                                                    isFirstInRow = isFirstInColumn,
                                                    isLastInRow = isLastInColumn,
                                                ),
                                            detailsViewModel = detailsViewModel,
                                            isVisible = listening,
                                            isFirstRow = false,
                                        )
                                    }
                                }
                            }
                        }
                        
                        if (remainingTiles.size > 2) {
                            ExpandCollapseButton(
                                isExpanded = isExpanded,
                                onClick = { isExpanded = !isExpanded },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        TileListener(tiles, listening)
    }

    @Composable
    override fun EditTileGrid(
        tiles: List<EditTileViewModel>,
        modifier: Modifier,
        onAddTile: (TileSpec, Int) -> Unit,
        onRemoveTile: (TileSpec) -> Unit,
        onSetTiles: (List<TileSpec>) -> Unit,
        onStopEditing: () -> Unit,
    ) {
        val viewModel =
            rememberViewModel(traceName = "InfiniteGridLayout.EditTileGrid") {
                viewModelFactory.create()
            }
        val iconTilesViewModel =
            rememberViewModel(traceName = "InfiniteGridLayout.EditTileGrid") {
                viewModel.dynamicIconTilesViewModelFactory.create()
            }
        val columnsViewModel =
            rememberViewModel(traceName = "InfiniteGridLayout.EditTileGrid") {
                viewModel.columnsWithMediaViewModelFactory.createWithoutMediaTracking()
            }
        val columns = columnsViewModel.columns
        val largeTilesSpan by iconTilesViewModel.largeTilesSpanState
        val largeTiles by iconTilesViewModel.largeTiles.collectAsStateWithLifecycle()

        // Non-current tiles should always be displayed as icon tiles.
        val sizedTiles =
            remember(tiles, largeTiles, largeTilesSpan) {
                tiles.map {
                    SizedTileImpl(
                        it,
                        if (!it.isCurrent || !largeTiles.contains(it.tileSpec)) 1
                        else largeTilesSpan,
                    )
                }
            }

        val (currentTiles, otherTiles) = sizedTiles.partition { it.tile.isCurrent }
        val currentListState = rememberEditListState(currentTiles, columns, largeTilesSpan)
        DefaultEditTileGrid(
            listState = currentListState,
            otherTiles = otherTiles,
            columns = columns,
            modifier = modifier,
            onAddTile = onAddTile,
            onRemoveTile = onRemoveTile,
            onSetTiles = onSetTiles,
            onResize = iconTilesViewModel::resize,
            onStopEditing = onStopEditing,
            onReset = viewModel::showResetDialog,
            largeTilesSpan = largeTilesSpan,
        )
    }

    override fun splitIntoPages(
        tiles: List<TileViewModel>,
        rows: Int,
        columns: Int,
    ): List<List<TileViewModel>> {

        return PaginatableGridLayout.splitInRows(
                tiles.map { SizedTileImpl(it, it.spec.width()) },
                columns,
            )
            .chunked(rows)
            .map { it.flatten().map { it.tile } }
    }

    private fun TileSpec.width(largeSize: Int = iconTilesViewModel.largeTilesSpan.value): Int {
        return if (iconTilesViewModel.isIconTile(this)) 1 else largeSize
    }
}

@Composable
private fun ExpandCollapseButton(
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "ExpandCollapseRotation"
    )
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isExpanded) "Show less" else "Show more",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
