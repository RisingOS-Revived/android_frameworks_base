/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED_WINDOW_EXT;
import static android.content.pm.ActivityInfo.CONFIG_DENSITY;
import static android.content.pm.ActivityInfo.CONFIG_ORIENTATION;
import static android.view.View.SYSTEM_UI_FLAG_FULLSCREEN;
import static android.view.WindowManager.LayoutParams.TYPE_MINI_WINDOW_DIMMER;

import static com.android.server.wm.PopUpWindowController.MOVE_TO_BACK_TOUCH_OUTSIDE;

import static org.rising.DebugConstants.DEBUG_POP_UP;
import static org.rising.view.PopUpViewManager.TAP_ACTION_EXIT;
import static org.rising.view.PopUpViewManager.TAP_ACTION_PIN_WINDOW;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.ActivityThread;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Handler;
import android.os.Looper;
import android.util.Slog;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.animation.PathInterpolator;

import com.android.server.UiThread;

class DimmerWindow {

    private static final String TAG = "DimmerWindow";
    static final String WIN_TITLE = "MiniWindowDimmer";

    private static final long FADE_IN_ANIMATION_DELAY_MS = 150L;
    private static final long FADE_IN_ANIMATION_DURATION_MS = 300L;

    private static final float WIN_DIM_AMOUNT = 0.4f;

    private final Context mUiContext = ActivityThread.currentActivityThread().getSystemUiContext();
    private final Handler mUiHandler = new Handler(UiThread.getHandler().getLooper());
    private final MiniWindowEdgeBarHelper mEdgeBarHelper = new MiniWindowEdgeBarHelper();
    private final LayoutParams mWindowParams = new LayoutParams();

    private final ValueAnimator mFadeInAnimator;

    private Configuration mOldConfig;
    private DimView mDimView;
    private Task mLastDimmerTask;
    private WindowManager mWindowManager;

    private boolean mIsWindowAdded = false;
    private boolean mShowing = false;
    private boolean mSingleTapOnly = false;
    private boolean mIsSystemTool = false;

    private static class InstanceHolder {
        private static final DimmerWindow INSTANCE = new DimmerWindow();
    }

    static DimmerWindow getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private DimmerWindow() {
        mFadeInAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
        mFadeInAnimator.setInterpolator(new PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f));
        mFadeInAnimator.addUpdateListener(valueAnimator -> {
            mDimView.setAlpha((float) valueAnimator.getAnimatedValue());
        });
        mFadeInAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDimView.setAlpha(1.0f);
            }
        });
        mFadeInAnimator.setDuration(FADE_IN_ANIMATION_DURATION_MS);
        mFadeInAnimator.setStartDelay(FADE_IN_ANIMATION_DELAY_MS);
    }

    private class DimView extends View {

        private final Region mEdgeTouchExcludeRegion;
        private final GestureDetector mGestureDetector;

        DimView(Context context) {
            super(context);
            mEdgeBarHelper.setUp(this);
            mEdgeTouchExcludeRegion = new Region();
            mOldConfig = new Configuration(context.getResources().getConfiguration());
            mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(MotionEvent event) {
                    if (!mSingleTapOnly && !mIsSystemTool) {
                        return true;
                    }
                    if (DEBUG_POP_UP) {
                        Slog.d(TAG, "onSingleTapUp: event=" + event);
                    }
                    if (!getEdgeBarBounds().contains(event.getX(), event.getY())) {
                        if (mIsSystemTool) {
                            moveActivityTaskToBack();
                            return true;
                        }
                        switch (PopUpSettingsConfig.getInstance().getSingleTapAction()) {
                            case TAP_ACTION_PIN_WINDOW:
                                enterPinnedWindowingMode();
                                break;
                            case TAP_ACTION_EXIT:
                                moveActivityTaskToBack();
                                break;
                        }
                    }
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent event) {
                    if (mSingleTapOnly || mIsSystemTool) {
                        return true;
                    }
                    if (DEBUG_POP_UP) {
                        Slog.d(TAG, "onSingleTapConfirmed: event=" + event);
                    }
                    if (!getEdgeBarBounds().contains(event.getX(), event.getY())) {
                        switch (PopUpSettingsConfig.getInstance().getSingleTapAction()) {
                            case TAP_ACTION_PIN_WINDOW:
                                enterPinnedWindowingMode();
                                break;
                            case TAP_ACTION_EXIT:
                                moveActivityTaskToBack();
                                break;
                        }
                    }
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent event) {
                    if (mSingleTapOnly || mIsSystemTool) {
                        return true;
                    }
                    if (DEBUG_POP_UP) {
                        Slog.d(TAG, "onDoubleTap: event=" + event);
                    }
                    if (!getEdgeBarBounds().contains(event.getX(), event.getY())) {
                        switch (PopUpSettingsConfig.getInstance().getDoubleTapAction()) {
                            case TAP_ACTION_PIN_WINDOW:
                                enterPinnedWindowingMode();
                                break;
                            case TAP_ACTION_EXIT:
                                moveActivityTaskToBack();
                                break;
                        }
                    }
                    return true;
                }

                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                    return mEdgeBarHelper.onScroll(e1, e2, distanceX, distanceY);
                }
            });
            mGestureDetector.setIsLongpressEnabled(false);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            mEdgeBarHelper.onTouchEvent(event);
            return mGestureDetector.onTouchEvent(event);
        }

        @Override
        protected void onVisibilityChanged(View changedView, int visibility) {
            if (changedView == this) {
                mEdgeBarHelper.onVisibilityChanged(visibility);
            }
        }

        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            if ((newConfig.diff(mOldConfig) & CONFIG_DENSITY) != 0) {
                onDensityChanged();
            } else if ((newConfig.diff(mOldConfig) & CONFIG_ORIENTATION) != 0) {
                mEdgeBarHelper.onOrientationChanged();
            }
            mOldConfig.setTo(newConfig);
        }

        @Override
        public void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            mEdgeBarHelper.onDraw(canvas);
        }
    }

    Task getTask() {
        return mLastDimmerTask;
    }

    void setTask(Task task) {
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "setTask: " + (task != null ? task : "null"));
        }
        mLastDimmerTask = task;
        mIsSystemTool = TopActivityRecorder.getInstance().isTaskSystemTool(task);
        mEdgeBarHelper.setTask(mLastDimmerTask, mIsSystemTool);
        updateWindowState(task != null);
    }

    RectF getEdgeBarBounds() {
        return mEdgeBarHelper.getBarTouchBounds();
    }

    boolean enterPinnedWindowingMode() {
        if (mLastDimmerTask == null) {
            return false;
        }
        synchronized (mLastDimmerTask.mAtmService.mGlobalLock) {
            final Task rootTask = mLastDimmerTask.getRootTask();
            if (rootTask == null) {
                Slog.e(TAG, "enterPinnedWindowingMode: the task doesn't have a root task");
                return false;
            }
            TopActivityRecorder.getInstance().moveTopMiniToPinned(mLastDimmerTask);
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "enterPinnedWindowingMode: task=" + rootTask);
            }
            rootTask.mWindowContainerExt.prepareTransition();
            rootTask.setWindowingMode(WINDOWING_MODE_PINNED_WINDOW_EXT);
            PopUpWindowController.getInstance().updateFocusedApp();
            rootTask.mWindowContainerExt.scheduleTransition();
            return true;
        }
    }

    void moveActivityTaskToBack() {
        if (mLastDimmerTask == null) {
            return;
        }
        PopUpWindowController.getInstance().moveActivityTaskToBack(
                mLastDimmerTask, MOVE_TO_BACK_TOUCH_OUTSIDE);
    }

    private void updateWindowState(boolean show) {
        mUiHandler.post(() -> {
            if (show && !mIsWindowAdded) {
                addDimmerWin();
            } else {
                updateDimmerWin(show);
            }
        });
    }

    void setSingleTapOnly(boolean singleTapOnly) {
        mSingleTapOnly = singleTapOnly;
    }

    void onDensityChanged() {
        mEdgeBarHelper.updateResources();
        PopUpWindowController.getInstance().findAndExitAllPopUp();
    }

    void onDragResizeChanged(float scale, Rect taskWindowSurfaceBound, boolean isLandscape) {
        mEdgeBarHelper.onDragResizeChanged(scale, taskWindowSurfaceBound, isLandscape);
    }

    void onResizeChanged() {
        mEdgeBarHelper.onResizeChanged();
    }

    private void addDimmerWin() {
        if (getWindowManager() != null) {
            mWindowParams.type = TYPE_MINI_WINDOW_DIMMER;
            mWindowParams.format = PixelFormat.RGBA_8888;
            mWindowParams.flags = LayoutParams.FLAG_DIM_BEHIND |
                                  LayoutParams.FLAG_NOT_FOCUSABLE |
                                  LayoutParams.FLAG_FULLSCREEN;
            mWindowParams.privateFlags = LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
            mWindowParams.layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            mWindowParams.setFitInsetsTypes(0);
            mWindowParams.dimAmount = WIN_DIM_AMOUNT;
            mWindowParams.gravity = Gravity.LEFT | Gravity.TOP;
            mWindowParams.x = 0;
            mWindowParams.y = 0;
            mWindowParams.setTitle(WIN_TITLE);
            mWindowParams.width = LayoutParams.FILL_PARENT;
            mWindowParams.height = LayoutParams.FILL_PARENT;
            mWindowParams.windowAnimations = 0;

            mDimView = new DimView(mUiContext.createWindowContext(TYPE_MINI_WINDOW_DIMMER, null));
            mDimView.setAlpha(0.0f);

            mWindowManager.addView(mDimView, mWindowParams);
            mIsWindowAdded = true;
            mShowing = true;
            mDimView.setSystemUiVisibility(SYSTEM_UI_FLAG_FULLSCREEN);
            mFadeInAnimator.start();
        }
    }

    private void updateDimmerWin(boolean show) {
        if (getWindowManager() != null && mDimView != null && mShowing != show) {
            if (show) {
                mWindowParams.flags |= LayoutParams.FLAG_DIM_BEHIND;
            } else {
                mWindowParams.flags &= ~LayoutParams.FLAG_DIM_BEHIND;
            }
            mWindowManager.updateViewLayout(mDimView, mWindowParams);
            if (show) {
                mFadeInAnimator.start();
            } else {
                mFadeInAnimator.cancel();
                mDimView.setAlpha(0.0f);
            }
            mDimView.setVisibility(show ? View.VISIBLE : View.GONE);
            mShowing = show;
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "updateDimmerWin: show=" + show);
            }
        }
    }

    private WindowManager getWindowManager() {
        if (mWindowManager == null) {
            mWindowManager = mUiContext.getSystemService(WindowManager.class);
        }
        return mWindowManager;
    }
}
