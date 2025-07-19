/*
 * Copyright (C) 2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package android.view;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import android.app.WindowConfiguration;
import android.app.WindowConfiguration.WindowingMode;
import android.graphics.Rect;

/** @hide */
class WindowLayoutExt {

    private WindowLayoutExt() {}

    static int computeCutoutMode(@WindowingMode int windowingMode, int originMode) {
        if (WindowConfiguration.isPopUpWindowMode(windowingMode)) {
            return LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        return originMode;
    }

    static void computeFrame(@WindowingMode int windowingMode,
            WindowManager.LayoutParams attrs,
            Rect outDisplayFrame, Rect outParentFrame, Rect windowBounds) {
        if (WindowConfiguration.isPopUpWindowMode(windowingMode)) {
            final int parentTop = outParentFrame.top;
            final int displayTop = outDisplayFrame.top;
            outParentFrame.set(windowBounds);
            outDisplayFrame.set(windowBounds);
            if (attrs.width == WRAP_CONTENT &&
                    (attrs.getFitInsetsSides() & WindowInsets.Type.statusBars()) != 0) {
                outParentFrame.top = parentTop;
                outDisplayFrame.top = displayTop;
            }
        }
    }
}
