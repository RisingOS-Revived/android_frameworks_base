/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import static android.app.WindowConfiguration.WINDOWING_MODE_MINI_WINDOW_EXT;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED_WINDOW_EXT;

import static org.rising.DebugConstants.DEBUG_POP_UP;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Binder;
import android.util.Slog;
import android.view.IWindow;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewConfiguration;

import com.android.internal.R;

class MiniWindowEdgeBarHelper {

    private static final String TAG = "MiniWindowEdgeBarHelper";

    private static final int EDGE_BAR_COLOR = Color.parseColor("#FFF0F2F2");

    private final RectF mBarBounds = new RectF();
    private final RectF mBarTouchBounds = new RectF();

    private Paint mPaint;
    private Task mTask;
    private TaskWindowSurfaceInfo mTaskWindowSurfaceInfo;
    private View mView;

    private int mBarHeight;
    private int mBarMargin;
    private int mBarRadius;
    private int mBarWidth;

    private float mDragSlop;

    private boolean mScrolling = false;
    private boolean mListening = false;
    private boolean mSkipDraw = false;

    void onDragResizeChanged(float scale, Rect displayBound, boolean isLandscape) {
        if (mListening && mView != null) {
            mView.postOnAnimation(() -> {
                updateBoundsOnDrag(scale, displayBound, isLandscape);
                mView.invalidate();
            });
        }
    }

    void onResizeChanged() {
        if (mListening && mView != null) {
            mView.postOnAnimation(() -> {
                updateBounds();
                mView.invalidate();
            });
        }
    }

    void onVisibilityChanged(int visibility) {
        if (visibility == View.VISIBLE) {
            mListening = true;
            updateBounds();
        } else {
            mScrolling = false;
            mListening = false;
        }
    }

    void onOrientationChanged() {
        if (mListening && mView != null) {
            mView.postOnAnimation(() -> {
                updateBounds();
                mView.invalidate();
            });
        }
    }

    void onDraw(Canvas canvas) {
        if (!mBarBounds.isEmpty() && !mSkipDraw) {
            canvas.drawRoundRect(mBarBounds, mBarRadius, mBarRadius, mPaint);
        }
    }

    boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (!mBarTouchBounds.contains(e1.getRawX(), e1.getRawY()) || mSkipDraw) {
            return false;
        }
        if (passedSlop(e1.getRawX(), e1.getRawY(), e2.getRawX(), e2.getRawY())) {
            mScrolling = true;
            if (mTask != null) {
                final WindowState winState = mTask.getTopVisibleAppMainWindow();
                if (winState != null) {
                    final long ident = Binder.clearCallingIdentity();
                    try {
                        mTask.mAtmService.mWindowManager.mTaskPositioningController.
                                startMovingTask(winState.getIWindow(), e2.getRawX(), e2.getRawY());
                    } finally {
                        Binder.restoreCallingIdentity(ident);
                    }
                }
            }
        }
        return true;
    }

    void onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_UP:
                if (mScrolling) {
                    mScrolling = false;
                    if (mTask == null) {
                        return;
                    }
                    final WindowState winState = mTask.getTopVisibleAppMainWindow();
                    if (winState == null) {
                        break;
                    }
                    final long ident = Binder.clearCallingIdentity();
                    try {
                        mTask.mAtmService.mWindowManager.mTaskPositioningController.
                                finishTaskPositioning(winState.getIWindow());
                    } finally {
                        Binder.restoreCallingIdentity(ident);
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mScrolling) {
                    mScrolling = false;
                }
                break;
        }
    }

    void updateResources() {
        if (getContext() == null) {
            return;
        }
        mBarWidth = getContext().getResources().getDimensionPixelSize(R.dimen.mini_window_bar_width);
        mBarHeight = getContext().getResources().getDimensionPixelSize(R.dimen.mini_window_bar_height);
        mBarRadius = getContext().getResources().getDimensionPixelSize(R.dimen.mini_window_bar_radius);
        mBarMargin = getContext().getResources().getDimensionPixelSize(R.dimen.mini_window_bar_margin);
    }

    RectF getBarTouchBounds() {
        return new RectF(mBarTouchBounds);
    }

    void setUp(View view) {
        mView = view;
        updateResources();

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(EDGE_BAR_COLOR);
        mPaint.setStyle(Style.FILL);

        mDragSlop = (float) ViewConfiguration.get(getContext()).getScaledTouchSlop();

        updateBounds();
    }

    void setTask(Task task, boolean isSystemTool) {
        mTask = task;
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "setTask: " + (task != null));
        }
        if (mTask != null) {
            mTaskWindowSurfaceInfo = mTask.mWindowContainerExt.getTaskWindowSurfaceInfo();
        } else {
            mTaskWindowSurfaceInfo = null;
        }
        mSkipDraw = isSystemTool;
        onResizeChanged();
    }

    private void updateBoundsOnDrag(float scale, Rect bounds, boolean isLandscape) {
        final RectF barBounds = new RectF();
        final float height = mBarHeight * scale;
        final float margin = mBarMargin * scale;
        final float width = mBarWidth * scale;
        if (isLandscape) {
            barBounds.left = bounds.right + margin;
            barBounds.top = bounds.top + ((bounds.height() - width) / 2);
            barBounds.right = barBounds.left + height;
            barBounds.bottom = barBounds.top + width;

            mBarTouchBounds.left = bounds.right;
            mBarTouchBounds.top = barBounds.top - margin * 3;
            mBarTouchBounds.right = barBounds.right + margin * 4;
            mBarTouchBounds.bottom = barBounds.bottom + margin * 3;
        } else {
            barBounds.left = bounds.left + ((bounds.width() - width) / 2);
            barBounds.top = bounds.bottom + margin;
            barBounds.right = barBounds.left + width;
            barBounds.bottom = barBounds.top + height;

            mBarTouchBounds.left = barBounds.left - margin * 3;
            mBarTouchBounds.top = bounds.bottom;
            mBarTouchBounds.right = barBounds.right + margin * 3;
            mBarTouchBounds.bottom = barBounds.bottom + margin * 4;
        }
        mBarBounds.set(barBounds);
    }

    private void updateBounds() {
        final Task rootTask = mTask != null ? mTask.getRootTask() : null;
        if (rootTask == null) {
            return;
        }
        final int displayRotation = rootTask.getWindowConfiguration().getDisplayRotation();
        final RectF barBounds = new RectF();
        final Rect bounds = mTaskWindowSurfaceInfo.getTaskWindowSurfaceBounds();
        final float scale = mTaskWindowSurfaceInfo.getWindowSurfaceScale();
        final float height = mBarHeight * scale;
        final float margin = mBarMargin * scale;
        final float width = mBarWidth * scale;
        if (displayRotation == Surface.ROTATION_90 || displayRotation == Surface.ROTATION_270) {
            barBounds.left = bounds.right + margin;
            barBounds.top = bounds.top + ((bounds.height() - width) / 2);
            barBounds.right = barBounds.left + height;
            barBounds.bottom = barBounds.top + width;

            mBarTouchBounds.left = bounds.right;
            mBarTouchBounds.top = bounds.top - margin * 3;
            mBarTouchBounds.right = barBounds.right + margin * 4;
            mBarTouchBounds.bottom = barBounds.bottom + margin * 3;
        } else {
            barBounds.left = bounds.left + ((bounds.width() - width) / 2);
            barBounds.top = bounds.bottom + margin;
            barBounds.right = barBounds.left + width;
            barBounds.bottom = barBounds.top + height;

            mBarTouchBounds.left = bounds.left - margin * 3;
            mBarTouchBounds.top = bounds.bottom;
            mBarTouchBounds.right = bounds.right + margin * 3;
            mBarTouchBounds.bottom = barBounds.bottom + margin * 4;
        }
        mBarBounds.set(barBounds);
    }

    private boolean passedSlop(float startX, float startY, float x, float y) {
        return Math.abs(x - startX) > mDragSlop ||
                Math.abs(y - startY) > mDragSlop;
    }

    private Context getContext() {
        if (mView == null) {
            return null;
        }
        return mView.getContext();
    }
}
