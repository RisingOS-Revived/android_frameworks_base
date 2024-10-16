/*
 * Copyright (C) 2023-2024 the risingOS Android Project
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

    private final Context mContext;
    private final PowerManager mPowerManager;

    private boolean mPluggedIn = false;
    private boolean mReceiverRegistered = false;
    private boolean mServiceEnabled = false;
    private boolean mAODActive = false;

    private final BroadcastReceiver mPowerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null || !mServiceEnabled) return;
            switch (action) {
                case Intent.ACTION_BATTERY_CHANGED:
                    handleBatteryChanged(intent);
                    break;
                case Intent.ACTION_POWER_DISCONNECTED:
                    Slog.v(TAG, "Device unplugged, disabling AOD");
                    mPluggedIn = false;
                    maybeDeactivateAOD();
                    break;
            }
        }
    };

    private final ContentObserver mSettingsObserver = new ContentObserver(null) {
        @Override
        public void onChange(boolean selfChange) {
            mServiceEnabled = Settings.System.getInt(mContext.getContentResolver(),
                    "doze_always_on_charge_mode", 0) != 0;
            mAODActive = Settings.Secure.getInt(mContext.getContentResolver(),
                    Settings.Secure.DOZE_ALWAYS_ON, 0) != 0;
            if (mServiceEnabled) {
                registerPowerReceiver();
            } else {
                unregisterPowerReceiver();
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
            Slog.v(TAG, "onBootPhase PHASE_BOOT_COMPLETED");
            if (mServiceEnabled) {
                // reset AOD state on boot if service is enabled
                Settings.Secure.putInt(mContext.getContentResolver(), 
                    Settings.Secure.DOZE_ALWAYS_ON, 0);
            }
        }
    }

    private void registerPowerReceiver() {
        if (mReceiverRegistered) return;
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        mContext.registerReceiver(mPowerReceiver, filter);
        mReceiverRegistered = true;
    }

    private void unregisterPowerReceiver() {
        if (!mReceiverRegistered) return;
        mContext.unregisterReceiver(mPowerReceiver);
        mReceiverRegistered = false;
    }

    private void registerSettingsObserver() {
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor("doze_always_on_charge_mode"), 
                true, 
                mSettingsObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.DOZE_ALWAYS_ON), 
                true, 
                mSettingsObserver);
    }

    private void handleBatteryChanged(Intent intent) {
        if (isCharging(intent) && isPluggedIn(intent)) {
            Slog.v(TAG, "Device plugged in and charging, enabling AOD");
            mPluggedIn = true;
            maybeActivateAOD();
        } else {
            Slog.v(TAG, "Device not charging, disabling AOD");
            mPluggedIn = false;
            maybeDeactivateAOD();
        }
    }

    private void maybeActivateAOD() {
        if (mPluggedIn && !mAODActive) {
            Slog.v(TAG, "Activating AOD due to device being plugged in");
            setAutoAODChargeActive(true);
        }
    }

    private void maybeDeactivateAOD() {
        if (!mPluggedIn && mAODActive) {
            Slog.v(TAG, "Deactivating AOD due to device being unplugged");
            setAutoAODChargeActive(false);
        }
    }

    private void setAutoAODChargeActive(boolean activate) {
        if (!mServiceEnabled) {
            return;
        }
        if (activate && mAODActive) {
            Slog.v(TAG, "AOD is already enabled, skipping activation");
            return;
        }
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.DOZE_ALWAYS_ON, activate ? 1 : 0);
        Slog.v(TAG, activate ? "AOD activated by service" : "AOD deactivated by service");
        handleAODStateChange(activate);
    }

    private void handleAODStateChange(boolean activate) {
        if (mPowerManager.isInteractive()) {
            Slog.v(TAG, "Screen is already on, no further action needed");
        } else {
            if ((activate && !isWakeOnPlugEnabled()) || !activate) {
                mContext.sendBroadcast(new Intent(PULSE_ACTION));
            }
        }
    }

    private boolean isWakeOnPlugEnabled() {
        return LineageSettings.Global.getInt(mContext.getContentResolver(),
                LineageSettings.Global.WAKE_WHEN_PLUGGED_OR_UNPLUGGED,
                (mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_unplugTurnsOnScreen) ? 1 : 0)) == 1;
    }

    private boolean isCharging(Intent intent) {
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    private boolean isPluggedIn(Intent intent) {
        int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return chargePlug == BatteryManager.BATTERY_PLUGGED_AC
                || chargePlug == BatteryManager.BATTERY_PLUGGED_USB
                || chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS;
    }
}
