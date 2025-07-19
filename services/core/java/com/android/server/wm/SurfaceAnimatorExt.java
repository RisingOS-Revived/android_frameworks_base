/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;

import static org.rising.DebugConstants.DEBUG_POP_UP;

import android.app.WindowConfiguration;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Slog;
import android.view.InsetsState;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.WindowInsets;

import java.util.function.Supplier;

class SurfaceAnimatorExt {

    private static final String TAG = "SurfaceAnimatorExt";

    static SurfaceControl createAnimationLeash(SurfaceAnimator.Animatable animatable, 
            SurfaceControl surface, SurfaceControl.Transaction t,
            int type, int width, int height, boolean hidden,
            Supplier<SurfaceControl.Transaction> transactionFactory,
            TaskWindowSurfaceInfo taskWindowSurfaceInfo) {
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "Reparenting to leash for " + animatable + ", taskWindowSurfaceInfo=" + taskWindowSurfaceInfo);
        }
        final SurfaceControl.Builder builder = animatable.makeAnimationLeash()
                .setParent(animatable.getAnimationLeashParent())
                .setName(surface + " - animation-leash of " + SurfaceAnimator.animationTypeToString(type))
                .setHidden(hidden)
                .setEffectLayer()
                .setCallsite("SurfaceAnimatorExt.createAnimationLeash");
        final SurfaceControl leash = builder.build();
        final Point pos = new Point();
        final float scale = getPositionAndScaleFormInfo(taskWindowSurfaceInfo, pos);
        final float cornerRadius = getCornerRadiusFromInfo(taskWindowSurfaceInfo);
        t.setWindowCrop(leash, width, height);
        t.setPosition(leash, pos.x, pos.y);
        t.setScale(leash, scale, scale);
        t.setCornerRadius(leash, cornerRadius);
        t.setCornerRadius(surface, cornerRadius);
        t.show(leash);
        t.setAlpha(leash, hidden ? 0.0f : 1.0f);
        t.reparent(surface, leash);
        return leash;
    }

    private static float getPositionAndScaleFormInfo(TaskWindowSurfaceInfo info, Point out) {
        final Task task = info.mTask;
        final int windowingMode = info.mFreezedWindowingMode != WINDOWING_MODE_UNDEFINED ?
                info.mFreezedWindowingMode :
                task.getConfiguration().windowConfiguration.getWindowingMode();
        if (WindowConfiguration.isPinnedExtWindowMode(windowingMode)) {
            final Rect bounds = new Rect();
            task.getBounds(bounds);
            final Rect displayBound = new Rect();
            if (task.mDisplayContent != null) {
                final InsetsState state = task.mDisplayContent.getInsetsStateController().getRawInsetsState();
                displayBound.set(state.getDisplayFrame());
                displayBound.inset(state.calculateInsets(displayBound,
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout(), true));
            }
            final Point pos = new Point();
            WindowResizingAlgorithm.getCenterByBoundaryGap(
                    bounds, displayBound, info.getWindowBoundaryGap(),
                    info.getPinnedWindowVerticalPosRatio(displayBound),
                    info.getWindowCenterPosition(), info.getWindowSurfaceScale(), pos);
            info.setWindowCenterPosition(pos);
        }
        if (WindowConfiguration.isPopUpWindowMode(windowingMode)) {
            final Rect displayBound = new Rect();
            if (task.mDisplayContent != null) {
                task.mDisplayContent.getBounds(displayBound);
            }
            final Rect bound = task.getBounds();
            info.setWindowSurfaceScaleFactor(
                    WindowResizingAlgorithm.getPositionAndScaleFactorForTask(
                            bound, displayBound, info.getWindowCenterPosition(),
                            info.getWindowSurfaceScale(),
                            WindowConfiguration.isPinnedExtWindowMode(windowingMode), out));
            return info.getWindowSurfaceRealScale();
        }
        return 1.0f;
    }

    private static float getCornerRadiusFromInfo(TaskWindowSurfaceInfo info) {
        final Task task = info.mTask;
        final int windowingMode = info.mFreezedWindowingMode != WINDOWING_MODE_UNDEFINED ?
                info.mFreezedWindowingMode :
                task.getConfiguration().windowConfiguration.getWindowingMode();
        if (WindowConfiguration.isPopUpWindowMode(windowingMode)) {
            return info.getCornerRadius();
        }
        return 0.0f;
    }
}
