/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import static android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED_WINDOW_EXT;
import static android.app.WindowConfiguration.WINDOWING_MODE_MINI_WINDOW_EXT;

import static com.android.server.wm.PopUpWindowController.PACKAGE_NAME_SYSTEM_TOOL;

import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.IActivityTaskManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Slog;

class PopUpAppStarter {

    private static final String TAG = "PopUpAppStarter";

    private static class InstanceHolder {
        private static final PopUpAppStarter INSTANCE = new PopUpAppStarter();
    }

    static PopUpAppStarter getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private Context mContext;
    private IActivityTaskManager mActivityTaskManager;
    private LauncherApps mLauncherApps;
    private PackageManager mPackageManager;

    void init(Context context) {
        mContext = context;
        mActivityTaskManager = ActivityTaskManager.getService();
        mLauncherApps = context.getSystemService(LauncherApps.class);
        mPackageManager = context.getPackageManager();
    }

    void startActivity(String packageName, String activityName,
            String shortcutId, int shortcutUserId, Bundle bundle) {
        if (!TextUtils.isEmpty(shortcutId) && shortcutUserId != Integer.MIN_VALUE) {
            startShortcut(packageName, shortcutId, shortcutUserId, bundle);
            return;
        }

        final Intent intent;
        if (TextUtils.isEmpty(activityName)) {
            intent = mPackageManager.getLaunchIntentForPackage(packageName);
        } else {
            intent = new Intent();
            intent.setClassName(packageName, activityName);
        }
        if (intent == null) {
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (PACKAGE_NAME_SYSTEM_TOOL.equals(packageName)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        }
        try {
            mContext.startActivityAsUser(intent, bundle, UserHandle.CURRENT);
        } catch (Exception e) {
            Slog.e(TAG, "Failed to start " + packageName + "/" + activityName, e);
        }
    }

    void startActivity(int taskId, Bundle bundle) {
        try {
            mActivityTaskManager.startActivityFromRecents(taskId, bundle);
        } catch (Exception e) {
            Slog.e(TAG, "Failed to start activity from taskId: " + taskId, e);
        }
    }

    private void startShortcut(String packageName, String shortcutId,
            int shortcutUserId, Bundle bundle) {
        try {
            mLauncherApps.startShortcut(
                packageName, shortcutId, null, bundle, new UserHandle(shortcutUserId));
        } catch (Exception e) {
            Slog.e(TAG, "Failed to start shortcut " + packageName + "/" + shortcutId, e);
        }
    }

    static Bundle getMiniWindowBundle() {
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_MINI_WINDOW_EXT);
        options.setPendingIntentBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        return options.toBundle();
    }

    static Bundle getPinnedWindowBundle() {
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_PINNED_WINDOW_EXT);
        options.setPendingIntentBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        return options.toBundle();
    }
}
