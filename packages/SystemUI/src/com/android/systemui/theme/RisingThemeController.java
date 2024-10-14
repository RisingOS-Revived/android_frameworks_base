/*
 * Copyright (C) 2023-2024 The RisingOS Android Project
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
package com.android.systemui.theme;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.android.systemui.res.R;

import lineageos.providers.LineageSettings;

public class RisingThemeController {

    private static final String TAG = "RisingThemeController";

    private static final int TYPE_SECURE = 0;
    private static final int TYPE_SYSTEM = 1;
    private static final int TYPE_LINEAGE_SYSTEM = 2;
    private static final int TYPE_LINEAGE_SECURE = 3;

    private final ContentResolver mContentResolver;
    private final Handler mBackgroundHandler;
    private final Context mContext;

    private Runnable mDebounceRunnable;

    public RisingThemeController(Context context, Handler backgroundHandler) {
        this.mContext = context;
        this.mContentResolver = mContext.getContentResolver();
        this.mBackgroundHandler = backgroundHandler;
    }

    public void observeSettings(Runnable reevaluateSystemThemeCallback) {
        observeSettingsKeys(RisingSettingsConstants.LINEAGE_SECURE_SETTINGS_KEYS, reevaluateSystemThemeCallback, TYPE_LINEAGE_SECURE);
        observeSettingsKeys(RisingSettingsConstants.LINEAGE_SYSTEM_SETTINGS_KEYS, reevaluateSystemThemeCallback, TYPE_LINEAGE_SYSTEM);
        observeSettingsKeys(RisingSettingsConstants.SYSTEM_SETTINGS_KEYS, reevaluateSystemThemeCallback, TYPE_SYSTEM);
        observeSettingsKeys(RisingSettingsConstants.SECURE_SETTINGS_KEYS, reevaluateSystemThemeCallback, TYPE_SECURE);
        observeSettingsKeys(RisingSettingsConstants.SYSTEM_SETTINGS_NOTIFY_ONLY_KEYS, null, TYPE_SYSTEM);
        observeSettingsKeys(RisingSettingsConstants.SECURE_SETTINGS_NOTIFY_ONLY_KEYS, null, TYPE_SECURE);
        observeRestartKey();
    }

    private void observeRestartKey() {
        Uri restartUri = Settings.System.getUriFor("system_ui_restart");
        observe(restartUri, () -> {
            android.os.Process.killProcess(android.os.Process.myPid());
        });
    }

    private void observeSettingsKeys(String[] keys, Runnable reevaluateSystemThemeCallback, int type) {
        Uri settingsUri = null;

        for (String key : keys) {
            switch (type) {
                case TYPE_SECURE:
                    settingsUri = Settings.Secure.getUriFor(key);
                    break;
                case TYPE_SYSTEM:
                    settingsUri = Settings.System.getUriFor(key);
                    break;
                case TYPE_LINEAGE_SYSTEM:
                    settingsUri = LineageSettings.System.getUriFor(key);
                    break;
                case TYPE_LINEAGE_SECURE:
                    settingsUri = LineageSettings.Secure.getUriFor(key);
                    break;
                default:
                    Log.e(TAG, "Unknown type for key: " + key);
                    continue;
            }
            if (settingsUri != null) {
                observe(settingsUri, reevaluateSystemThemeCallback);
            } else {
                Log.e(TAG, "Failed to get URI for key: " + key);
            }
        }
    }

    private void observe(Uri uri, Runnable reevaluateSystemThemeCallback) {
        if (uri != null) {
            ContentObserver contentObserver = new ContentObserver(mBackgroundHandler) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    Toast toast = Toast.makeText(mContext, R.string.reevaluating_system_theme, Toast.LENGTH_SHORT);
                    if (isDeviceSetupComplete()) {
                        toast.show();
                    }
                    if (mDebounceRunnable != null) {
                        mBackgroundHandler.removeCallbacks(mDebounceRunnable);
                    }
                    mDebounceRunnable = () -> {
                        if (reevaluateSystemThemeCallback != null) {
                            mBackgroundHandler.post(() -> reevaluateSystemThemeCallback.run());
                        }
                    };
                    mBackgroundHandler.postDelayed(mDebounceRunnable, 1000);
                }
            };
            mContentResolver.registerContentObserver(uri, false, contentObserver);
        } else {
            Log.e(TAG, "Failed to get URI for observing");
        }
    }

    private boolean isDeviceSetupComplete() {
        try {
            return Settings.Secure.getInt(mContentResolver, Settings.Secure.USER_SETUP_COMPLETE) == 1;
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "USER_SETUP_COMPLETE setting not found", e);
            return false;
        }
    }
}
