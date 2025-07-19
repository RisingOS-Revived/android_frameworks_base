/*
 * Copyright (C) 2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package android.view;

import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.view.WindowManager.LayoutParams.TYPE_CHANGED;

import static org.rising.DebugConstants.DEBUG_POP_UP;

import android.app.WindowConfiguration;
import android.content.res.Configuration;
import android.util.Slog;

/** @hide */
class ViewRootImplExt {

    private static final String TAG = "ViewRootImplExt";
    private static final String PKG_GMS = "com.google.android.gms";

    private final ViewRootImpl mImpl;
    private int mLastWindowingMode = WINDOWING_MODE_UNDEFINED;
    private float[] mPopUpViewOffsets = new float[4];

    ViewRootImplExt(ViewRootImpl impl) {
        mImpl = impl;
    }

    void handleAppVisibility(boolean visible) {
        if (WindowConfiguration.isPinnedExtWindowMode(mLastWindowingMode) &&
                !PKG_GMS.equals(mImpl.mBasePackageName) &&
                !mImpl.mUpcomingWindowFocus && visible) {
            mImpl.windowFocusChanged(false);
            mImpl.windowFocusChanged(true);
        }
    }

    void performConfigurationChange(Configuration overrideConfig) {
        final int windowingMode = overrideConfig.windowConfiguration.getWindowingMode();
        final boolean isPinnedWindow = WindowConfiguration.isPinnedExtWindowMode(windowingMode);
        if (isPinnedWindow && mLastWindowingMode != windowingMode) {
            final boolean isExcludedWindow = PKG_GMS.equals(mImpl.mBasePackageName)
                    && mImpl.mWindowAttributes.getTitle().toString().isEmpty()
                    && mImpl.mWindowAttributes.type == TYPE_CHANGED;
            if (!mImpl.mUpcomingWindowFocus && mImpl.mAppVisible && !isExcludedWindow) {
                mImpl.windowFocusChanged(false);
                mImpl.windowFocusChanged(true);
            }
        }
        mLastWindowingMode = overrideConfig.windowConfiguration.getWindowingMode();
        if (WindowConfiguration.isMiniExtWindowMode(mLastWindowingMode)) {
            try {
                mImpl.mWindowSession.getPopUpViewTouchOffset(mImpl.mWindow, mPopUpViewOffsets);
            } catch (Exception e) {
                Slog.e(TAG, "Unable get Pop-Up View Touch Offset");
            }
        } else {
            mPopUpViewOffsets[0] = 0.0f;
            mPopUpViewOffsets[1] = 0.0f;
            mPopUpViewOffsets[2] = 1.0f;
            mPopUpViewOffsets[3] = 1.0f;
        }
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "mPopUpViewOffsets: offset=(" + mPopUpViewOffsets[0] + ", " +
                    mPopUpViewOffsets[1] + "), scale=(" + mPopUpViewOffsets[2] + ", " +
                    mPopUpViewOffsets[3] + ")");
        }
    }

    void onMotionEvent(MotionEvent event) {
        if (WindowConfiguration.isMiniExtWindowMode(mLastWindowingMode)) {
            event.mEventExt.setRawDisplayOffset(mPopUpViewOffsets[0], mPopUpViewOffsets[1]);
            event.mEventExt.setRawDisplayScale(mPopUpViewOffsets[2], mPopUpViewOffsets[3]);
        } else {
            event.mEventExt.setRawDisplayOffset(0.0f, 0.0f);
            event.mEventExt.setRawDisplayScale(1.0f, 1.0f);
        }
    }
}
