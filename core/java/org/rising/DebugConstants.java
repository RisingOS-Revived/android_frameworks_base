/*
 * Copyright (C) 2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.rising;

import android.os.Build;
import android.os.SystemProperties;

import java.util.LinkedHashMap;

/** @hide */
public class DebugConstants {

    private DebugConstants() {}

    public static LinkedHashMap<String, String> CONSTANTS_MAP = new LinkedHashMap<>();

    static {
        CONSTANTS_MAP.put("DEBUG_GLOBAL", "persist.sys.rising.debug.global");
        CONSTANTS_MAP.put("DEBUG_POP_UP", "persist.sys.rising.popup.debug");
        CONSTANTS_MAP.put("DEBUG_WMS_RESOLUTION", "persist.sys.rising.wm.resolution.debug");
        CONSTANTS_MAP.put("DEBUG_WMS_TOP_APP", "persist.sys.rising.wm.top_app.debug");
    }

    // Enable this to debug all rising features
    private static final boolean DEBUG_GLOBAL = Build.IS_ENG || SystemProperties.getBoolean(
        "persist.sys.rising.debug.global", false
    );

    // Enable this to debug Pop-Up View feature
    public static final boolean DEBUG_POP_UP = DEBUG_GLOBAL || SystemProperties.getBoolean(
        "persist.sys.rising.popup.debug", false
    );

    // Enable this to debug resolution switch feature
    // Package: com.android.server.wm.DisplayResolutionController
    // Key: DisplayResolutionController
    public static final boolean DEBUG_WMS_RESOLUTION = DEBUG_GLOBAL || SystemProperties.getBoolean(
        "persist.sys.rising.wm.resolution.debug", false
    );

    // Enable this to debug top activity change
    // Package: com.android.server.wm.TopActivityRecorder
    // Key: TopActivityRecorder
    public static final boolean DEBUG_WMS_TOP_APP = DEBUG_GLOBAL || SystemProperties.getBoolean(
        "persist.sys.rising.wm.top_app.debug", false
    );
}
