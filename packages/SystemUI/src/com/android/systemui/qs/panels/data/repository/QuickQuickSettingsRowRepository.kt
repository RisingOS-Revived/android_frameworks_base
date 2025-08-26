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

package com.android.systemui.qs.panels.data.repository

import android.content.res.Configuration
import android.content.res.Resources
import com.android.systemui.common.ui.data.repository.ConfigurationRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.util.kotlin.emitOnStart
import com.android.systemui.util.settings.SystemSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

@SysUISingleton
class QuickQuickSettingsRowRepository
@Inject
constructor(
    @ShadeDisplayAware private val resources: Resources,
    @ShadeDisplayAware configurationRepository: ConfigurationRepository,
    private val systemSettings: SystemSettings,
) {
    val rows: Flow<Int> =
        configurationRepository.onConfigurationChange.emitOnStart().mapLatest { _ ->
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val settingKey = if (isLandscape) "qs_layout_rows_landscape" else "qs_layout_rows"
            val defaultValue = if (isLandscape) {
                try {
                    resources.getInteger(R.integer.quick_settings_paginated_grid_num_rows_landscape)
                } catch (e: android.content.res.Resources.NotFoundException) {
                    resources.getInteger(R.integer.quick_settings_paginated_grid_num_rows)
                }
            } else {
                resources.getInteger(R.integer.quick_settings_paginated_grid_num_rows)
            }
            
            systemSettings.getIntForUser(
                settingKey,
                defaultValue,
                systemSettings.userId
            )
        }
    
    val quickRows: Flow<Int> =
        configurationRepository.onConfigurationChange.emitOnStart().mapLatest { _ ->
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val settingKey = if (isLandscape) "qqs_layout_rows_landscape" else "qqs_layout_rows"
            val defaultValue = if (isLandscape) {
                try {
                    resources.getInteger(R.integer.quick_qs_paginated_grid_num_rows_landscape)
                } catch (e: android.content.res.Resources.NotFoundException) {
                    resources.getInteger(R.integer.quick_qs_paginated_grid_num_rows)
                }
            } else {
                resources.getInteger(R.integer.quick_qs_paginated_grid_num_rows)
            }
            
            systemSettings.getIntForUser(
                settingKey,
                defaultValue,
                systemSettings.userId
            )
        }
}
