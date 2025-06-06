/*
 * Copyright (C) 2023-2024 the risingOS Android Project
 * Copyright (C) 2025 AxionAOSP Project
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
package com.android.server.display;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Slog;

import lineageos.providers.LineageSettings;
import com.android.server.SystemService;

public class AODOnChargeService extends SystemService {
    private static final String TAG = "AODOnChargeService";
    private static final String PULSE_ACTION = "com.android.systemui.doze.pulse";

    private static final int MODE_DISABLED = 0;
    private static final int MODE_PLUGGED_IN = 1;
    private static final int MODE_CHARGING = 2;

    private final Context mContext;
    private final PowerManager mPowerManager;

    private boolean mReceiverRegistered = false;
    private boolean mAODActive = false;
    private boolean mServiceEnabled = false;
    private int mCurrentMode = MODE_DISABLED;

    private final BroadcastReceiver mPowerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null || !mServiceEnabled) return;

            switch (action) {
                case Intent.ACTION_BATTERY_CHANGED:
                case Intent.ACTION_POWER_CONNECTED:
                    handleBatteryStateChange(intent);
                    break;
                case Intent.ACTION_POWER_DISCONNECTED:
                    Slog.v(TAG, "Power disconnected");
                    mCurrentMode = MODE_DISABLED;
                    maybeDeactivateAOD();
                    break;
            }
        }
    };

    private final ContentObserver mSettingsObserver = new ContentObserver(null) {
        @Override
        public void onChange(boolean selfChange) {
            mServiceEnabled = Settings.System.getInt(
                    mContext.getContentResolver(), "doze_always_on_charge_mode", 0) != 0;

            mAODActive = Settings.Secure.getInt(
                    mContext.getContentResolver(), Settings.Secure.DOZE_ALWAYS_ON, 0) != 0;

            Slog.v(TAG, "Settings changed: serviceEnabled=" + mServiceEnabled + ", aodActive=" + mAODActive);

            if (mServiceEnabled) {
                registerPowerReceiver();
                refreshPowerState();
            } else {
                unregisterPowerReceiver();
                maybeDeactivateAOD();
            }
        }
    };

    public AODOnChargeService(Context context) {
        super(context);
        mContext = context;
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    }

    @Override
    public void onStart() {
        Slog.v(TAG, "Starting " + TAG);
        publishLocalService(AODOnChargeService.class, this);
        registerSettingsObserver();
        mSettingsObserver.onChange(true);
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_BOOT_COMPLETED) {
            Slog.v(TAG, "Boot completed");
            if (mServiceEnabled) {
                refreshPowerState();
            }
        }
    }

    private void registerSettingsObserver() {
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor("doze_always_on_charge_mode"), true, mSettingsObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.DOZE_ALWAYS_ON), true, mSettingsObserver);
    }

    private void registerPowerReceiver() {
        if (mReceiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        mContext.registerReceiver(mPowerReceiver, filter);
        mReceiverRegistered = true;
        Slog.v(TAG, "Power receiver registered");
    }

    private void unregisterPowerReceiver() {
        if (!mReceiverRegistered) return;
        mContext.unregisterReceiver(mPowerReceiver);
        mReceiverRegistered = false;
        Slog.v(TAG, "Power receiver unregistered");
    }

    private void handleBatteryStateChange(Intent intent) {
        if (!mServiceEnabled) {
            mCurrentMode = MODE_DISABLED;
            maybeDeactivateAOD();
            return;
        }

        boolean isCharging = isCharging(intent);
        boolean isPlugged = isPluggedIn(intent);

        int mode;
        if (isCharging) {
            mode = MODE_CHARGING;
        } else if (isPlugged) {
            mode = MODE_PLUGGED_IN;
        } else {
            mode = MODE_DISABLED;
        }

        if (mCurrentMode != mode) {
            Slog.v(TAG, "Power mode changed: " + mCurrentMode + " -> " + mode);
            mCurrentMode = mode;
        }

        if (mCurrentMode == MODE_DISABLED) {
            maybeDeactivateAOD();
        } else {
            maybeActivateAOD();
        }
    }

    private void maybeActivateAOD() {
        if (!mAODActive) {
            Slog.v(TAG, "Activating AOD due to current power mode: " + mCurrentMode);
            setAODState(true);
        }
    }

    private void maybeDeactivateAOD() {
        if (mAODActive) {
            Slog.v(TAG, "Deactivating AOD due to current power mode: " + mCurrentMode);
            setAODState(false);
        }
    }

    private void setAODState(boolean activate) {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.DOZE_ALWAYS_ON, activate ? 1 : 0);
        mAODActive = activate;

        if (!mPowerManager.isInteractive()) {
            if ((activate && !isWakeOnPlugEnabled()) || !activate) {
                mContext.sendBroadcast(new Intent(PULSE_ACTION));
            }
        }

        Slog.v(TAG, activate ? "AOD enabled by service" : "AOD disabled by service");
    }

    private boolean isWakeOnPlugEnabled() {
        return LineageSettings.Global.getInt(mContext.getContentResolver(),
                LineageSettings.Global.WAKE_WHEN_PLUGGED_OR_UNPLUGGED,
                mContext.getResources().getBoolean(
                        com.android.internal.R.bool.config_unplugTurnsOnScreen) ? 1 : 0) == 1;
    }

    private boolean isCharging(Intent intent) {
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    private boolean isPluggedIn(Intent intent) {
        int plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return plugType == BatteryManager.BATTERY_PLUGGED_AC
                || plugType == BatteryManager.BATTERY_PLUGGED_USB
                || plugType == BatteryManager.BATTERY_PLUGGED_WIRELESS
                || plugType == BatteryManager.BATTERY_PLUGGED_DOCK;
    }

    private void refreshPowerState() {
        Intent batteryStatus = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus != null) {
            handleBatteryStateChange(batteryStatus);
        }
    }
}
