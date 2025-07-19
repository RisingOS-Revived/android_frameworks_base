/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import static com.android.server.wm.SurfaceAnimator.ANIMATION_TYPE_SCREEN_ROTATION;
import static org.rising.DebugConstants.DEBUG_POP_UP;

import android.graphics.Rect;
import android.util.Slog;
import android.view.SurfaceControl;

class SurfaceFreezerExt {
    private static final String TAG = "SurfaceFreezerExt";

    /**
     * Interface for objects that can be frozen (replaces SurfaceFreezer.Freezable)
     */
    public interface Freezable extends SurfaceAnimator.Animatable {
        SurfaceControl getSurfaceControl();
        void onAnimationLeashCreated(SurfaceControl.Transaction t, SurfaceControl leash);
        void onAnimationLeashDestroyed(SurfaceControl.Transaction t);
    }

    /**
     * Snapshot class (replaces SurfaceFreezer.Snapshot)
     */
    public static class Snapshot {
        private SurfaceControl mSurfaceControl;
        private final Rect mBounds = new Rect();

        public Snapshot(SurfaceControl surface, Rect bounds) {
            mSurfaceControl = surface;
            mBounds.set(bounds);
        }

        public SurfaceControl getSurfaceControl() {
            return mSurfaceControl;
        }

        public Rect getBounds() {
            return new Rect(mBounds);
        }

        public void destroy(SurfaceControl.Transaction t) {
            if (mSurfaceControl != null && mSurfaceControl.isValid()) {
                t.remove(mSurfaceControl);
                mSurfaceControl = null;
            }
            mBounds.setEmpty();
        }

        public boolean isValid() {
            return mSurfaceControl != null && mSurfaceControl.isValid();
        }
    }

    // Internal state that replaces SurfaceFreezer fields
    private final Freezable mAnimatable;
    private final WindowManagerService mWmService;
    private final Rect mFreezeBounds = new Rect();
    
    private int mPreFreezedWindowingMode;
    private TaskWindowSurfaceInfo mFreezeTaskWindowSurfaceInfo;
    private Snapshot mSnapshot;
    private SurfaceControl mLeash;
    
    boolean mNoWindowModeAnim;

    SurfaceFreezerExt(Freezable animatable, WindowManagerService service) {
        mAnimatable = animatable;
        mWmService = service;
    }

    // Legacy constructor for compatibility with old SurfaceFreezer usage
    SurfaceFreezerExt(Object freezer, Freezable animatable, WindowManagerService service) {
        this(animatable, service);
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "SurfaceFreezerExt created with legacy freezer parameter (ignored)");
        }
    }

    Snapshot getSnapshot() {
        return mSnapshot;
    }

    TaskWindowSurfaceInfo getFreezeTaskWindowSurfaceInfo() {
        final TaskWindowSurfaceInfo info = mFreezeTaskWindowSurfaceInfo;
        mFreezeTaskWindowSurfaceInfo = null;
        return info;
    }

    boolean hasFreezeTaskWindowSurfaceInfo() {
        return mFreezeTaskWindowSurfaceInfo != null;
    }

    void setPreFreezedWindowingMode(int windowingMode) {
        mPreFreezedWindowingMode = windowingMode;
    }

    int getPreFreezedWindowingMode() {
        return mPreFreezedWindowingMode;
    }

    Rect getFreezeBounds() {
        return new Rect(mFreezeBounds);
    }

    SurfaceControl getLeash() {
        return mLeash;
    }

    boolean isFrozen() {
        return mLeash != null && mLeash.isValid();
    }

    void freeze(SurfaceControl.Transaction t, Rect startBounds,
            TaskWindowSurfaceInfo taskWindowSurfaceInfo) {
        reset(t);
        mFreezeBounds.set(startBounds);
        mFreezeTaskWindowSurfaceInfo = new TaskWindowSurfaceInfo(taskWindowSurfaceInfo, mPreFreezedWindowingMode);
        
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "freeze(): startBounds=" + startBounds);
        }

        if (mAnimatable != null && mAnimatable.getSurfaceControl() != null) {
            mLeash = SurfaceAnimatorExt.createAnimationLeash(
                    mAnimatable, mAnimatable.getSurfaceControl(), t,
                    ANIMATION_TYPE_SCREEN_ROTATION, startBounds.width(), startBounds.height(),
                    false, mWmService.mTransactionFactory, mFreezeTaskWindowSurfaceInfo);
            
            if (mLeash != null) {
                mAnimatable.onAnimationLeashCreated(t, mLeash);
                
                // Create snapshot if leash was successfully created
                mSnapshot = new Snapshot(mLeash, startBounds);
            }
        }
        
        t.apply();
    }

    void transitionFreeze(Rect startBounds, TaskWindowSurfaceInfo info) {
        if (!hasFreezeTaskWindowSurfaceInfo()) {
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "transitionFreeze(): startBounds=" + startBounds +
                        ", mPreFreezedWindowingMode=" + mPreFreezedWindowingMode);
            }
            mFreezeBounds.set(startBounds);
            mFreezeTaskWindowSurfaceInfo = new TaskWindowSurfaceInfo(info, mPreFreezedWindowingMode);
        }
    }

    void unfreeze(SurfaceControl.Transaction t) {
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "unfreeze()");
        }
        
        if (mAnimatable != null && mLeash != null) {
            mAnimatable.onAnimationLeashDestroyed(t);
        }
        
        reset(t);
        t.apply();
    }

    private void reset(SurfaceControl.Transaction t) {
        if (mSnapshot != null) {
            mSnapshot.destroy(t);
            mSnapshot = null;
        }
        if (mLeash != null) {
            t.remove(mLeash);
            mLeash = null;
        }
        mFreezeBounds.setEmpty();
    }

    /**
     * Clean up all resources
     */
    void destroy() {
        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        try {
            reset(t);
            t.apply();
        } finally {
            t.close();
        }
        mFreezeTaskWindowSurfaceInfo = null;
    }
}
