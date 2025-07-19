
/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;

import static org.rising.DebugConstants.DEBUG_POP_UP;

import android.app.WindowConfiguration;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Slog;
import android.util.proto.ProtoOutputStream;
import android.view.DisplayInfo;
import android.view.InsetsState;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.WindowInsets;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.Transformation;
import android.view.animation.TranslateAnimation;

import java.io.PrintWriter;
import java.util.function.Supplier;

class WindowChangeAnimationSpecExt implements LocalAnimationAdapter.AnimationSpec {

    private static final String TAG = "WindowChangeAnimationSpecExt";

    static final int ANIMATION_DURATION_MODE_CHANGING = 320;

    private static final Interpolator INTERPOLATOR_MODE_CHANGING = new PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f);

    private final TaskWindowSurfaceInfo mStartInfo;
    private final TaskWindowSurfaceInfo mEndInfo;

    private final boolean mIsAppAnimation;
    private final boolean mIsThumbnail;

    private Animation mAnimation;
    private float mStartCornerRadius;
    private float mEndCornerRadius;

    private final ThreadLocal<TmpValues> mThreadLocalTmps =
            ThreadLocal.withInitial(() -> new TmpValues());
    private final Rect mTmpRect = new Rect();

    WindowChangeAnimationSpecExt(TaskWindowSurfaceInfo startInfo, TaskWindowSurfaceInfo endInfo,
            DisplayInfo displayInfo, float durationScale, boolean isAppAnimation, boolean isThumbnail) {
        mStartInfo = startInfo;
        mEndInfo = endInfo;
        mIsAppAnimation = isAppAnimation;
        mIsThumbnail = isThumbnail;
        createAnimationInner((int) (ANIMATION_DURATION_MODE_CHANGING * durationScale), displayInfo);
    }

    @Override
    public boolean getShowWallpaper() {
        return false;
    }

    @Override
    public long getDuration() {
        return mAnimation.getDuration();
    }

    private float getPositionAndScaleFormInfo(TaskWindowSurfaceInfo info, Point out) {
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "getPositionAndScaleFormInfo, info=" + info);
        }
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

    private void createAnimationInner(long duration, DisplayInfo displayInfo) {
        final Point startTrans = new Point();
        final float startScaleX = getPositionAndScaleFormInfo(mStartInfo, startTrans);
        mStartCornerRadius = getCornerRadiusFromInfo(mStartInfo);
        mEndCornerRadius = getCornerRadiusFromInfo(mEndInfo);
        final AnimationSet as = new AnimationSet(true);
        if (mIsThumbnail) {
            final Animation anim = new AlphaAnimation(0.0f, 0.0f);
            anim.setDuration(duration);
            as.addAnimation(anim);
            final float endScaleX = 1.0f / startScaleX;
            final float endScaleY = 1.0f / startScaleX;
            final Animation anim2 = new ScaleAnimation(endScaleX, endScaleX, endScaleY, endScaleY);
            anim2.setDuration(duration);
            as.addAnimation(anim2);
            mAnimation = as;
            mAnimation.initialize(0, 0, 0, 0);
            return;
        }
        final Point endTrans = new Point();
        final float endScaleX = getPositionAndScaleFormInfo(mEndInfo, endTrans);
        final Animation scaleAnim = new ScaleAnimation(startScaleX, endScaleX, startScaleX, endScaleX);
        scaleAnim.setDuration(duration);
        as.addAnimation(scaleAnim);
        final Animation translateAnim = new TranslateAnimation(startTrans.x, endTrans.x, startTrans.y, endTrans.y);
        translateAnim.setDuration(duration);
        as.addAnimation(translateAnim);
        mAnimation = as;
        mAnimation.setInterpolator(INTERPOLATOR_MODE_CHANGING);
        mAnimation.initialize(0, 0, displayInfo.appWidth, displayInfo.appHeight);
    }

    @Override
    public void apply(SurfaceControl.Transaction t, SurfaceControl leash, long currentPlayTime) {
        final TmpValues tmp = mThreadLocalTmps.get();
        if (mIsThumbnail) {
            mAnimation.getTransformation(currentPlayTime, tmp.mTransformation);
            t.setMatrix(leash, tmp.mTransformation.getMatrix(), tmp.mFloats);
            t.setAlpha(leash, tmp.mTransformation.getAlpha());
            return;
        }
        mAnimation.getTransformation(currentPlayTime, tmp.mTransformation);
        final Matrix matrix = tmp.mTransformation.getMatrix();
        t.setMatrix(leash, matrix, tmp.mFloats);
        if (mAnimation.getDuration() > 0) {
            final float normalizedTime = (float) (currentPlayTime -
                    (mAnimation.getStartTime() + mAnimation.getStartOffset())) /
                    (float) mAnimation.getDuration();
            final float interpolatedTime = mAnimation.getInterpolator().getInterpolation(normalizedTime);
            final float cornerRadius = mStartCornerRadius +
                    ((mEndCornerRadius - mStartCornerRadius) * interpolatedTime);
            t.setCornerRadius(leash, cornerRadius);
            t.setCornerRadius(mEndInfo.mTask.mSurfaceControl, cornerRadius);
            mEndInfo.mTask.getBounds(mTmpRect);
            t.setWindowCrop(leash, mTmpRect);
        }
    }

    @Override
    public long calculateStatusBarTransitionStartTime() {
        final long uptime = SystemClock.uptimeMillis();
        return Math.max(uptime, (long) (mAnimation.getDuration() * 0.99f + uptime - 120));
    }

    @Override
    public boolean needsEarlyWakeup() {
        return mIsAppAnimation;
    }

    @Override
    public void dump(PrintWriter pw, String prefix) {
    }

    @Override
    public void dumpDebugInner(ProtoOutputStream proto) {
    }

    private static class TmpValues {
        final float[] mFloats;
        final Transformation mTransformation;
        final float[] mVecs;

        private TmpValues() {
            mTransformation = new Transformation();
            mFloats = new float[9];
            mVecs = new float[4];
        }
    }
}
