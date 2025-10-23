/*
 * Copyright (C) 2025 RisingOS Revived Android Project
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

package com.android.systemui.qs.tiles

import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import com.android.internal.logging.MetricsLogger
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.systemui.animation.Expandable
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile.BooleanState
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.res.R
import com.android.systemui.volume.domain.interactor.VolumeInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

class RingerModeTile @Inject constructor(
    host: QSHost,
    uiEventLogger: QsEventLogger,
    @Background backgroundLooper: Looper,
    @Main private val mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    private val activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    private val volumeInteractor: VolumeInteractor
) : QSTileImpl<BooleanState>(
    host, uiEventLogger, backgroundLooper, mainHandler, falsingManager,
    metricsLogger, statusBarStateController, activityStarter, qsLogger
) {

    companion object {
        const val TILE_SPEC = "ringer"
    }

    private var ringerModeJob: Job? = null
    private var currentMode: Int = volumeInteractor.getRingerMode()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun newTileState(): BooleanState {
        return BooleanState().apply {
            handlesLongClick = true
        }
    }

    override fun getLongClickIntent(): Intent {
        return Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)
    }

    override fun isAvailable(): Boolean = true

    override fun handleClick(expandable: Expandable?) {
        cycleRingerMode()
    }

    override fun handleLongClick(expandable: Expandable?) {
        val intent = longClickIntent
        activityStarter.postStartActivityDismissingKeyguard(intent, 0)
    }

    private fun cycleRingerMode() {
        volumeInteractor.toggleRingerMode()
    }

    override fun handleUpdateState(state: BooleanState, arg: Any?) {
        val mode = arg as? Int ?: volumeInteractor.getRingerMode()
        state.label = mContext.getString(R.string.quick_settings_ringer_label)
        
        when (mode) {
            AudioManager.RINGER_MODE_SILENT -> {
                state.state = Tile.STATE_ACTIVE
                state.icon = ResourceIcon.get(R.drawable.ic_volume_ringer_mute)
                state.secondaryLabel = mContext.getString(R.string.quick_settings_ringer_silent)
                state.contentDescription = "${state.label}, ${state.secondaryLabel}"
                state.value = true
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                state.state = Tile.STATE_ACTIVE
                state.icon = ResourceIcon.get(R.drawable.ic_volume_ringer_vibrate)
                state.secondaryLabel = mContext.getString(R.string.quick_settings_ringer_vibrate)
                state.contentDescription = "${state.label}, ${state.secondaryLabel}"
                state.value = true
            }
            AudioManager.RINGER_MODE_NORMAL -> {
                state.state = Tile.STATE_ACTIVE
                state.icon = ResourceIcon.get(R.drawable.ic_volume_ringer)
                state.secondaryLabel = mContext.getString(R.string.quick_settings_ringer_ring)
                state.contentDescription = "${state.label}, ${state.secondaryLabel}"
                state.value = true
            }
        }
        
        state.stateDescription = state.secondaryLabel
    }

    override fun getTileLabel(): CharSequence {
        return mContext.getString(R.string.quick_settings_ringer_label)
    }

    override fun handleSetListening(listening: Boolean) {
        super.handleSetListening(listening)
        if (listening) {
            ringerModeJob?.cancel()
            ringerModeJob = scope.launch {
                volumeInteractor.getRingerModeFlow().collect { mode ->
                    currentMode = mode
                    refreshState(mode)
                }
            }
        } else {
            ringerModeJob?.cancel()
            ringerModeJob = null
        }
    }

    override fun handleDestroy() {
        super.handleDestroy()
        ringerModeJob?.cancel()
        ringerModeJob = null
    }

    override fun getMetricsCategory(): Int {
        return MetricsEvent.QS_PANEL
    }
}
