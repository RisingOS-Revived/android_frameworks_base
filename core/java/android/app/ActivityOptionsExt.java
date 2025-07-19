/*
 * Copyright (C) 2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package android.app;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;

import static org.rising.DebugConstants.DEBUG_POP_UP;
import static org.rising.view.PopUpViewManager.FEATURE_SUPPORTED;

import android.util.Slog;

/** @hide */
class ActivityOptionsExt {

    private static final String TAG = "ActivityOptionsExt";

    private static final String PKG_LAUNCHER3 = "com.android.launcher3";
    private static final String PKG_PIXEL_LAUNCHER = "com.google.android.apps.nexuslauncher";

    private ActivityOptionsExt() {}

    static boolean hookLauncherSetLaunchBounds() {
        if (!FEATURE_SUPPORTED) {
            return false;
        }
        return isFromLauncher();
    }

    static boolean hookLauncherSetFreeform(int windowingMode) {
        if (!FEATURE_SUPPORTED) {
            return false;
        }
        return isFromLauncher() && windowingMode == WINDOWING_MODE_FREEFORM;
    }

    private static boolean isFromLauncher() {
        final String packageName = ActivityThread.currentOpPackageName();
        if (packageName == null) {
            return false;
        }
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "isFromLauncher, packageName=" + packageName);
        }
        if (PKG_LAUNCHER3.equals(packageName)) {
            return true;
        }
        if (PKG_PIXEL_LAUNCHER.equals(packageName)) {
            return true;
        }
        if (packageName.toLowerCase().contains("lawnchair")) {
            return true;
        }
        return false;
    }
}
