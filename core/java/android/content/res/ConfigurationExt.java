/*
 * Copyright (C) 2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package android.content.res;

import android.app.ActivityThread;
import android.app.WindowConfiguration;

/** @hide */
class ConfigurationExt {

    private ConfigurationExt() {}

    static boolean diffWindowConfiguration(Configuration delta,
            WindowConfiguration windowConfiguration, boolean publicOnly) {
        return publicOnly && ActivityThread.isSystem() &&
                windowConfiguration.getWindowingMode() !=
                        delta.windowConfiguration.getWindowingMode() &&
                (windowConfiguration.isPopUpWindowMode() ||
                        delta.windowConfiguration.isPopUpWindowMode());
    }
}
