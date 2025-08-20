/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.keyguard;

import static com.android.systemui.DejankUtils.whitelistIpcs;
import static com.android.systemui.Flags.msdlFeedback;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.telecom.TelecomManager;
// QTI_BEGIN: 2021-05-10: Android_UI: SystemUI: Fix emergency call button no response issue
import android.telephony.CellInfo;
// QTI_END: 2021-05-10: Android_UI: SystemUI: Fix emergency call button no response issue
import android.telephony.ServiceState;
// QTI_BEGIN: 2021-05-10: Android_UI: SystemUI: Fix emergency call button no response issue
import android.telephony.SubscriptionManager;
// QTI_END: 2021-05-10: Android_UI: SystemUI: Fix emergency call button no response issue
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.dagger.KeyguardBouncerScope;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.res.R;
import com.android.systemui.shade.ShadeController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener;
import com.android.systemui.user.domain.interactor.SelectedUserInteractor;
import com.android.systemui.util.EmergencyDialerConstants;
import com.android.systemui.util.ViewController;

import com.google.android.msdl.data.model.MSDLToken;
import com.google.android.msdl.domain.MSDLPlayer;

import java.util.concurrent.Executor;
// QTI_BEGIN: 2024-03-13: Android_UI: SystemUI:check ServiceStatus for all subs to show Emergency button
import java.util.HashMap;
// QTI_END: 2024-03-13: Android_UI: SystemUI:check ServiceStatus for all subs to show Emergency button

import javax.inject.Inject;

/** View Controller for {@link com.android.keyguard.EmergencyButton}. */
@KeyguardBouncerScope
public class EmergencyButtonController extends ViewController<EmergencyButton> {
    private static final String TAG = "EmergencyButton";
    private final ConfigurationController mConfigurationController;
    private final KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    private final PowerManager mPowerManager;
    private final ActivityTaskManager mActivityTaskManager;
    private final ShadeController mShadeController;
    private final TelecomManager mTelecomManager;
    private final MetricsLogger mMetricsLogger;

    private final LockPatternUtils mLockPatternUtils;
    private final Executor mMainExecutor;
    private final Executor mBackgroundExecutor;
    private final SelectedUserInteractor mSelectedUserInteractor;
    private final MSDLPlayer mMSDLPlayer;

    private EmergencyButtonCallback mEmergencyButtonCallback;

    private final KeyguardUpdateMonitorCallback mInfoCallback =
            new KeyguardUpdateMonitorCallback() {
        @Override
        public void onSimStateChanged(int subId, int slotId, int simState) {
            updateEmergencyCallButton();
        }

        @Override
        public void onPhoneStateChanged(int phoneState) {
            updateEmergencyCallButton();
        }

        @Override
        public void onServiceStateChanged(int subId, ServiceState state) {
            updateEmergencyCallButton();
        }
    };

    private final ConfigurationListener mConfigurationListener = new ConfigurationListener() {
        @Override
        public void onConfigChanged(Configuration newConfig) {
            updateEmergencyCallButton();
        }
    };

    @VisibleForTesting
    public EmergencyButtonController(@Nullable EmergencyButton view,
            ConfigurationController configurationController,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            PowerManager powerManager,
            ActivityTaskManager activityTaskManager,
            ShadeController shadeController,
            @Nullable TelecomManager telecomManager,
            MetricsLogger metricsLogger,
            LockPatternUtils lockPatternUtils,
            Executor mainExecutor, Executor backgroundExecutor,
            SelectedUserInteractor selectedUserInteractor,
            MSDLPlayer msdlPlayer) {
        super(view);
        mConfigurationController = configurationController;
        mKeyguardUpdateMonitor = keyguardUpdateMonitor;
        mPowerManager = powerManager;
        mActivityTaskManager = activityTaskManager;
        mShadeController = shadeController;
        mTelecomManager = telecomManager;
        mMetricsLogger = metricsLogger;
        mLockPatternUtils = lockPatternUtils;
        mMainExecutor = mainExecutor;
        mBackgroundExecutor = backgroundExecutor;
        mSelectedUserInteractor = selectedUserInteractor;
        mMSDLPlayer = msdlPlayer;
    }

    @Override
    protected void onInit() {
        whitelistIpcs(this::updateEmergencyCallButton);
    }

    @Override
    protected void onViewAttached() {
        mKeyguardUpdateMonitor.registerCallback(mInfoCallback);
        mConfigurationController.addCallback(mConfigurationListener);
        mView.setOnClickListener(v -> takeEmergencyCallAction());
    }

    @Override
    protected void onViewDetached() {
        mKeyguardUpdateMonitor.removeCallback(mInfoCallback);
        mConfigurationController.removeCallback(mConfigurationListener);
    }

    /**
     * Updates the visibility of the emergency button.
     *
     * This method runs binder calls in a background thread.
     */
    @VisibleForTesting
    @SuppressLint("MissingPermission")
    public void updateEmergencyCallButton() {
        if (mView != null) {
            // Run in bg thread to avoid throttling the main thread with binder call.
            mBackgroundExecutor.execute(() -> {
                boolean isInCall = mTelecomManager != null && mTelecomManager.isInCall();
                boolean isSecure = mLockPatternUtils
                        .isSecure(mSelectedUserInteractor.getSelectedUserId());
                mMainExecutor.execute(() -> mView.updateEmergencyCallButton(
                        /* isInCall= */ isInCall,
                        /* hasTelephonyRadio= */ getContext().getPackageManager()
                                .hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CALLING),
                        /* simLocked= */ mKeyguardUpdateMonitor.isSimPinVoiceSecure(),
                        /* isSecure= */ isSecure,
                        isEmergencyCapable()));
            });
        }
    }

    public void setEmergencyButtonCallback(EmergencyButtonCallback callback) {
        mEmergencyButtonCallback = callback;
    }
    /**
     * Shows the emergency dialer or returns the user to the existing call.
     */
    @SuppressLint("MissingPermission")
    public void takeEmergencyCallAction() {
        mMetricsLogger.action(MetricsEvent.ACTION_EMERGENCY_CALL);
        if (msdlFeedback()) {
            mMSDLPlayer.playToken(MSDLToken.KEYPRESS_RETURN, null);
        }
        if (mPowerManager != null) {
            mPowerManager.userActivity(SystemClock.uptimeMillis(), true);
        }
        mActivityTaskManager.stopSystemLockTaskMode();
        mShadeController.collapseShade(false);
        // Run in bg thread to avoid throttling the main thread with binder call.
        mBackgroundExecutor.execute(() -> {
            boolean isInCall = mTelecomManager != null && mTelecomManager.isInCall();
            mMainExecutor.execute(() -> {
                if (isInCall) {
                    mTelecomManager.showInCallScreen(false);
                    if (mEmergencyButtonCallback != null) {
                        mEmergencyButtonCallback.onEmergencyButtonClickedWhenInCall();
                    }
                } else {
                    mKeyguardUpdateMonitor.reportEmergencyCallAction(true /* bypassHandler */);
                    if (mTelecomManager == null) {
                        Log.wtf(TAG, "TelecomManager was null, cannot launch emergency dialer");
                        return;
                    }
                    com.android.systemui.keyguard.MistouchInteractor.Companion.get().handleEmergencyButtonClick();
                    Intent emergencyDialIntent =
                            mTelecomManager.createLaunchEmergencyDialerIntent(null /* number*/)
                                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                                            | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    .putExtra(EmergencyDialerConstants.EXTRA_ENTRY_TYPE,
                                            EmergencyDialerConstants.ENTRY_TYPE_LOCKSCREEN_BUTTON);

                    getContext().startActivityAsUser(emergencyDialIntent,
                            ActivityOptions.makeCustomAnimation(getContext(), 0, 0).toBundle(),
                            new UserHandle(mSelectedUserInteractor.getSelectedUserId()));
                }
            });
        });
    }

// QTI_BEGIN: 2021-05-10: Android_UI: SystemUI: Fix emergency call button no response issue
    private boolean isEmergencyCapable() {
// QTI_END: 2021-05-10: Android_UI: SystemUI: Fix emergency call button no response issue
// QTI_BEGIN: 2024-05-16: Android_UI: SystemUI: Update EmergencyButton display logic
        int slotCount = mKeyguardUpdateMonitor.getActiveSlots();
        Log.d(TAG, "isEmergencyCapable slotCount:" + slotCount);
        for (int slotId = 0; slotId < slotCount; slotId++) {
            ServiceState ss = mKeyguardUpdateMonitor.getServiceStateWithSlotid(slotId);
            Log.d(TAG, "mServiceStates list slotid:" + slotId + ";;ss:" + ss);
// QTI_END: 2024-05-16: Android_UI: SystemUI: Update EmergencyButton display logic
// QTI_BEGIN: 2024-03-13: Android_UI: SystemUI:check ServiceStatus for all subs to show Emergency button
            if (ss != null) {
                if (ss.isEmergencyOnly()) {
                    return true;
// QTI_END: 2024-03-13: Android_UI: SystemUI:check ServiceStatus for all subs to show Emergency button
// QTI_BEGIN: 2024-05-16: Android_UI: SystemUI: Update EmergencyButton display logic
                } else if ((ss.getVoiceRegState() != ServiceState.STATE_OUT_OF_SERVICE)
                        && (ss.getVoiceRegState() != ServiceState.STATE_POWER_OFF)) {
                    return true;
// QTI_END: 2024-05-16: Android_UI: SystemUI: Update EmergencyButton display logic
// QTI_BEGIN: 2024-03-13: Android_UI: SystemUI:check ServiceStatus for all subs to show Emergency button
                }
            }
        }
        return false;
// QTI_END: 2024-03-13: Android_UI: SystemUI:check ServiceStatus for all subs to show Emergency button
// QTI_BEGIN: 2021-05-10: Android_UI: SystemUI: Fix emergency call button no response issue
    }

// QTI_END: 2021-05-10: Android_UI: SystemUI: Fix emergency call button no response issue
    /** */
    public interface EmergencyButtonCallback {
        /** */
        void onEmergencyButtonClickedWhenInCall();
    }

    /** Injectable Factory for creating {@link EmergencyButtonController}. */
    public static class Factory {
        private final ConfigurationController mConfigurationController;
        private final KeyguardUpdateMonitor mKeyguardUpdateMonitor;
        private final PowerManager mPowerManager;
        private final ActivityTaskManager mActivityTaskManager;
        private final ShadeController mShadeController;
        @Nullable
        private final TelecomManager mTelecomManager;
        private final MetricsLogger mMetricsLogger;
        private final LockPatternUtils mLockPatternUtils;
        private final Executor mMainExecutor;
        private final Executor mBackgroundExecutor;
        private final SelectedUserInteractor mSelectedUserInteractor;
        private final MSDLPlayer mMSDLPlayer;

        @Inject
        public Factory(ConfigurationController configurationController,
                KeyguardUpdateMonitor keyguardUpdateMonitor,
                PowerManager powerManager,
                ActivityTaskManager activityTaskManager,
                ShadeController shadeController,
                @Nullable TelecomManager telecomManager,
                MetricsLogger metricsLogger,
                LockPatternUtils lockPatternUtils,
                @Main Executor mainExecutor,
                @Background Executor backgroundExecutor,
                SelectedUserInteractor selectedUserInteractor,
                MSDLPlayer msdlPlayer) {

            mConfigurationController = configurationController;
            mKeyguardUpdateMonitor = keyguardUpdateMonitor;
            mPowerManager = powerManager;
            mActivityTaskManager = activityTaskManager;
            mShadeController = shadeController;
            mTelecomManager = telecomManager;
            mMetricsLogger = metricsLogger;
            mLockPatternUtils = lockPatternUtils;
            mMainExecutor = mainExecutor;
            mBackgroundExecutor = backgroundExecutor;
            mSelectedUserInteractor = selectedUserInteractor;
            mMSDLPlayer = msdlPlayer;
        }

        /** Construct an {@link com.android.keyguard.EmergencyButtonController}. */
        public EmergencyButtonController create(EmergencyButton view) {
            return new EmergencyButtonController(view, mConfigurationController,
                    mKeyguardUpdateMonitor, mPowerManager, mActivityTaskManager, mShadeController,
                    mTelecomManager, mMetricsLogger, mLockPatternUtils, mMainExecutor,
                    mBackgroundExecutor, mSelectedUserInteractor, mMSDLPlayer);
        }
    }
}
