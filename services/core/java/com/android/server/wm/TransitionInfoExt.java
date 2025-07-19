/*
 * Copyright (C) 2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;

import static org.rising.DebugConstants.DEBUG_POP_UP;

import android.app.WindowConfiguration;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Slog;
import android.view.DisplayInfo;
import android.view.InsetsState;
import android.view.WindowInsets;

import com.android.server.wm.Transition.ChangeInfo.PopUpViewInfo;

public class TransitionInfoExt {

    private static final String TAG = "TransitionInfoExt";

    private final PopUpViewInfo mPopUpViewInfo = new PopUpViewInfo();

    private float getPositionAndScaleFormInfo(TaskWindowSurfaceInfo info, Point out) {
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "getPositionAndScaleFormInfo, info=" + info);
        }
        out.set(0, 0);
        final Task task = info.mTask;
        final int windowingMode = info.mFreezedWindowingMode != WINDOWING_MODE_UNDEFINED
                ? info.mFreezedWindowingMode
                : task.getConfiguration().windowConfiguration.getWindowingMode();
        if (WindowConfiguration.isPinnedExtWindowMode(windowingMode)) {
            final Rect bounds = new Rect();
            task.getBounds(bounds);
            final Rect innerBound = new Rect();
            if (task.mDisplayContent != null) {
                final InsetsState state = task.mDisplayContent.getInsetsStateController().getRawInsetsState();
                innerBound.set(state.getDisplayFrame());
                innerBound.inset(state.calculateInsets(innerBound,
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout(), true));
            }
            final Point pos = new Point();
            WindowResizingAlgorithm.getCenterByBoundaryGap(bounds, innerBound,
                    info.getWindowBoundaryGap(), info.getPinnedWindowVerticalPosRatio(innerBound),
                    info.getWindowCenterPosition(), info.getWindowSurfaceScale(), pos);
            info.setWindowCenterPosition(pos);
        }

        if (WindowConfiguration.isPopUpWindowMode(windowingMode)) {
            final Rect displayBound = new Rect();
            if (task.mDisplayContent != null) {
                task.mDisplayContent.getBounds(displayBound);
            }
            final Rect bound = task.getBounds();
            info.setWindowSurfaceScaleFactor(WindowResizingAlgorithm.getPositionAndScaleFactorForTask(
                    bound, displayBound, info.getWindowCenterPosition(), info.getWindowSurfaceScale(),
                    WindowConfiguration.isPinnedExtWindowMode(windowingMode), out));
            return info.getWindowSurfaceRealScale();
        }
        return 1.0f;
    }

    private float getCornerRadiusFromInfo(TaskWindowSurfaceInfo info) {
        final int windowingMode = info.mFreezedWindowingMode != 0 ? info.mFreezedWindowingMode
                : info.mTask.getConfiguration().windowConfiguration.getWindowingMode();
        if (WindowConfiguration.isPopUpWindowMode(windowingMode)) {
            return info.getCornerRadius();
        }
        return 0.0f;
    }

    PopUpViewInfo getPopUpViewInfo() {
        return mPopUpViewInfo;
    }

    void setupPopUpViewInfo(TaskWindowSurfaceInfo freezeInfo, TaskWindowSurfaceInfo info, DisplayInfo displayInfo) {
        mPopUpViewInfo.mStartScale = getPositionAndScaleFormInfo(freezeInfo, mPopUpViewInfo.mStartPos);
        mPopUpViewInfo.mEndScale = getPositionAndScaleFormInfo(info, mPopUpViewInfo.mEndPos);
        mPopUpViewInfo.mStartCornerRadius = getCornerRadiusFromInfo(freezeInfo);
        mPopUpViewInfo.mEndCornerRadius = getCornerRadiusFromInfo(info);
        mPopUpViewInfo.mAppBounds.set(0, 0, displayInfo.appWidth, displayInfo.appHeight);
        if (info.mTask != null) {
            info.mTask.getBounds(mPopUpViewInfo.mWindowCrop);

            try {
                java.lang.reflect.Field freezerField = info.mTask.getClass().getDeclaredField("mSurfaceFreezer");
                freezerField.setAccessible(true);
                Object surfaceFreezer = freezerField.get(info.mTask);

                if (surfaceFreezer != null) {
                    java.lang.reflect.Field boundsField = surfaceFreezer.getClass().getDeclaredField("mFreezeBounds");
                    boundsField.setAccessible(true);
                    Rect freezeBounds = (Rect) boundsField.get(surfaceFreezer);

                    if (freezeBounds != null) {
                        mPopUpViewInfo.mStartDragBounds.set(freezeBounds);
                    } else {
                        info.mTask.getBounds(mPopUpViewInfo.mStartDragBounds);
                    }
                } else {
                    info.mTask.getBounds(mPopUpViewInfo.mStartDragBounds);
                }
            } catch (Exception e) {
                if (DEBUG_POP_UP) {
                    Slog.w(TAG, "Unable to access surface freezer bounds, using task bounds as fallback", e);
                }
                info.mTask.getBounds(mPopUpViewInfo.mStartDragBounds);
            }
        }
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "setupPopUpViewInfo, info=" + mPopUpViewInfo);
        }
    }
}
