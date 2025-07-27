/*
 * Copyright (C) 2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.rising.view;

import android.os.SystemProperties;
import android.util.ArraySet;

/** @hide */
public class PopUpViewManager {

    public static final boolean FEATURE_SUPPORTED = SystemProperties.getBoolean(
        "ro.rising.feature.pop_up_view", true
    );

    /** TODO: Get rid of these dirty blacklist stuff. Maybe move them to local config file. */
    private static final ArraySet<String> SYSTEM_NOTIFICATION_BLACKLIST = new ArraySet<>();

    public static final String ACTION_START_MINI_WINDOW = "org.rising.intent.START_MINI_WINDOW";
    public static final String ACTION_START_PINNED_WINDOW = "org.rising.intent.START_PINNED_WINDOW";
    public static final String ACTION_PIN_CURRENT_APP = "org.rising.intent.PIN_CURRENT_APP";

    public static final String EXTRA_PACKAGE_NAME = "packageName";
    public static final String EXTRA_ACTIVITY_NAME = "activityName";
    public static final String EXTRA_SHORTCUT_ID = "shortcutId";
    public static final String EXTRA_SHORTCUT_USER_ID = "shortcutUserId";

    public static final int TAP_ACTION_PIN_WINDOW = 0;
    public static final int TAP_ACTION_EXIT = 1;
    public static final int TAP_ACTION_NOTHING = 2;

    private PopUpViewManager() {}

    static {
        SYSTEM_NOTIFICATION_BLACKLIST.add("android");
        SYSTEM_NOTIFICATION_BLACKLIST.add("com.android.chrome");
        SYSTEM_NOTIFICATION_BLACKLIST.add("com.android.packageinstaller");
        SYSTEM_NOTIFICATION_BLACKLIST.add("com.google.android.gms");
        SYSTEM_NOTIFICATION_BLACKLIST.add("com.google.android.packageinstaller");
    }

    public static boolean inSystemNotificationBlacklist(String packageName) {
        return SYSTEM_NOTIFICATION_BLACKLIST.contains(packageName);
    }
}
