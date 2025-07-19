/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

import static com.android.server.wm.WindowResizingAlgorithm.BOUNDARY_GAP;
import static com.android.server.wm.WindowResizingAlgorithm.PINNED_WINDOW_SCALE_LARGE_LAND;
import static com.android.server.wm.WindowResizingAlgorithm.PINNED_WINDOW_SCALE_LARGE_PORT;
import static com.android.server.wm.WindowResizingAlgorithm.PINNED_WINDOW_SCALE_SMALL_LAND;
import static com.android.server.wm.WindowResizingAlgorithm.PINNED_WINDOW_SCALE_SMALL_PORT;

import static org.rising.DebugConstants.DEBUG_POP_UP;

import android.app.WindowConfiguration;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.Slog;
import android.view.DisplayInfo;
import android.view.IWindow;
import android.view.InsetsState;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.WindowInsets;

import com.android.internal.R;

import com.android.server.wm.Transition.ChangeInfo.PopUpViewInfo;

import java.util.Arrays;
import java.util.List;

class TaskWindowSurfaceInfo {

    private static final String TAG = "TaskWindowSurfaceInfo";

    private static final int DENSITY_DEFAULT = 420;

    private final List<String> mForceUpdateDpiList;

    private final PopUpAnimationController mPopUpAnimationController;

    private final Configuration mConfiguration = new Configuration();
    private final TransitionInfoExt mTransitionInfoExt = new TransitionInfoExt();
    private final Point mWindowCenterPosition;
    private final PointF mPinnedWindowVerticalPosRatio;
    private final Rect mWindowBoundaryGap;

    private float mCornerRadius;
    private float mPinnedWindowCornerRadius;
    private float mMiniWindowCornerRadius;

    private boolean mIsPinnedWindowSmall = true;
    private boolean mMute = false;

    private float mWindowSurfaceScale;
    private float mWindowSurfaceScaleFactor;

    final WindowManagerService mService;
    final Task mTask;

    int mFreezedWindowingMode = WINDOWING_MODE_UNDEFINED;

    TaskWindowSurfaceInfo(Task task) {
        mTask = task;
        mService = task.mWmService;

        mConfiguration.setTo(mTask.getConfiguration());

        mForceUpdateDpiList = Arrays.asList(mService.mContext.getResources()
                .getStringArray(R.array.config_popUpView_forceUpdateDpiPackages));

        mWindowCenterPosition = new Point();
        setWindowSurfaceScale(
                mTask.getConfiguration().orientation == ORIENTATION_PORTRAIT ?
                PINNED_WINDOW_SCALE_SMALL_PORT : PINNED_WINDOW_SCALE_SMALL_LAND);
        mWindowSurfaceScaleFactor = 1.0f;
        mWindowBoundaryGap = new Rect(0, BOUNDARY_GAP, BOUNDARY_GAP, 0);

        mMiniWindowCornerRadius = mService.mContext.getResources()
                .getDimensionPixelSize(R.dimen.mini_window_corner_radius);
        mPinnedWindowCornerRadius = mService.mContext.getResources()
                .getDimensionPixelSize(R.dimen.pinned_window_corner_radius);
        mCornerRadius = mPinnedWindowCornerRadius;

        mPinnedWindowVerticalPosRatio = new PointF(0.0f, 0.0f);
        mPopUpAnimationController = new PopUpAnimationController(mService);
        mPopUpAnimationController.setTask(task);
    }

    TaskWindowSurfaceInfo(TaskWindowSurfaceInfo other, int preFreezedWindowingMode) {
        mTask = other.mTask;
        mService = other.mService;

        mFreezedWindowingMode = preFreezedWindowingMode;
        mConfiguration.setTo(mTask.getConfiguration());
        mMute = other.getMute();

        mForceUpdateDpiList = Arrays.asList(mService.mContext.getResources()
                .getStringArray(R.array.config_popUpView_forceUpdateDpiPackages));

        mWindowCenterPosition = other.getWindowCenterPosition();
        mWindowSurfaceScale = other.getWindowSurfaceScale();
        mWindowSurfaceScaleFactor = other.getWindowSurfaceScaleFactor();
        mWindowBoundaryGap = other.getWindowBoundaryGap();

        mCornerRadius = other.getCornerRadius();

        mPinnedWindowVerticalPosRatio = other.getPinnedWindowVerticalPosRatioPointF();
        mPopUpAnimationController = new PopUpAnimationController(mService);
        mPopUpAnimationController.setTask(mTask);
    }

    void toggleMute() {
        mMute = !mMute;
    }

    boolean getMute() {
        return mMute;
    }

    boolean isPinnedWindowSmall() {
        return mIsPinnedWindowSmall;
    }

    void setWindowCenterPosition(Point pos) {
        mWindowCenterPosition.set(pos.x, pos.y);
    }

    Point getWindowCenterPosition() {
        return new Point(mWindowCenterPosition.x, mWindowCenterPosition.y);
    }

    PopUpViewInfo getPopUpViewInfo() {
        return mTransitionInfoExt.getPopUpViewInfo();
    }

    void setWindowSurfaceScaleDrag(float scale, Rect displayBound, boolean isLandscape) {
        if (mWindowSurfaceScale != scale) {
            mWindowSurfaceScale = scale;
            DimmerWindow.getInstance().onDragResizeChanged(scale,
                    getTaskWindowSurfaceBoundsOnDrag(displayBound), isLandscape);
        }
    }

    void setWindowSurfaceScale(float scale) {
        if (scale == PINNED_WINDOW_SCALE_SMALL_PORT || scale == PINNED_WINDOW_SCALE_SMALL_LAND) {
            mIsPinnedWindowSmall = true;
        } else if (scale == PINNED_WINDOW_SCALE_LARGE_PORT || scale == PINNED_WINDOW_SCALE_LARGE_LAND) {
            mIsPinnedWindowSmall = false;
        }
        if (mWindowSurfaceScale != scale) {
            mWindowSurfaceScale = scale;
            DimmerWindow.getInstance().onResizeChanged();
        }
    }

    float getWindowSurfaceScale() {
        return mWindowSurfaceScale;
    }

    float getWindowSurfaceRealScale() {
        return mWindowSurfaceScale * mWindowSurfaceScaleFactor;
    }

    float getWindowSurfaceRealScale(float scale) {
        return scale * mWindowSurfaceScaleFactor;
    }

    void setWindowSurfaceScaleFactor(float factor) {
        mWindowSurfaceScaleFactor = factor;
    }

    float getWindowSurfaceScaleFactor() {
        return mWindowSurfaceScaleFactor;
    }

    void resetWindowBoundaryGap() {
        mWindowBoundaryGap.setEmpty();
    }

    float getCornerRadius() {
        return mCornerRadius;
    }

    void setPinnedWindowVerticalPosRatio(Point pos, Rect displayBound, boolean isDrag) {
        if (displayBound != null && displayBound.height() > 0) {
            final float ratio = (float) pos.y / displayBound.height();
            if (isDrag) {
                mPinnedWindowVerticalPosRatio.set(ratio, ratio);
            } else if (displayBound.height() > displayBound.width()) {
                mPinnedWindowVerticalPosRatio.x = ratio;
            } else {
                mPinnedWindowVerticalPosRatio.y = ratio;
            }
        }
    }

    float getPinnedWindowVerticalPosRatio(Rect displayBound) {
        return displayBound.height() > displayBound.width() ?
                mPinnedWindowVerticalPosRatio.x : mPinnedWindowVerticalPosRatio.y;
    }

    private PointF getPinnedWindowVerticalPosRatioPointF() {
        return new PointF(mPinnedWindowVerticalPosRatio.x, mPinnedWindowVerticalPosRatio.y);
    }

    void resetWindowBoundaryGapToOrigin() {
        mWindowBoundaryGap.set(0, BOUNDARY_GAP, BOUNDARY_GAP, 0);
    }

    void setWindowBoundaryGap(int left, int top, int right, int bottom) {
        if (left > 0) {
            mWindowBoundaryGap.left = left;
        }
        if (top > 0) {
            mWindowBoundaryGap.top = top;
        }
        if (right > 0) {
            mWindowBoundaryGap.right = right;
        }
        if (bottom > 0) {
            mWindowBoundaryGap.bottom = bottom;
        }
    }

    Rect getWindowBoundaryGap() {
        return new Rect(mWindowBoundaryGap.left, mWindowBoundaryGap.top,
                mWindowBoundaryGap.right, mWindowBoundaryGap.bottom);
    }

    Rect getTaskWindowSurfaceBounds() {
        int windowingMode = mFreezedWindowingMode;
        if (windowingMode == WINDOWING_MODE_UNDEFINED) {
            windowingMode = mTask.getConfiguration().windowConfiguration.getWindowingMode();
        }
        final Rect result = new Rect();
        if (WindowConfiguration.isPinnedExtWindowMode(windowingMode)) {
            final Rect bounds = new Rect();
            mTask.getBounds(bounds);
            final Rect displayBound = new Rect();
            if (mTask.mDisplayContent != null) {
                final InsetsState state = mTask.mDisplayContent.getInsetsStateController().getRawInsetsState();
                displayBound.set(state.getDisplayFrame());
                displayBound.inset(state.calculateInsets(displayBound,
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout(), true));
            }
            final Point pos = new Point();
            WindowResizingAlgorithm.getCenterByBoundaryGap(
                    bounds, displayBound, getWindowBoundaryGap(),
                    getPinnedWindowVerticalPosRatio(displayBound),
                    getWindowCenterPosition(), getWindowSurfaceScale(), pos);
            setWindowCenterPosition(pos);
        }
        if (WindowConfiguration.isPopUpWindowMode(windowingMode)) {
            final Rect displayBound = new Rect();
            if (mTask.mDisplayContent != null) {
                mTask.mDisplayContent.getBounds(displayBound);
            }
            final Point pos = new Point();
            final Rect bound = mTask.getBounds();
            mWindowSurfaceScaleFactor = WindowResizingAlgorithm.getPositionAndScaleFactorForTask(
                    bound, displayBound, mWindowCenterPosition, mWindowSurfaceScale,
                    WindowConfiguration.isPinnedExtWindowMode(windowingMode), pos);
            result.set(0, 0, bound.width(), bound.height());
            result.scale(getWindowSurfaceRealScale());
            result.offsetTo(pos.x, pos.y);
        }
        return result;
    }

    Rect getTaskWindowSurfaceBoundsOnDrag(Rect displayBound) {
        final Rect result = new Rect();
        final Point pos = new Point();
        final Rect bound = mTask.getBounds();
        mWindowSurfaceScaleFactor = WindowResizingAlgorithm.getPositionAndScaleFactorForTask(
                bound, displayBound, mWindowCenterPosition, mWindowSurfaceScale,
                false, pos);
        result.set(0, 0, bound.width(), bound.height());
        result.scale(getWindowSurfaceRealScale());
        result.offsetTo(pos.x, pos.y);
        return result;
    }

    void onWindowingModeChanged(int preWindowMode) {
        final WindowConfiguration winConfig = mTask.getConfiguration().windowConfiguration;
        final boolean isMiniWindow = winConfig.isMiniExtWindowMode();
        final boolean isPinnedWindow = winConfig.isPinnedExtWindowMode();
        final boolean isPopUpWindow = winConfig.isPopUpWindowMode();
        final boolean isPrevMiniWindow = WindowConfiguration.isMiniExtWindowMode(preWindowMode);
        final boolean isPrevPinnedWindow = WindowConfiguration.isPinnedExtWindowMode(preWindowMode);
        final boolean isPrevPopUpWindow = WindowConfiguration.isPopUpWindowMode(preWindowMode);
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "onWindowingModeChanged " + preWindowMode + "->"
                    + winConfig.getWindowingMode() + " mTask=" + mTask);
        }
        if (!isPopUpWindow && isPrevPopUpWindow) {
            final IWindow window = getIWindow();
            if (window != null) {
                finishTaskPositioning(window);
            }
            if (cancelPopUpViewAnimation()) {
                if (DEBUG_POP_UP) {
                    Slog.d(TAG, "cancel PopUpViewAnimation when exit PopupView. mTask=" + mTask);
                }
            }
        }
        if (isMiniWindow) {
            final Rect displayBound = new Rect();
            if (mTask.mDisplayContent != null) {
                mTask.mDisplayContent.getBounds(displayBound);
                final Point pos = new Point(displayBound.width() / 2, displayBound.height() / 2);
                setWindowSurfaceScale(WindowResizingAlgorithm.getDefaultMiniWindowScale(
                        mTask.getConfiguration().orientation, mTask.mDisplayContent.getRotation()));
                setWindowCenterPosition(pos);
            }
            mCornerRadius = mMiniWindowCornerRadius;
        } else if (isPinnedWindow) {
            setWindowSurfaceScale(WindowResizingAlgorithm.getDefaultPinnedWindowScale(
                    mTask.getConfiguration().orientation, isPinnedWindowSmall()));
            mCornerRadius = mPinnedWindowCornerRadius;
        }
        final boolean shouldMuteInMiniWindow = PopUpSettingsConfig.getInstance().shouldMuteInMiniWindow();
        if (isPrevPinnedWindow && isMiniWindow && !shouldMuteInMiniWindow) {
            PinnedWindowOverlayController.getInstance().triggerTmpWindowMute(mTask, false);
        } else if (isPrevMiniWindow && isPinnedWindow && !shouldMuteInMiniWindow && getMute()) {
            PinnedWindowOverlayController.getInstance().triggerTmpWindowMute(mTask, true);
        } else if (isPrevPopUpWindow && !isPopUpWindow && getMute()) {
            PinnedWindowOverlayController.getInstance().triggerPinnedWindowMute(mTask);
        }
        if (isPrevPinnedWindow && !isPopUpWindow) {
            mIsPinnedWindowSmall = true;
        }
        if (isPopUpWindow || isPrevPopUpWindow) {
            final SurfaceControl surfaceControl = mTask.getSurfaceControl();
            if (surfaceControl != null && surfaceControl.isValid()) {
                mService.mTransactionFactory.get().setTrustedOverlay(surfaceControl, isPopUpWindow).apply();
            }
            updateDensityIfNeed(isPrevPopUpWindow && !isPopUpWindow);
        }
    }

    private void updateDensityIfNeed(boolean isExitPopUpView) {
        if (mTask.getWindowConfiguration().isPopUpWindowMode()) {
            final int initDensity = mTask.mDisplayContent == null ?
                    DENSITY_DEFAULT : mTask.mDisplayContent.mInitialDisplayDensity;
            if (mTask.getConfiguration().densityDpi < initDensity) {
                final ActivityRecord topActivity = mTask.topRunningActivityLocked();
                if (topActivity == null || !mForceUpdateDpiList.contains(topActivity.packageName)) {
                    return;
                }
                if (DEBUG_POP_UP) {
                    Slog.d(TAG, "force update density for " + mTask +
                            " densityDpi=" + mTask.getConfiguration().densityDpi);
                }
                mTask.getRequestedOverrideConfiguration().densityDpi = initDensity;
                mTask.onRequestedOverrideConfigurationChanged(mTask.getRequestedOverrideConfiguration());
            }
        } else if (isExitPopUpView && mTask.getRequestedOverrideConfiguration().densityDpi != 0) {
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "reset density for " + mTask +
                        " densityDpi=" + mTask.getRequestedOverrideConfiguration().densityDpi);
            }
            mTask.getRequestedOverrideConfiguration().densityDpi = 0;
            mTask.onRequestedOverrideConfigurationChanged(mTask.getRequestedOverrideConfiguration());
        }
    }

    void onRotationChanged() {
        if (mTask.getConfiguration().windowConfiguration.isMiniExtWindowMode()
                && mTask.mDisplayContent != null) {
            final Rect displayBound = new Rect();
            mTask.mDisplayContent.getBounds(displayBound);
            final Point pos = new Point(displayBound.width() / 2, displayBound.height() / 2);
            setWindowSurfaceScale(WindowResizingAlgorithm.getDefaultMiniWindowScale(
                    mTask.getConfiguration().orientation, mTask.mDisplayContent.getRotation()));
            setWindowCenterPosition(pos);
        }
    }

    void onConfigurationChanged() {
        final Configuration newConfig = mTask.getConfiguration();
        final long diff = mConfiguration.windowConfiguration.diff(newConfig.windowConfiguration, false);
        if ((WindowConfiguration.WINDOW_CONFIG_BOUNDS & diff) != 0
                || (WindowConfiguration.WINDOW_CONFIG_ROTATION & diff) != 0) {
            if (mTask.getWindowConfiguration().isMiniExtWindowMode()
                    && mTask.mDisplayContent != null) {
                setWindowSurfaceScale(WindowResizingAlgorithm.getDefaultMiniWindowScale(
                        mTask.getConfiguration().orientation, mTask.mDisplayContent.getRotation()));
            }
            DimmerWindow.getInstance().onResizeChanged();
        }
        if ((mConfiguration.diff(newConfig) & ActivityInfo.CONFIG_DENSITY) != 0) {
            updateDensityIfNeed(false);
        }
        mConfiguration.setTo(newConfig);
    }

    void onPrepareSurfaces(SurfaceControl.Transaction t) {
        final WindowConfiguration winConfig = mTask.getConfiguration().windowConfiguration;
        if (winConfig.isPinnedExtWindowMode() && !mPopUpAnimationController.isAnimating()
                && !mTask.mTransitionController.inPlayingTransition(mTask)
                && !isWindowPositioningLocked()) {
            final Rect surfaceBounds = getTaskWindowSurfaceBounds();
            final Rect bounds = new Rect();
            mTask.getBounds(bounds);
            final Rect displayBound = new Rect();
            if (mTask.mDisplayContent != null) {
                final InsetsState state = mTask.mDisplayContent.getInsetsStateController().getRawInsetsState();
                displayBound.set(state.getDisplayFrame());
                displayBound.inset(state.calculateInsets(displayBound,
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout(), true));
            }
            final Rect boundaryGap = getWindowBoundaryGap();
            boundaryGap.top = surfaceBounds.top <= displayBound.top + BOUNDARY_GAP ? BOUNDARY_GAP : 0;
            boundaryGap.bottom = surfaceBounds.bottom >= displayBound.bottom - BOUNDARY_GAP ? BOUNDARY_GAP : 0;
            final Point pos = new Point();
            WindowResizingAlgorithm.getCenterByBoundaryGap(
                    bounds, displayBound, boundaryGap,
                    getPinnedWindowVerticalPosRatio(displayBound),
                    getWindowCenterPosition(), getWindowSurfaceScale(), pos);
            setWindowCenterPosition(pos);
            setPinnedWindowVerticalPosRatio(getWindowCenterPosition(), displayBound, false);
        }
        final boolean hasAnimationLeash = hasTaskSurfaceAnimationLeash() ||
                mPopUpAnimationController.isAnimating() ||
                mTask.mTransitionController.isPlaying();
        if (winConfig.isPopUpWindowMode() && !hasAnimationLeash &&
                !isWindowPositioningLocked()) {
            final Rect displayBound = new Rect();
            if (mTask.mDisplayContent != null) {
                mTask.mDisplayContent.getBounds(displayBound);
            }
            final Point pos = new Point();
            final Rect bound = mTask.getBounds();
            mWindowSurfaceScaleFactor = WindowResizingAlgorithm.getPositionAndScaleFactorForTask(
                    bound, displayBound, mWindowCenterPosition,
                    mWindowSurfaceScale, winConfig.isPinnedExtWindowMode(), pos);
            t.setPosition(mTask.mSurfaceControl, pos.x, pos.y)
                    .setWindowCrop(mTask.mSurfaceControl, bound.width(), bound.height())
                    .setCornerRadius(mTask.mSurfaceControl, mCornerRadius)
                    .setScale(mTask.mSurfaceControl, getWindowSurfaceRealScale(), getWindowSurfaceRealScale());
            if (mTask.mLastSurfaceShowing) {
                t.show(mTask.mSurfaceControl);
            }
        }
        if (winConfig.isPinnedExtWindowMode() &&
                PinnedWindowOverlayController.getInstance().isOverlayViewShowing()) {
            PinnedWindowOverlayController.getInstance().updateWindowState(true);
        }
    }

    void scheduleTransition(TaskWindowSurfaceInfo freezeTaskWindowSurfaceInfo, DisplayInfo displayInfo) {
        mTransitionInfoExt.setupPopUpViewInfo(freezeTaskWindowSurfaceInfo, this, displayInfo);
    }

    boolean isCrossOverAnimating() {
        return mPopUpAnimationController.isCrossOverAnimating();
    }

    void playExitAnimation(boolean isFromLeaveButton, float startScale,
            PopUpAnimationController.OnAnimationEndCallback callback) {
        mPopUpAnimationController.playExitAnimation(
                isFromLeaveButton, startScale, callback);
    }

    void resizeWindowWithAnimation(Point startPos, Point endPos, int boundWidth,
            int boundHeight, float startWinScale, float endWinScale,
            Rect displayBound, boolean isLandscape) {
        mPopUpAnimationController.playResizeAnimation(startPos, endPos,
                boundWidth, boundHeight, startWinScale, endWinScale,
                displayBound, isLandscape, this);
    }

    void playToggleResizeWindowAnimation(Point startPos, Point endPos, float startWinScale,
            float endWinScale, PopUpAnimationController.OnAnimationEndCallback callback) {
        mPopUpAnimationController.playToggleResizeWindowAnimation(
                startPos, endPos, startWinScale, endWinScale, callback);
    }

    void flingWindowToEdge(Point startPos, Point endPos, int boundWidth, int boundHeight,
            float winScale, float velX, float velY) {
        mPopUpAnimationController.playSpringAnimation(
                startPos, endPos, boundWidth, boundHeight, winScale, velX, velY);
    }

    boolean cancelPopUpViewAnimation() {
        return mPopUpAnimationController.cancelAnimation();
    }

    private void finishTaskPositioning(IWindow window) {
        try {
            if (mService != null && window != null) {
                synchronized (mService.mGlobalLock) {
                }
            }
        } catch (Exception e) {
            if (DEBUG_POP_UP) {
                Slog.w(TAG, "Failed to finish task positioning", e);
            }
        }
    }

    private boolean isWindowPositioningLocked() {
        try {
            synchronized (mService.mGlobalLock) {
                return false;
            }
        } catch (Exception e) {
            if (DEBUG_POP_UP) {
                Slog.w(TAG, "Failed to check window positioning lock state", e);
            }
            return false;
        }
    }

    private boolean hasTaskSurfaceAnimationLeash() {
        try {
            if (mTask.mSurfaceAnimator != null) {
                return mTask.mSurfaceAnimator.hasLeash();
            }
            return false;
        } catch (Exception e) {
            if (DEBUG_POP_UP) {
                Slog.w(TAG, "Failed to check animation leash state", e);
            }
            return false;
        }
    }

    private IWindow getIWindow() {
        synchronized (mService.mAtmService.mGlobalLock) {
            if (mTask != null && mTask.getTopVisibleAppMainWindow() != null) {
                final IWindow iWindow = mTask.getTopVisibleAppMainWindow().getIWindow();
                return iWindow;
            }
            return null;
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(1024);
        sb.append("Task=");
        sb.append(mTask);
        sb.append(" {mTask.windowingMode=");
        sb.append(mTask.getConfiguration().windowConfiguration.getWindowingMode());
        sb.append("}");
        sb.append(" {mFreezedWindowingMode=");
        sb.append(mFreezedWindowingMode);
        sb.append(" mMute=");
        sb.append(mMute);
        sb.append(" mWindowCenterPosition=");
        sb.append(mWindowCenterPosition);
        sb.append(" mWindowSurfaceScale=");
        sb.append(mWindowSurfaceScale);
        sb.append(" mWindowSurfaceScaleFactor=");
        sb.append(mWindowSurfaceScaleFactor);
        sb.append(" mWindowBoundaryGap=");
        sb.append(mWindowBoundaryGap);
        sb.append(" mCornerRadius=");
        sb.append(mCornerRadius);
        sb.append(" mPinnedWindowVerticalPosRatio=");
        sb.append(mPinnedWindowVerticalPosRatio);
        sb.append("}");
        return sb.toString();
    }
}
