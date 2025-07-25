/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wm;

import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_TASK_POSITIONING;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;

import static java.util.concurrent.CompletableFuture.completedFuture;

import android.annotation.Nullable;
import android.app.WindowConfiguration;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.IBinder;
import android.util.Slog;
import android.view.Display;
import android.view.IWindow;
import android.view.InputWindowHandle;
import android.view.SurfaceControl;

import java.util.concurrent.CompletableFuture;

/**
 * Controller for task positioning by drag.
 */
class TaskPositioningController {
    private final WindowManagerService mService;
    private SurfaceControl mInputSurface;
    private DisplayContent mPositioningDisplay;

    private @Nullable TaskPositioner mTaskPositioner;
    private @Nullable WindowPositioner mWindowPositioner;

    private final Rect mTmpClipRect = new Rect();
    private static final String TAG = "TaskPositioningController";
    boolean isWindowPositioningLocked() {
        return mWindowPositioner != null;
    }

    final SurfaceControl.Transaction mTransaction;

    TaskPositioningController(WindowManagerService service) {
        Slog.d(TAG_WM, "TaskPositioningController: <init>");
        mService = service;
        mTransaction = service.mTransactionFactory.get();
    }

    boolean isPositioningLocked() {
        Slog.d(TAG_WM, "TaskPositioningController: isPositioningLocked()");
        return mTaskPositioner != null;
    }

    InputWindowHandle getDragWindowHandleLocked() {
        Slog.d(TAG_WM, "TaskPositioningController: getDragWindowHandleLocked()");
        if (mTaskPositioner != null) {
            return mTaskPositioner.mDragWindowHandle;
        }
        if (mWindowPositioner != null) {
            return mWindowPositioner.mDragWindowHandle;
        }
        return null;
    }

    void hideInputSurface(int displayId) {
        Slog.d(TAG_WM, "TaskPositioningController: hideInputSurface(" + displayId + ")");
        if (mPositioningDisplay != null && mPositioningDisplay.getDisplayId() == displayId
                && mInputSurface != null) {
            mTransaction.hide(mInputSurface).apply();
        }
    }

    /**
     * @return a future that completes after window info is sent.
     */
    CompletableFuture<Void> showInputSurface(int displayId) {
        Slog.d(TAG_WM, "TaskPositioningController: showInputSurface(" + displayId + ")");
        if (mPositioningDisplay == null || mPositioningDisplay.getDisplayId() != displayId) {
            return completedFuture(null);
        }
        final DisplayContent dc = mService.mRoot.getDisplayContent(displayId);
        if (mInputSurface == null) {
            mInputSurface = mService.makeSurfaceBuilder()
                    .setContainerLayer()
                    .setName("Drag and Drop Input Consumer")
                    .setCallsite("TaskPositioningController.showInputSurface")
                    .setParent(dc.getOverlayLayer())
                    .build();
        }

        final InputWindowHandle h = getDragWindowHandleLocked();
        if (h == null) {
            Slog.w(TAG_WM, "Drag is in progress but there is no "
                    + "drag window handle.");
            return completedFuture(null);
        }

        final Display display = dc.getDisplay();
        final Point p = new Point();
        display.getRealSize(p);
        mTmpClipRect.set(0, 0, p.x, p.y);

        CompletableFuture<Void> result = new CompletableFuture<>();
        mTransaction.show(mInputSurface)
                .setInputWindowInfo(mInputSurface, h)
                .setLayer(mInputSurface, Integer.MAX_VALUE)
                .setPosition(mInputSurface, 0, 0)
                .setCrop(mInputSurface, mTmpClipRect)
                .addWindowInfosReportedListener(() -> result.complete(null))
                .apply();
        return result;
    }

    boolean startMovingTask(IWindow window, float startX, float startY) {
        Slog.d(TAG_WM, "TaskPositioningController: startMovingTask(window=" + window + ", startX=" + startX + ", startY=" + startY + ")");
        WindowState win = null;
        CompletableFuture<Boolean> startPositioningLockedFuture;
        synchronized (mService.mGlobalLock) {
            win = mService.windowForClientLocked(null, window, false);
            startPositioningLockedFuture =
                startPositioningLocked(
                    win, false /*resize*/, false /*preserveOrientation*/, startX, startY);
        }

        try {
            if (!startPositioningLockedFuture.get()) {
                return false;
            }
        } catch (Exception exception) {
            Slog.e(TAG_WM, "Exception thrown while waiting for startPositionLocked future",
                    exception);
            return false;
        }

        synchronized (mService.mGlobalLock) {
            mService.mAtmService.setFocusedTask(win.getTask().mTaskId);
        }
        return true;
    }

    void handleTapOutsideTask(DisplayContent displayContent, int x, int y) {
        Slog.d(TAG_WM, "TaskPositioningController: handleTapOutsideTask(displayContent=" + displayContent + ", x=" + x + ", y=" + y + ")");
        mService.mH.post(() -> {
            Task task;
            CompletableFuture<Boolean> startPositioningLockedFuture;
            synchronized (mService.mGlobalLock) {
                task = displayContent.findTaskForResizePoint(x, y);
                if (task == null || !task.isResizeable()) {
                    // The task is not resizable, so don't do anything when the user drags the
                    // the resize handles.
                    return;
                }
                if (task.getWindowConfiguration().isPopUpWindowMode()) {
                    return;
                }
                startPositioningLockedFuture =
                    startPositioningLocked(task.getTopVisibleAppMainWindow(), true /*resize*/,
                            task.preserveOrientationOnResize(), x, y);
            }

            try {
                if (!startPositioningLockedFuture.get()) {
                    return;
                }
            } catch (Exception exception) {
                Slog.e(TAG_WM, "Exception thrown while waiting for startPositionLocked future",
                        exception);
                return;
            }

            synchronized (mService.mGlobalLock) {
                mService.mAtmService.setFocusedTask(task.mTaskId);
            }
        });
    }

    private CompletableFuture<Boolean> startPositioningLocked(WindowState win, boolean resize,
            boolean preserveOrientation, float startX, float startY) {
        Slog.d(TAG_WM, "TaskPositioningController: startPositioningLocked(win=" + win + ", resize=" + resize + ", preserveOrientation=" + preserveOrientation + ", startX=" + startX + ", startY=" + startY + ")");
        if (DEBUG_TASK_POSITIONING)
            Slog.d(TAG_WM, "startPositioningLocked: "
                    + "win=" + win + ", resize=" + resize + ", preserveOrientation="
                    + preserveOrientation + ", {" + startX + ", " + startY + "}");

        if (win == null || win.mActivityRecord == null) {
            Slog.w(TAG_WM, "startPositioningLocked: Bad window " + win);
            return completedFuture(false);
        }

        /*
        if (win.mInputChannel == null) {
            Slog.wtf(TAG_WM, "startPositioningLocked: " + win + " has no input channel, "
                    + " probably being removed");
            return completedFuture(false);
        }
        */

        final DisplayContent displayContent = win.getDisplayContent();
        if (displayContent == null) {
            Slog.w(TAG_WM, "startPositioningLocked: Invalid display content " + win);
            return completedFuture(false);
        }
        mPositioningDisplay = displayContent;

        final int windowingMode = win.getTask() != null ? win.getTask().getWindowingMode()
                : win.getWindowConfiguration().getWindowingMode();
        if (WindowConfiguration.isPopUpWindowMode(windowingMode)) {
            mWindowPositioner = WindowPositioner.create(mService);
            return mWindowPositioner.register(displayContent, win).thenApply(unused -> {
                // The global lock is held by the callers of startPositioningLocked but released before
                // the async results are waited on. We must acquire the lock in this callback to ensure
                // thread safety.
                synchronized (mService.mGlobalLock) {
                    // We need to grab the touch focus so that the touch events during the
                    // resizing/scrolling are not sent to the app. 'win' is the main window
                    // of the app, it may not have focus since there might be other windows
                    // on top (eg. a dialog window).
                    final WindowState transferFocusFromWin = mWindowPositioner.updateTransferFocus(windowingMode);
                    if (!mService.mInputManager.transferTouchGesture(
                            transferFocusFromWin.mInputChannel.getToken(),
                            mWindowPositioner.mClientChannel.getToken(),
                            true)) {
                        Slog.e(TAG_WM, "startPositioningLocked: Unable to transfer touch focus");
                        cleanUpTaskPositioner();
                        return false;
                    }

                    mWindowPositioner.startDrag(WindowConfiguration.isMiniExtWindowMode(windowingMode),
                            startX, startY);
                    return true;
                }
            });
        }

        mTaskPositioner = TaskPositioner.create(mService);
        return mTaskPositioner.register(displayContent, win).thenApply(unused -> {
            // The global lock is held by the callers of startPositioningLocked but released before
            // the async results are waited on. We must acquire the lock in this callback to ensure
            // thread safety.
            synchronized (mService.mGlobalLock) {
                // We need to grab the touch focus so that the touch events during the
                // resizing/scrolling are not sent to the app. 'win' is the main window
                // of the app, it may not have focus since there might be other windows
                // on top (eg. a dialog window).
                WindowState transferTouchFromWin = win;
                if (displayContent.mCurrentFocus != null && displayContent.mCurrentFocus != win
                        && displayContent.mCurrentFocus.mActivityRecord == win.mActivityRecord) {
                    transferTouchFromWin = displayContent.mCurrentFocus;
                }

                IBinder fromToken = win.mInputChannel.getToken();
                IBinder toToken = mTaskPositioner.mClientChannel.getToken();
                boolean isDrag = true;
                Slog.d(TAG, "Attempting transferTouchGesture 2 : from=" + fromToken
                        + " to=" + toToken + " isDrag=" + isDrag
                        + " (fromToken == null? " + (fromToken == null)
                        + ", toToken == null? " + (toToken == null) + ")");

                if (!mService.mInputManager.transferTouchGesture(
                        transferTouchFromWin.mInputChannel.getToken(),
                        mTaskPositioner.mClientChannel.getToken(),
                        true)) {
                    Slog.e(TAG_WM, "startPositioningLocked: Unable to transfer touch focus");
                    cleanUpTaskPositioner();
                    return false;
                }


                mTaskPositioner.startDrag(resize, preserveOrientation, startX, startY);
                return true;
            }
        });
    }

    void cancelWindowPositionerInputEvent() {
        mService.mAnimationHandler.post(() -> {
            synchronized (mService.mGlobalLock) {
                if (mWindowPositioner != null) {
                    mWindowPositioner.cancelInputEvent();
                    if (true) {
                        Slog.d(TAG_WM, "cancelWindowPositionerInputEvent");
                    }
                }
            }
        });
    }

    public void finishTaskPositioning(IWindow window) {
        Slog.d(TAG_WM, "TaskPositioningController: finishTaskPositioning(window=" + window + ")");
        if (mTaskPositioner != null && mTaskPositioner.mClientCallback == window.asBinder()) {
            finishTaskPositioning();
        }
        if (mWindowPositioner != null && mWindowPositioner.mClientCallback == window.asBinder()) {
            finishTaskPositioning();
        }
    }

    void finishTaskPositioning() {
        Slog.d(TAG_WM, "TaskPositioningController: finishTaskPositioning()");
        // TaskPositioner attaches the InputEventReceiver to the animation thread. We need to
        // dispose the receiver on the same thread to avoid race conditions.
        mService.mAnimationHandler.post(() -> {
            if (DEBUG_TASK_POSITIONING) Slog.d(TAG_WM, "finishPositioning");

            synchronized (mService.mGlobalLock) {
                cleanUpTaskPositioner();
                mPositioningDisplay = null;
            }
        });
    }

    private void cleanUpTaskPositioner() {
        Slog.d(TAG_WM, "TaskPositioningController: cleanUpTaskPositioner()");

        final TaskPositioner positioner = mTaskPositioner;
        final WindowPositioner winPositioner = mWindowPositioner;
        if (positioner != null) {
            // We need to assign task positioner to null first to indicate that we're finishing task
            // positioning.
            mTaskPositioner = null;
            positioner.unregister();
        }
        if (winPositioner != null) {
            // We need to assign window positioner to null first to indicate that we're finishing task
            // positioning.
            mWindowPositioner = null;
            winPositioner.unregister();
        }

    }
}
