/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED_WINDOW_EXT;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.os.InputConfig.NOT_FOCUSABLE;
import static android.os.InputConstants.DEFAULT_DISPATCHING_TIMEOUT_MILLIS;
import static android.view.InputDevice.SOURCE_CLASS_POINTER;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_DRAG;
import static android.view.WindowManager.LayoutParams.TYPE_MINI_WINDOW_DIMMER;

import static org.rising.DebugConstants.DEBUG_POP_UP;
import static com.android.server.wm.PopUpWindowController.MOVE_TO_BACK_FROM_LEAVE_BUTTON;
import static com.android.server.wm.WindowResizingAlgorithm.MINI_WINDOW_SCALE_EXIT_MAX;
import static com.android.server.wm.WindowResizingAlgorithm.MINI_WINDOW_SCALE_EXIT_MIN;
import static com.android.server.wm.WindowResizingAlgorithm.MINI_WINDOW_SCALE_REVERSE_ORIENTATION_EXIT_MAX;
import static com.android.server.wm.WindowResizingAlgorithm.MINI_WINDOW_SCALE_REVERSE_ORIENTATION_EXIT_MIN;
import static com.android.server.wm.WindowState.MINIMUM_VISIBLE_HEIGHT_IN_DP;
import static com.android.server.wm.WindowState.MINIMUM_VISIBLE_WIDTH_IN_DP;

import static java.util.concurrent.CompletableFuture.completedFuture;

import android.app.WindowConfiguration;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.Slog;
import android.view.BatchedInputEventReceiver;
import android.view.Choreographer;
import android.view.InputApplicationHandle;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputWindowHandle;
import android.view.InsetsState;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.WindowInsets;

import com.android.internal.R;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

class WindowPositioner implements IBinder.DeathRecipient {

    private static final String TAG = "WindowPositioner";

    private static final int DISMISS_TYPE_NONE = 0;
    private static final int DISMISS_TYPE_HAND_CROSS_OVER = 1;

    private static final float RESIZING_HINT_ALPHA = 0.5f;

    private static final int RESIZING_HINT_DURATION_MS = 0;

    private static final int MINI_WINDOW_DRAG_SCALE_TYPE_NONE = 0;
    private static final int MINI_WINDOW_DRAG_SCALE_TYPE_TO_PINNED = 1;
    private static final int MINI_WINDOW_DRAG_SCALE_TYPE_TO_FULL = 2;

    private static Factory sFactory;

    private final WindowManagerService mService;

    private final Rect mTmpRect = new Rect();
    private final Rect mTmpRect2 = new Rect();
    private final Rect mWindowOriginalBounds = new Rect();
    private final Rect mWindowDragBounds = new Rect();
    private final Point mWindowOriginalPosition = new Point();
    private final Point mMaxVisibleSize = new Point();

    private PinnedWindowDismissView mDismissView;
    private DisplayContent mDisplayContent;
    private InputApplicationHandle mDragApplicationHandle;
    private InputEventReceiver mInputEventReceiver;
    private MotionEvent mLastMotionEvent;

    private int mMinVisibleHeight;
    private int mMinVisibleWidth;
    private boolean mDragEnded;
    private boolean mResizing;
    private boolean mStartOrientationWasLandscape;
    private boolean mStartRotationWasLandscape;
    private float mStartDragEdgebarCenterX;
    private float mStartDragEdgebarCenterY;
    private float mStartDragX;
    private float mStartDragY;

    private Task mTask;
    private TaskWindowSurfaceInfo mTaskWindowSurfaceInfo;
    private WindowState mWindow;

    private int mLastMiniWindowDragScaleType = MINI_WINDOW_DRAG_SCALE_TYPE_NONE;
    private int mLastDismissType = DISMISS_TYPE_NONE;

    IBinder mClientCallback;
    InputChannel mClientChannel;
    InputWindowHandle mDragWindowHandle;

    WindowPositioner(WindowManagerService service) {
        mService = service;
    }

    private Choreographer getChoreographer() {
        // Get choreographer from the animation handler's looper
        return Choreographer.getInstance();
    }

    boolean onInputEvent(InputEvent event) {
        if (!(event instanceof MotionEvent) || (event.getSource() & SOURCE_CLASS_POINTER) == 0) {
            return false;
        }
        if (mDragEnded) {
            return true;
        }
        mLastMotionEvent = (MotionEvent) event;
        final float newX = mLastMotionEvent.getRawX();
        final float newY = mLastMotionEvent.getRawY();
        final int dismissType = !mResizing ? moveOnDismissView(newX, newY) : DISMISS_TYPE_NONE;
        switch (mLastMotionEvent.getAction()) {
            case MotionEvent.ACTION_UP:
                mDragEnded = true;
                break;
            case MotionEvent.ACTION_MOVE:
                synchronized (mService.mGlobalLock) {
                    final boolean reversedOrientation = isOrientationReversed();
                    mDragEnded = notifyMoveLocked(newX, newY, reversedOrientation);
                    mTask.getDimBounds(mTmpRect);
                    if (mTmpRect.equals(mWindowDragBounds) && !mResizing) {
                        break;
                    }
                    mDisplayContent.getBounds(mTmpRect2);
                    final Point pos = new Point();
                    mTaskWindowSurfaceInfo.setWindowSurfaceScaleFactor(
                            WindowResizingAlgorithm.getPositionAndScaleFactorForTask(
                                    mWindowDragBounds, mTmpRect2, mTaskWindowSurfaceInfo.getWindowCenterPosition(),
                                    mTaskWindowSurfaceInfo.getWindowSurfaceScale(),
                                    mTask.getWindowConfiguration().isPinnedExtWindowMode(), pos));
                    final float winScale = mTaskWindowSurfaceInfo.getWindowSurfaceRealScale();
                    mService.mH.post(() -> {
                        synchronized (mService.mGlobalLock) {
                            if (mTask != null && mTask.getWindowConfiguration().isPopUpWindowMode()) {
                                final SurfaceControl.Transaction t = mTask.getSyncTransaction();
                                final SurfaceControl leash = mTask.mSurfaceControl;
                                final float alpha = dismissType == DISMISS_TYPE_HAND_CROSS_OVER ? RESIZING_HINT_ALPHA : 1.0f;
                                if (mResizing) {
                                    final float exitScaleMax = reversedOrientation ?
                                            MINI_WINDOW_SCALE_REVERSE_ORIENTATION_EXIT_MAX : MINI_WINDOW_SCALE_EXIT_MAX;
                                    final float exitScaleMin = reversedOrientation ?
                                            MINI_WINDOW_SCALE_REVERSE_ORIENTATION_EXIT_MIN : MINI_WINDOW_SCALE_EXIT_MIN;
                                    int miniWindowDragScaleType = MINI_WINDOW_DRAG_SCALE_TYPE_NONE;
                                    if (exitScaleMin < mTaskWindowSurfaceInfo.getWindowSurfaceScale() &&
                                            mTaskWindowSurfaceInfo.getWindowSurfaceScale() < exitScaleMax) {
                                        miniWindowDragScaleType = MINI_WINDOW_DRAG_SCALE_TYPE_NONE;
                                    } else if (mTaskWindowSurfaceInfo.getWindowSurfaceScale() <= exitScaleMin) {
                                        miniWindowDragScaleType = MINI_WINDOW_DRAG_SCALE_TYPE_TO_PINNED;
                                    } else if (mTaskWindowSurfaceInfo.getWindowSurfaceScale() > exitScaleMax) {
                                        miniWindowDragScaleType = MINI_WINDOW_DRAG_SCALE_TYPE_TO_FULL;
                                    }
                                    if (mLastMiniWindowDragScaleType != miniWindowDragScaleType) {
                                        mLastMiniWindowDragScaleType = miniWindowDragScaleType;
                                        if (miniWindowDragScaleType != MINI_WINDOW_DRAG_SCALE_TYPE_NONE) {
                                            PopUpWindowController.getInstance().triggerVibrate();
                                        }
                                    }
                                    t.setPosition(leash, pos.x, pos.y);
                                } else {
                                    if (mLastDismissType != dismissType) {
                                        mLastDismissType = dismissType;
                                        if (dismissType == DISMISS_TYPE_HAND_CROSS_OVER) {
                                            PopUpWindowController.getInstance().triggerVibrate();
                                        }
                                    }
                                    if (!mTaskWindowSurfaceInfo.isCrossOverAnimating()) {
                                        t.setPosition(leash, pos.x, pos.y);
                                    }
                                }
                                t.setWindowCrop(leash, mWindowDragBounds.width(), mWindowDragBounds.height())
                                        .setCornerRadius(leash, mTaskWindowSurfaceInfo.getCornerRadius())
                                        .setAlpha(leash, alpha)
                                        .setScale(leash, winScale, winScale)
                                        .show(leash).apply();
                                if (DEBUG_POP_UP) {
                                    Slog.d(TAG, "ACTION_MOVE apply end @ {" + newX + ", " + newY + "}");
                                }
                            }
                        }
                    });
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                mDragEnded = true;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (mLastMotionEvent.getActionIndex() == 0) {
                    mDragEnded = true;
                }
                break;
        }
        if (!mDragEnded) {
            return true;
        }
        synchronized (mService.mGlobalLock) {
            final boolean wasResizing = mResizing;
            endDragLocked();
            mTask.getDimBounds(mTmpRect);
            mDisplayContent.getBounds(mTmpRect2);
            if (wasResizing) {
                if (mLastMiniWindowDragScaleType == MINI_WINDOW_DRAG_SCALE_TYPE_NONE) {
                    final Point startPos = getPosition();
                    final float startWinScale = mTaskWindowSurfaceInfo.getWindowSurfaceRealScale();
                    final float defaultScale = WindowResizingAlgorithm.getDefaultMiniWindowScale(
                            mTask.getConfiguration().orientation, !mStartRotationWasLandscape);
                    final Point endPos = getPosition(defaultScale);
                    final float endWinScale = mTaskWindowSurfaceInfo.getWindowSurfaceRealScale(defaultScale);
                    mTaskWindowSurfaceInfo.resizeWindowWithAnimation(startPos, endPos,
                            mWindowDragBounds.width(), mWindowDragBounds.height(),
                            startWinScale, endWinScale, mTmpRect2, mStartRotationWasLandscape);
                    finishTaskPositioning();
                    return true;
                }
                if (mLastMiniWindowDragScaleType == MINI_WINDOW_DRAG_SCALE_TYPE_TO_PINNED) {
                    mService.mH.post(() -> {
                        synchronized (mService.mGlobalLock) {
                            try {
                                if (DEBUG_POP_UP) {
                                    Slog.d(TAG, "enterPinnedWindowingMode!");
                                }
                                enterPinnedWindowingMode();
                                PopUpWindowController.getInstance().updateFocusedApp();
                                finishTaskPositioning();
                            } catch (Throwable th) {
                                throw th;
                            }
                        }
                    });
                    return true;
                }
                if (mLastMiniWindowDragScaleType == MINI_WINDOW_DRAG_SCALE_TYPE_TO_FULL) {
                    mService.mH.post(() -> {
                        synchronized (mService.mGlobalLock) {
                            if (DEBUG_POP_UP) {
                                Slog.d(TAG, "exitMiniWindowingMode!");
                            }
                            exitMiniWindowingMode();
                            finishTaskPositioning();
                        }
                    });
                    return true;
                }
                return true;
            }
            final Rect displayBound = new Rect();
            final InsetsState state = mDisplayContent.getInsetsStateController().getRawInsetsState();
            displayBound.set(state.getDisplayFrame());
            displayBound.inset(state.calculateInsets(displayBound,
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout(), true));
            if (dismissType == DISMISS_TYPE_HAND_CROSS_OVER) {
                PopUpWindowController.getInstance().moveActivityTaskToBack(mTask, MOVE_TO_BACK_FROM_LEAVE_BUTTON);
            } else {
                mService.mH.post(() -> {
                    synchronized (mService.mGlobalLock) {
                        if (mTask != null) {
                            final SurfaceControl.Transaction t = mTask.getSyncTransaction();
                            final SurfaceControl leash = mTask.mSurfaceControl;
                            if (leash != null && leash.isValid()) {
                                t.setAlpha(leash, 1.0f).apply();
                            }
                        }
                    }
                });
                final Point startPos = getPosition();
                final Point pos = new Point();
                final float[] vels = PinnedWindowOverlayController.getInstance().computeCurrentVelocity();
                final float xVelocity = vels[0];
                final float yVelocity = vels[1];
                final Rect surfaceBounds = mTaskWindowSurfaceInfo.getTaskWindowSurfaceBounds();
                final Rect boundaryGap = WindowResizingAlgorithm.getBoundaryGapAfterMoving(
                        mTmpRect2.width(), displayBound, surfaceBounds,
                        newX, newY, xVelocity, yVelocity);
                mTaskWindowSurfaceInfo.setWindowBoundaryGap(
                        boundaryGap.left, boundaryGap.top, boundaryGap.right, boundaryGap.bottom);
                WindowResizingAlgorithm.getCenterByBoundaryGap(mWindowDragBounds, displayBound,
                        mTaskWindowSurfaceInfo.getWindowBoundaryGap(),
                        mTaskWindowSurfaceInfo.getPinnedWindowVerticalPosRatio(displayBound),
                        mTaskWindowSurfaceInfo.getWindowCenterPosition(),
                        mTaskWindowSurfaceInfo.getWindowSurfaceScale(), pos);
                mTaskWindowSurfaceInfo.setWindowCenterPosition(pos);
                mTaskWindowSurfaceInfo.setPinnedWindowVerticalPosRatio(pos, displayBound, true);
                final Point endPos = getPosition();
                final float winScale = mTaskWindowSurfaceInfo.getWindowSurfaceRealScale();
                mTaskWindowSurfaceInfo.flingWindowToEdge(startPos, endPos,
                        mWindowDragBounds.width(), mWindowDragBounds.height(),
                        winScale, xVelocity, yVelocity);
            }
            finishTaskPositioning();
            return true;
        }
    }

    void cancelInputEvent() {
        if (mLastMotionEvent != null) {
            mLastMotionEvent.cancel();
            if (onInputEvent(mLastMotionEvent)) {
                if (DEBUG_POP_UP) {
                    Slog.d(TAG, "cancelWindowPositionerInputEvent");
                }
            }
        }
    }

    private Point getPosition() {
        final Point pos = new Point();
        mTaskWindowSurfaceInfo.setWindowSurfaceScaleFactor(
                WindowResizingAlgorithm.getPositionAndScaleFactorForTask(mWindowDragBounds, mTmpRect2,
                        mTaskWindowSurfaceInfo.getWindowCenterPosition(),
                        mTaskWindowSurfaceInfo.getWindowSurfaceScale(),
                        mTask.getWindowConfiguration().isPinnedExtWindowMode(), pos));
        return pos;
    }

    private Point getPosition(float scale) {
        final Point pos = new Point();
        mTaskWindowSurfaceInfo.setWindowSurfaceScaleFactor(
                WindowResizingAlgorithm.getPositionAndScaleFactorForTask(mWindowDragBounds, mTmpRect2,
                        mTaskWindowSurfaceInfo.getWindowCenterPosition(), scale,
                        mTask.getWindowConfiguration().isPinnedExtWindowMode(), pos));
        return pos;
    }

    CompletableFuture<Void> register(DisplayContent displayContent, WindowState win) {
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "Registering task positioner");
        }

        if (mClientChannel != null) {
            Slog.e(TAG, "Task positioner already registered");
            return completedFuture(null);
        }

        mDisplayContent = displayContent;
        mClientChannel = mService.mInputManager.createInputChannel(TAG);

        mInputEventReceiver = new BatchedInputEventReceiver.SimpleBatchedInputEventReceiver(
                mClientChannel, mService.mAnimationHandler.getLooper(),
                getChoreographer(), this::onInputEvent);

        mDragApplicationHandle = new InputApplicationHandle(new Binder(), TAG, DEFAULT_DISPATCHING_TIMEOUT_MILLIS);

        mDragWindowHandle = new InputWindowHandle(mDragApplicationHandle, displayContent.getDisplayId());
        mDragWindowHandle.name = TAG;
        mDragWindowHandle.token = mClientChannel.getToken();
        mDragWindowHandle.layoutParamsType = TYPE_DRAG;
        mDragWindowHandle.dispatchingTimeoutMillis = DEFAULT_DISPATCHING_TIMEOUT_MILLIS;
        mDragWindowHandle.ownerPid = WindowManagerService.MY_PID;
        mDragWindowHandle.ownerUid = WindowManagerService.MY_UID;
        mDragWindowHandle.scaleFactor = 1.0f;
        mDragWindowHandle.inputConfig = NOT_FOCUSABLE;

        mDragWindowHandle.touchableRegion.setEmpty();

        mDisplayContent.getDisplayRotation().pause();

        return showInputSurface(win.getDisplayId())
            .thenRun(() -> {
                synchronized (this.mService.mGlobalLock) {
                    final Rect displayBounds = mTmpRect2;
                    displayContent.getBounds(displayBounds);
                    final DisplayMetrics displayMetrics = displayContent.getDisplayMetrics();
                    mMinVisibleWidth = WindowManagerService.dipToPixel(MINIMUM_VISIBLE_WIDTH_IN_DP, displayMetrics);
                    mMinVisibleHeight = WindowManagerService.dipToPixel(MINIMUM_VISIBLE_HEIGHT_IN_DP, displayMetrics);
                    mMaxVisibleSize.set(displayBounds.width(), displayBounds.height());

                    mDragEnded = false;

                    try {
                        mClientCallback = win.mClient.asBinder();
                        mClientCallback.linkToDeath(this, 0);
                        mWindow = win;
                        mTask = win.getTask();
                        mTaskWindowSurfaceInfo = mTask.mWindowContainerExt.getTaskWindowSurfaceInfo();
                    } catch (RemoteException e) {
                        finishTaskPositioning();
                    }
                }
            });
    }

    void unregister() {
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "Unregistering task positioner");
        }

        if (mClientChannel == null) {
            Slog.e(TAG, "Task positioner not registered");
            return;
        }

        hideInputSurface(mDisplayContent.getDisplayId());
        mService.mInputManager.removeInputChannel(mClientChannel.getToken());

        mInputEventReceiver.dispose();
        mInputEventReceiver = null;
        mClientChannel.dispose();
        mClientChannel = null;

        mDragWindowHandle = null;
        mDragApplicationHandle = null;
        mDragEnded = true;

        mDisplayContent.getInputMonitor().updateInputWindowsLw(true);

        mDisplayContent.getDisplayRotation().resume();
        mDisplayContent = null;
        if (mClientCallback != null) {
            mClientCallback.unlinkToDeath(this, 0);
        }
        mWindow = null;

        if (mDismissView != null) {
            mDismissView.hideOnScreen(mLastDismissType == DISMISS_TYPE_HAND_CROSS_OVER);
            mDismissView = null;
        }
    }

    WindowState updateTransferFocus(int windowingMode) {
        if (WindowConfiguration.isMiniExtWindowMode(windowingMode)) {
            final ArrayList<WindowState> windows = new ArrayList<>();
            mService.mRoot.getWindowsByName(windows, DimmerWindow.WIN_TITLE);
            if (windows.size() > 0) {
                for (WindowState state : windows) {
                    if (state.mAttrs.type == TYPE_MINI_WINDOW_DIMMER) {
                        return state;
                    }
                }
            } else {
                Slog.e(TAG, "Unable to find " + DimmerWindow.WIN_TITLE);
            }
        } else {
            final ArrayList<WindowState> windows = new ArrayList<>();
            mService.mRoot.getWindowsByName(windows, PinnedWindowOverlayController.WIN_TITLE);
            if (windows.size() > 0) {
                for (WindowState state : windows) {
                    if (state.mAttrs.type == TYPE_APPLICATION_OVERLAY &&
                            "android".equals(state.getOwningPackage())) {
                        return state;
                    }
                }
            } else {
                Slog.e(TAG, "Unable to find " + PinnedWindowOverlayController.WIN_TITLE);
            }
        }
        return null;
    }

    void startDrag(boolean resize, float startX, float startY) {
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "startDrag: win=" + mWindow + ", resize=" + resize + ", {" + startX + ", " + startY + "}");
        }
        final Rect startBounds = mTmpRect;
        mTask.getBounds(startBounds);

        mStartDragX = startX;
        mStartDragY = startY;
        mStartDragEdgebarCenterX = DimmerWindow.getInstance().getEdgeBarBounds().centerX();
        mStartDragEdgebarCenterY = DimmerWindow.getInstance().getEdgeBarBounds().centerY();

        if (resize) {
            mResizing = true;
        } else if (mDismissView == null) {
            final Context uiContext = mDisplayContent.getDisplayPolicy().getSystemUiContext();
            mDismissView = (PinnedWindowDismissView) LayoutInflater.from(uiContext)
                    .inflate(R.layout.pinned_window_dismiss, null);
            mDismissView.showOnScreen(mService.mAnimationHandler.getLooper());
        }
        mStartOrientationWasLandscape = startBounds.width() >= startBounds.height();
        final int rotation = mDisplayContent.getRotation();
        mStartRotationWasLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
        mWindowDragBounds.set(startBounds);
        mWindowOriginalBounds.set(startBounds);
        mWindowOriginalPosition.set(mTaskWindowSurfaceInfo.getWindowCenterPosition());

        if (mResizing) {
            notifyMoveLocked(startX, startY, isOrientationReversed());

            mService.mH.post(() -> {
                synchronized (mService.mGlobalLock) {
                    if (mTask != null && mTask.getWindowConfiguration().isPopUpWindowMode()) {
                        if (mDisplayContent != null) {
                            final SurfaceControl.Transaction t = mTask.getSyncTransaction();
                            final SurfaceControl leash = mTask.mSurfaceControl;
                            mDisplayContent.getBounds(mTmpRect2);
                            final Point pos = new Point();
                            mTaskWindowSurfaceInfo.setWindowSurfaceScaleFactor(
                                    WindowResizingAlgorithm.getPositionAndScaleFactorForTask(
                                            mWindowDragBounds, mTmpRect2,
                                            mTaskWindowSurfaceInfo.getWindowCenterPosition(),
                                            mTaskWindowSurfaceInfo.getWindowSurfaceScale(),
                                            mTask.getWindowConfiguration().isPinnedExtWindowMode(), pos));
                            final float winScale = mTaskWindowSurfaceInfo.getWindowSurfaceRealScale();
                            t.setPosition(leash, pos.x, pos.y)
                                    .setWindowCrop(leash, mWindowDragBounds.width(), mWindowDragBounds.height())
                                    .setScale(leash, winScale, winScale)
                                    .setCornerRadius(leash, mTaskWindowSurfaceInfo.getCornerRadius())
                                    .show(leash).apply();
                        }
                    }
                }
            });
        }
        mWindowDragBounds.set(startBounds);
    }

    private void endDragLocked() {
        mResizing = false;
    }

    private boolean isOrientationReversed() {
        if (mTask == null) {
            return false;
        }
        final boolean portraitOrientation = mTask.getConfiguration().orientation == ORIENTATION_PORTRAIT;
        return portraitOrientation == mStartRotationWasLandscape;
    }

    private boolean notifyMoveLocked(float x, float y, boolean reversedOrientation) {
        final Task rootTask = mTask != null ? mTask.getRootTask() : null;
        if (mResizing) {
            if (rootTask != null) {
                if (mStartRotationWasLandscape) {
                    x = x - (DimmerWindow.getInstance().getEdgeBarBounds().width() / 2.0f)
                            + mStartDragEdgebarCenterX - mStartDragX;
                } else {
                    y = y - (DimmerWindow.getInstance().getEdgeBarBounds().height() / 2.0f)
                            + mStartDragEdgebarCenterY - mStartDragY;
                }
            }
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "notifyMoveLocked Resizing: {" + x + "," + y + "}");
            }
            resizeDrag(x, y, reversedOrientation);
            return false;
        }
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "notifyMoveLocked: {" + x + "," + y + "}");
        }
        mDisplayContent.getStableRect(mTmpRect);
        if (rootTask != null && rootTask.getParent() != null && rootTask.getParent().getBounds() != null) {
            mTmpRect.intersect(mTask.getRootTask().getParent().getBounds());
        }
        int nX = (int) x;
        int nY = (int) y;
        if (!mTmpRect.contains(nX, nY)) {
            nX = Math.min(Math.max(nX, mTmpRect.left), mTmpRect.right);
            nY = Math.min(Math.max(nY, mTmpRect.top), mTmpRect.bottom);
        }
        mTaskWindowSurfaceInfo.resetWindowBoundaryGap();
        updateWindowDragBounds(nX, nY, mTmpRect);
        return false;
    }

    private void resizeDrag(float x, float y, boolean reversedOrientation) {
        mDisplayContent.getBounds(mTmpRect2);
        float scale = WindowResizingAlgorithm.getScaleForTask(
                mWindowDragBounds, mTmpRect2,
                mTaskWindowSurfaceInfo.getWindowCenterPosition(), x, y);
        final float miniWindowScaleMin = 0.28f / mTaskWindowSurfaceInfo.getWindowSurfaceScaleFactor();
        final float miniWindowScaleMax = reversedOrientation ? 1.1f : 0.96f;
        if (scale < miniWindowScaleMin) {
            scale = miniWindowScaleMin;
        } else if (scale > miniWindowScaleMax) {
            scale = miniWindowScaleMax;
        }
        mTaskWindowSurfaceInfo.setWindowSurfaceScaleDrag(scale, mTmpRect2, mStartRotationWasLandscape);
        updateDraggedBounds(mWindowDragBounds);
    }

    private void updateDraggedBounds(Rect newBounds) {
        mWindowDragBounds.set(newBounds);
    }

    private void updateWindowDragBounds(int x, int y, Rect rootTaskBounds) {
        final int offsetX = Math.round(x - mStartDragX);
        final int offsetY = Math.round(y - mStartDragY);
        mWindowDragBounds.set(mWindowOriginalBounds);
        final int winOffsetX = mWindowOriginalBounds.left + offsetX;
        final int winOffsetY = mWindowOriginalBounds.top + offsetY;
        final int dx = winOffsetX - mWindowDragBounds.left;
        final int dy = winOffsetY - mWindowDragBounds.top;
        mWindowDragBounds.offsetTo(winOffsetX, winOffsetY);
        final Point pos = new Point(mWindowOriginalPosition.x, mWindowOriginalPosition.y);
        pos.offset(dx, dy);
        final Rect displayBound = new Rect();
        if (mTask.mDisplayContent != null) {
            final InsetsState state = mTask.mDisplayContent.getInsetsStateController().getRawInsetsState();
            displayBound.set(state.getDisplayFrame());
            displayBound.inset(state.calculateInsets(displayBound,
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout(), true));
        }
        mTaskWindowSurfaceInfo.setWindowCenterPosition(pos);
        mTaskWindowSurfaceInfo.setPinnedWindowVerticalPosRatio(pos, displayBound, true);
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "updateWindowDragBounds: " + mWindowDragBounds);
        }
    }

    private void enterPinnedWindowingMode() {
        synchronized (mService.mAtmService.mGlobalLock) {
            final Task rootTask = mTask.getRootTask();
            if (rootTask == null) {
                Slog.e(TAG, "enterPinnedWindowingMode: the mTask doesn't have a root task");
                return;
            }
            if (!rootTask.getWindowConfiguration().isMiniExtWindowMode()) {
                Slog.e(TAG, "enterPinnedWindowingMode: You can only enter pinned-window from mini-window.");
                return;
            }
            TopActivityRecorder.getInstance().moveTopMiniToPinned(mTask);
            rootTask.mWindowContainerExt.prepareTransition();
            rootTask.setWindowingMode(WINDOWING_MODE_PINNED_WINDOW_EXT);
            rootTask.mWindowContainerExt.scheduleTransition();
        }
    }

    private void exitMiniWindowingMode() {
        synchronized (mService.mAtmService.mGlobalLock) {
            final Task rootTask = mTask.getRootTask();
            if (rootTask == null) {
                Slog.e(TAG, "exitMiniWindowingMode: the mTask doesn't have a root task");
                return;
            }
            if (!rootTask.getWindowConfiguration().isMiniExtWindowMode()) {
                Slog.e(TAG, "exitMiniWindowingMode: You can only exit from mini-window.");
            }
            TopActivityRecorder.getInstance().moveTopMiniToFull();
            PopUpWindowController.getInstance().setTryExitWindowingModeByDrag(true);
            PopUpWindowController.getInstance().tryExitPopUpView(rootTask, false, false, false);
            PopUpWindowController.getInstance().setTryExitWindowingModeByDrag(false);
        }
    }

    private int moveOnDismissView(float newX, float newY) {
        if (mDismissView != null && mDismissView.crossOver(newX, newY)) {
            return DISMISS_TYPE_HAND_CROSS_OVER;
        }
        return DISMISS_TYPE_NONE;
    }

    static WindowPositioner create(WindowManagerService service) {
        if (sFactory == null) {
            sFactory = new Factory() {};
        }
        return sFactory.create(service);
    }

    @Override
    public void binderDied() {
        finishTaskPositioning();
    }

    private void finishTaskPositioning() {
       if (DEBUG_POP_UP) {
           Slog.d(TAG, "finishTaskPositioning");
       }
       unregister();
    }

    private CompletableFuture<Void> showInputSurface(int displayId) {
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "showInputSurface for display " + displayId);
        }
        if (mDisplayContent != null) {
            mDisplayContent.getInputMonitor().updateInputWindowsLw(true);
        }
        return completedFuture(null);
    }

    private void hideInputSurface(int displayId) {
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "hideInputSurface for display " + displayId);
        }
        if (mDisplayContent != null) {
            mDisplayContent.getInputMonitor().updateInputWindowsLw(true);
        }
    }

    private interface Factory {
        default WindowPositioner create(WindowManagerService service) {
            return new WindowPositioner(service);
        }
    }
}
