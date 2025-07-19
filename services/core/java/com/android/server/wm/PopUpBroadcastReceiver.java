 /*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED_WINDOW_EXT;
import static android.app.WindowConfiguration.WINDOWING_MODE_MINI_WINDOW_EXT;

import static org.rising.DebugConstants.DEBUG_POP_UP;
import static org.rising.view.PopUpViewManager.ACTION_PIN_CURRENT_APP;
import static org.rising.view.PopUpViewManager.ACTION_START_MINI_WINDOW;
import static org.rising.view.PopUpViewManager.ACTION_START_PINNED_WINDOW;
import static org.rising.view.PopUpViewManager.EXTRA_ACTIVITY_NAME;
import static org.rising.view.PopUpViewManager.EXTRA_PACKAGE_NAME;
import static org.rising.view.PopUpViewManager.EXTRA_SHORTCUT_ID;
import static org.rising.view.PopUpViewManager.EXTRA_SHORTCUT_USER_ID;
import static org.rising.view.PopUpViewManager.FEATURE_SUPPORTED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Slog;

import com.android.internal.util.android.Utils;

public class PopUpBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "PopUpBroadcastReceiver";

    private static final boolean ALLOW_START_TOP = false;

    private static class InstanceHolder {
        private static final PopUpBroadcastReceiver INSTANCE = new PopUpBroadcastReceiver();
    }

    public static PopUpBroadcastReceiver getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private boolean mBootCompleted = false;

    void init(Context context, Handler handler) {
        if (FEATURE_SUPPORTED) {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_START_MINI_WINDOW);
            filter.addAction(ACTION_START_PINNED_WINDOW);
            filter.addAction(ACTION_PIN_CURRENT_APP);
            context.registerReceiverForAllUsers(this, filter, null, handler);
        }

        mBootCompleted = true;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        if (ACTION_PIN_CURRENT_APP.equals(action)) {
            if (TopActivityRecorder.getInstance().hasMiniWindow()) {
                if (DEBUG_POP_UP) {
                    Slog.d(TAG, "pin current app, return: already has mini window");
                }
                return;
            }
            if (TopActivityRecorder.getInstance().isTopFullscreenActivityHome()) {
                if (DEBUG_POP_UP) {
                    Slog.d(TAG, "pin current app, return: top fullscreen activity is home");
                }
                return;
            }
            final int topTaskId = TopActivityRecorder.getInstance().getTopFullscreenTaskId();
            if (topTaskId != INVALID_TASK_ID) {
                if (DEBUG_POP_UP) {
                    final String packageName = TopActivityRecorder.getInstance().getTopFullscreenPackage();
                    Slog.d(TAG, "pin current app, top fullscreen package: " + packageName);
                }
                PopUpWindowController.getInstance().notifyNextRecentIsPin();
                PopUpAppStarter.getInstance().startActivity(topTaskId, PopUpAppStarter.getPinnedWindowBundle());
            }
            return;
        }

        final String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        if (TextUtils.isEmpty(packageName)) {
            return;
        }
        if (!Utils.isPackageInstalled(context, packageName, false)) {
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "return: package " + packageName + " is not available");
            }
            return;
        }
        if (TopActivityRecorder.getInstance().isPackageAtTop(packageName) && !ALLOW_START_TOP) {
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "return: package " + packageName + " already at top");
            }
            return;
        }

        final String activityName = intent.getStringExtra(EXTRA_ACTIVITY_NAME);
        final String shortcutId = intent.getStringExtra(EXTRA_SHORTCUT_ID);
        final int shortcutUserId = intent.getIntExtra(EXTRA_SHORTCUT_USER_ID, Integer.MIN_VALUE);

        Bundle bundle = null;
        switch (action) {
            case ACTION_START_MINI_WINDOW:
                bundle = PopUpAppStarter.getMiniWindowBundle();
                break;
            case ACTION_START_PINNED_WINDOW:
                bundle = PopUpAppStarter.getPinnedWindowBundle();
                break;
        }
        if (bundle != null) {
            PopUpAppStarter.getInstance().startActivity(
                packageName,
                activityName,
                shortcutId,
                shortcutUserId,
                bundle
            );
        }
    }
}
