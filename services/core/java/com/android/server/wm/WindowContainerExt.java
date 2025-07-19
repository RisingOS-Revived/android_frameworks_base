/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import static android.view.WindowManager.TRANSIT_CHANGE;
import static org.rising.DebugConstants.DEBUG_POP_UP;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Debug;
import android.util.Pair;
import android.util.Slog;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;

import com.android.server.wm.Transition.ChangeInfo.PopUpViewInfo;

class WindowContainerExt implements SurfaceFreezerExt.Freezable, SurfaceAnimator.Animatable {

    private static final String TAG = "WindowContainerExt";

    private final Rect mPrevBounds = new Rect();

    final SurfaceFreezerExt mFreezerExt;
    private final TransitionController mTransitionController;
    private final WindowContainer mWc;

    private TaskWindowSurfaceInfo mTaskWindowSurfaceInfo;
    private Transition mTransition;

    private int mWindowingMode;
    private boolean mFinishTopTask;

    WindowContainerExt(WindowContainer wc) {
        mWc = wc;
        mFreezerExt = new SurfaceFreezerExt(this, wc.mWmService);
        mTransitionController = wc.mTransitionController;
    }

    WindowContainerExt(WindowContainer wc, Object freezer) {
        this(wc);
    }

    @Override
    public SurfaceControl getSurfaceControl() {
        return mWc != null ? mWc.getSurfaceControl() : null;
    }

    @Override
    public SurfaceControl.Builder makeAnimationLeash() {
        return mWc != null ? mWc.makeSurface() : null;
    }

    @Override
    public SurfaceControl getParentSurfaceControl() {
        return mWc != null && mWc.getParent() != null ? mWc.getParent().getSurfaceControl() : null;
    }

    @Override
    public void onAnimationLeashCreated(Transaction t, SurfaceControl leash) {
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "Animation leash created for " + mWc + ", leash=" + leash);
        }
    }

    @Override
    public void onAnimationLeashLost(Transaction t) {
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "Animation leash lost for " + mWc);
        }
    }

    @Override
    public void onAnimationLeashDestroyed(Transaction t) {
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "Animation leash destroyed for " + mWc);
        }
    }

    @Override
    public int getSurfaceWidth() {
        return mWc != null ? mWc.getBounds().width() : 0;
    }

    @Override
    public int getSurfaceHeight() {
        return mWc != null ? mWc.getBounds().height() : 0;
    }

    @Override
    public SurfaceControl getAnimationLeashParent() {
        return getParentSurfaceControl();
    }

    @Override
    public Transaction getSyncTransaction() {
        return mWc != null ? mWc.getSyncTransaction() : null;
    }

    @Override
    public Transaction getPendingTransaction() {
        return mWc != null ? mWc.getPendingTransaction() : null;
    }

    @Override
    public void commitPendingTransaction() {
        if (mWc != null) {
            mWc.commitPendingTransaction();
        }
    }

    void onWindowingModeChanged(int preWindowMode) {
        if (mTaskWindowSurfaceInfo != null) {
            mTaskWindowSurfaceInfo.onWindowingModeChanged(preWindowMode);
        }
    }

    void onConfigurationChanged() {
        if (mTaskWindowSurfaceInfo != null) {
            mTaskWindowSurfaceInfo.onConfigurationChanged();
        }
    }

    Pair<AnimationAdapter, AnimationAdapter> getAnimationAdapter(DisplayInfo displayInfo, float durationScale) {
        if (mWc.getWindowConfiguration().isPopUpWindowMode() ||
                PopUpWindowController.getInstance().isTryExitWindowingMode() ||
                mFreezerExt.hasFreezeTaskWindowSurfaceInfo()) {
            final TaskWindowSurfaceInfo freezedInfo = mFreezerExt.getFreezeTaskWindowSurfaceInfo();
            if (freezedInfo == null) {
                return null;
            }
            final AnimationAdapter adapter = new LocalAnimationAdapter(
                    new WindowChangeAnimationSpecExt(freezedInfo, mTaskWindowSurfaceInfo,
                            displayInfo, durationScale, true, false), mWc.getSurfaceAnimationRunner());
            AnimationAdapter thumbnailAdapter = null;
            if (mFreezerExt.getSnapshot() != null) {
                thumbnailAdapter = new LocalAnimationAdapter(
                        new WindowChangeAnimationSpecExt(freezedInfo, mTaskWindowSurfaceInfo,
                                displayInfo, durationScale, true, true), mWc.getSurfaceAnimationRunner());
            }
            return new Pair<>(adapter, thumbnailAdapter);
        }
        return null;
    }

    PopUpViewInfo getPopUpViewInfo() {
        return mTaskWindowSurfaceInfo != null ? mTaskWindowSurfaceInfo.getPopUpViewInfo() : null;
    }

    void getPrevBounds(Rect rect) {
        if (rect != null) {
            rect.set(mPrevBounds);
        }
    }

    TaskWindowSurfaceInfo getTaskWindowSurfaceInfo() {
        return mTaskWindowSurfaceInfo;
    }

    boolean getFreezerSkipAnim() {
        return mFreezerExt.mNoWindowModeAnim;
    }

    void setFinishTopTask(boolean finishTopTask) {
        mFinishTopTask = finishTopTask;
    }

    void setFreezerSkipAnim(boolean skip) {
        mFreezerExt.mNoWindowModeAnim = skip;
    }

    boolean setPreFreezedWindowingMode(int windowingMode) {
        mFreezerExt.setPreFreezedWindowingMode(windowingMode);
        return true;
    }

    boolean setOrientation(WindowContainer parent) {
        if (mWc == null || parent == null ||
                (!mWc.getWindowConfiguration().isPopUpWindowMode() &&
                !parent.getWindowConfiguration().isPopUpWindowMode())) {
            return false;
        }
        final int requestedOrientation = mWc.getRequestedConfigurationOrientation();
        final int systemOrientation = mWc.mWmService.mRoot.getConfiguration().orientation;
        final Configuration overrideConfig = parent.getRequestedOverrideConfiguration();
        final Rect tmpBounds = parent.getConfiguration().windowConfiguration.getBounds();
        final int height = Math.max(tmpBounds.height(), tmpBounds.width());
        final int width = Math.min(tmpBounds.height(), tmpBounds.width());
        final Rect newBounds;
        if (requestedOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            newBounds = new Rect(0, 0, height, width);
        } else {
            newBounds = new Rect(0, 0, width, height);
        }
        newBounds.offsetTo(tmpBounds.left, tmpBounds.top);
        overrideConfig.windowConfiguration.setBounds(newBounds);
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "setOrientation: requestedOrientation=" + requestedOrientation +
                    ", systemOrientation=" + systemOrientation + " tmpBounds=" + tmpBounds +
                    " newBounds" + newBounds + " mWc=" + mWc + " parent=" + parent +
                    " callers=" + Debug.getCallers(2));
        }
        if (!tmpBounds.equals(newBounds)) {
            parent.onRequestedOverrideConfigurationChanged(overrideConfig);
        }
        if (mWc.getWindowConfiguration().isPinnedExtWindowMode()) {
            TopActivityRecorder.getInstance().updateTopPinnedWindowActivity(mWc.asActivityRecord());
        }
        return true;
    }

    void initTask(Task task) {
        mTaskWindowSurfaceInfo = new TaskWindowSurfaceInfo(task);
    }

    boolean initializeChangeTransition(Rect startBounds) {
        if (mWc.getWindowConfiguration().isPopUpWindowMode() ||
                PopUpWindowController.getInstance().isTryExitWindowingMode()) {
            mFreezerExt.freeze(mWc.getSyncTransaction(), startBounds, mTaskWindowSurfaceInfo);
            return true;
        }
        return false;
    }

    void prepareTransition() {
        if (!mTransitionController.isShellTransitionsEnabled()) {
            return;
        }
        if (mTransitionController.isCollecting()) {
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "prepareTransition(), isCollecting, return");
            }
            return;
        }
        mTransition = mTransitionController.createTransition(TRANSIT_CHANGE);
        if ((mWc.asWallpaperToken() != null &&
                !mTransition.isInTransition(mWc.mDisplayContent)) ||
                mWc.getSyncGroup() == null ||
                mWc.getSyncGroup() == mWc.mWmService.mSyncEngine.getSyncSet(mTransition.getSyncId())) {
            mTransitionController.collect(mWc);
            mTransition.setReady(mWc, true);
            mPrevBounds.set(mWc.getBounds());
            mWindowingMode = mWc.getWindowingMode();
        }
    }

    void scheduleTransition() {
        if (mTransition == null) {
            return;
        }
        if (mWc.getWindowConfiguration().isPopUpWindowMode() ||
                PopUpWindowController.getInstance().isTryExitWindowingMode() ||
                mFreezerExt.hasFreezeTaskWindowSurfaceInfo()) {
            final TaskWindowSurfaceInfo info = mFreezerExt.getFreezeTaskWindowSurfaceInfo();
            if (info != null) {
                mTaskWindowSurfaceInfo.scheduleTransition(info, mWc.getDisplayContent().getDisplayInfo());
            }
        }
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "scheduleTransition(), requestStartTransition");
        }
        mTransitionController.requestStartTransition(mTransition, mWc.asTask(), null, null);
        mTransition = null;
    }

    void transitionFreeze(Task task) {
        if (mTransition == null) {
            return;
        }
        if (!PopUpWindowController.getInstance().shouldStartChangeTransition(
                mWindowingMode, task.getWindowingMode())) {
            return;
        }
        if (!PopUpWindowController.getInstance().shouldInitializeChangeTransition(task, mWindowingMode)) {
            return;
        }
        mFreezerExt.transitionFreeze(task.mTmpPrevBounds, mTaskWindowSurfaceInfo);
    }

    boolean isFinishTopTask() {
        return mFinishTopTask;
    }

    public boolean shouldDeferAnimationFinish(Runnable endDeferFinishCallback) {
        return false;
    }
}
