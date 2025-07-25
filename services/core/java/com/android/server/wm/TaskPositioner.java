/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static android.app.ActivityTaskManager.RESIZE_MODE_USER;
import static android.app.ActivityTaskManager.RESIZE_MODE_USER_FORCED;
import static android.os.InputConstants.DEFAULT_DISPATCHING_TIMEOUT_MILLIS;
import static android.os.Trace.TRACE_TAG_WINDOW_MANAGER;

import static com.android.internal.policy.TaskResizingAlgorithm.CTRL_BOTTOM;
import static com.android.internal.policy.TaskResizingAlgorithm.CTRL_LEFT;
import static com.android.internal.policy.TaskResizingAlgorithm.CTRL_NONE;
import static com.android.internal.policy.TaskResizingAlgorithm.CTRL_RIGHT;
import static com.android.internal.policy.TaskResizingAlgorithm.CTRL_TOP;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_TASK_POSITIONING;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;
import static com.android.server.wm.WindowManagerService.dipToPixel;
import static com.android.server.wm.WindowState.MINIMUM_VISIBLE_HEIGHT_IN_DP;
import static com.android.server.wm.WindowState.MINIMUM_VISIBLE_WIDTH_IN_DP;

import static java.util.concurrent.CompletableFuture.completedFuture;

import android.annotation.NonNull;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.os.InputConfig;
import android.os.RemoteException;
import android.os.Trace;
import android.util.DisplayMetrics;
import android.util.Slog;
import android.view.BatchedInputEventReceiver;
import android.view.Choreographer;
import android.view.InputApplicationHandle;
import android.view.InputChannel;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputWindowHandle;
import android.view.MotionEvent;
import android.view.WindowManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.policy.TaskResizingAlgorithm;
import com.android.internal.policy.TaskResizingAlgorithm.CtrlType;
import com.android.internal.protolog.ProtoLog;

import java.util.concurrent.CompletableFuture;

class TaskPositioner implements IBinder.DeathRecipient {
    private static final boolean DEBUG_ORIENTATION_VIOLATIONS = false;
    private static final String TAG_LOCAL = "TaskPositioner";
    private static final String TAG = TAG_WITH_CLASS_NAME ? TAG_LOCAL : TAG_WM;

    private static Factory sFactory;

    public static final float RESIZING_HINT_ALPHA = 0.5f;
    public static final int RESIZING_HINT_DURATION_MS = 0;

    private final WindowManagerService mService;
    private InputEventReceiver mInputEventReceiver;
    private DisplayContent mDisplayContent;
    private Rect mTmpRect = new Rect();
    private int mMinVisibleWidth;
    private int mMinVisibleHeight;

    @VisibleForTesting
    Task mTask;
    WindowState mWindow;
    private boolean mResizing;
    private boolean mPreserveOrientation;
    private boolean mStartOrientationWasLandscape;
    private final Rect mWindowOriginalBounds = new Rect();
    private final Rect mWindowDragBounds = new Rect();
    private final Point mMaxVisibleSize = new Point();
    private float mStartDragX;
    private float mStartDragY;
    @CtrlType
    private int mCtrlType = CTRL_NONE;
    @VisibleForTesting
    boolean mDragEnded;
    IBinder mClientCallback;

    InputChannel mClientChannel;
    InputApplicationHandle mDragApplicationHandle;
    InputWindowHandle mDragWindowHandle;

    /** Use {@link #create(WindowManagerService)} instead. */
    @VisibleForTesting
    TaskPositioner(WindowManagerService service) {
        Slog.d(TAG, "TaskPositioner: <init>");
        mService = service;
    }

    private boolean onInputEvent(InputEvent event) {
        Slog.d(TAG, "TaskPositioner: onInputEvent(" + event + ")");
        // All returns need to be in the try block to make sure the finishInputEvent is
        // called correctly.
        if (!(event instanceof MotionEvent)
                || (event.getSource() & InputDevice.SOURCE_CLASS_POINTER) == 0) {
            Slog.d(TAG, "TaskPositioner: onInputEvent - Not a MotionEvent or not a pointer event");
            return false;
        }
        final MotionEvent motionEvent = (MotionEvent) event;
        if (mDragEnded) {
            Slog.d(TAG, "TaskPositioner: onInputEvent - Drag already ended");
            // The drag has ended but the clean-up message has not been processed by
            // window manager. Drop events that occur after this until window manager
            // has a chance to clean-up the input handle.
            return true;
        }

        final float newX = motionEvent.getRawX();
        final float newY = motionEvent.getRawY();

        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                Slog.d(TAG, "TaskPositioner: ACTION_DOWN @ {" + newX + ", " + newY + "}");
            }
            break;

            case MotionEvent.ACTION_MOVE: {
                Slog.d(TAG, "TaskPositioner: ACTION_MOVE @ {" + newX + ", " + newY + "}");
                synchronized (mService.mGlobalLock) {
                    mDragEnded = notifyMoveLocked(newX, newY);
                    mTask.getDimBounds(mTmpRect);
                }
                if (!mTmpRect.equals(mWindowDragBounds)) {
                    Slog.d(TAG, "TaskPositioner: ACTION_MOVE - resizing task");
                    Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER,
                            "wm.TaskPositioner.resizeTask");
                    mService.mAtmService.resizeTask(
                            mTask.mTaskId, mWindowDragBounds, RESIZE_MODE_USER);
                    Trace.traceEnd(Trace.TRACE_TAG_WINDOW_MANAGER);
                }
            }
            break;

            case MotionEvent.ACTION_UP: {
                Slog.d(TAG, "TaskPositioner: ACTION_UP @ {" + newX + ", " + newY + "}");
                mDragEnded = true;
            }
            break;

            case MotionEvent.ACTION_CANCEL: {
                Slog.d(TAG, "TaskPositioner: ACTION_CANCEL @ {" + newX + ", " + newY + "}");
                mDragEnded = true;
            }
            break;
        }

        if (mDragEnded) {
            Slog.d(TAG, "TaskPositioner: Drag ended, cleaning up");
            final boolean wasResizing = mResizing;
            synchronized (mService.mGlobalLock) {
                endDragLocked();
                mTask.getDimBounds(mTmpRect);
            }
            if (wasResizing && !mTmpRect.equals(mWindowDragBounds)) {
                Slog.d(TAG, "TaskPositioner: Final resizeTask call after drag end");
                // We were using fullscreen surface during resizing. Request
                // resizeTask() one last time to restore surface to window size.
                mService.mAtmService.resizeTask(
                        mTask.mTaskId, mWindowDragBounds, RESIZE_MODE_USER_FORCED);
            }

            // Post back to WM to handle clean-ups. We still need the input
            // event handler for the last finishInputEvent()!
            mService.mTaskPositioningController.finishTaskPositioning();
        }
        return true;
    }

    @VisibleForTesting
    Rect getWindowDragBounds() {
        Slog.d(TAG, "TaskPositioner: getWindowDragBounds()");
        return mWindowDragBounds;
    }

    /**
     * @param displayContent The Display that the window being dragged is on.
     * @param win The window which will be dragged.
     */
    CompletableFuture<Void> register(DisplayContent displayContent, @NonNull WindowState win) {
        Slog.d(TAG, "TaskPositioner: register(displayContent=" + displayContent + ", win=" + win + ")");
        if (DEBUG_TASK_POSITIONING) {
            Slog.d(TAG, "Registering task positioner");
        }

        if (mClientChannel != null) {
            Slog.e(TAG, "TaskPositioner: Task positioner already registered");
            return completedFuture(null);
        }

        mDisplayContent = displayContent;
        mClientChannel = mService.mInputManager.createInputChannel(TAG);

        mInputEventReceiver = new BatchedInputEventReceiver.SimpleBatchedInputEventReceiver(
                mClientChannel, mService.mAnimationHandler.getLooper(),
                Choreographer.getInstance(), this::onInputEvent);

        mDragApplicationHandle = new InputApplicationHandle(new Binder(), TAG,
                DEFAULT_DISPATCHING_TIMEOUT_MILLIS);

        mDragWindowHandle = new InputWindowHandle(mDragApplicationHandle,
                displayContent.getDisplayId());
        mDragWindowHandle.name = TAG;
        mDragWindowHandle.token = mClientChannel.getToken();
        mDragWindowHandle.layoutParamsType = WindowManager.LayoutParams.TYPE_DRAG;
        mDragWindowHandle.dispatchingTimeoutMillis = DEFAULT_DISPATCHING_TIMEOUT_MILLIS;
        mDragWindowHandle.ownerPid = WindowManagerService.MY_PID;
        mDragWindowHandle.ownerUid = WindowManagerService.MY_UID;
        mDragWindowHandle.scaleFactor = 1.0f;
        // When dragging the window around, we do not want to steal focus for the window.
        mDragWindowHandle.inputConfig = InputConfig.NOT_FOCUSABLE;

        // The drag window cannot receive new touches.
        mDragWindowHandle.touchableRegion.setEmpty();

        // Pause rotations before a drag.
        mDisplayContent.getDisplayRotation().pause();

        // Notify InputMonitor to take mDragWindowHandle.
        return mService.mTaskPositioningController.showInputSurface(win.getDisplayId())
            .thenRun(() -> {
                Slog.d(TAG, "TaskPositioner: register() - showInputSurface complete");
                // The global lock is held by the callers of register but released before the async
                // results are waited on. We must acquire the lock in this callback to ensure thread
                // safety.
                synchronized (mService.mGlobalLock) {
                    Slog.d(TAG, "TaskPositioner: register() - in globalLock");
                    final Rect displayBounds = mTmpRect;
                    displayContent.getBounds(displayBounds);
                    final DisplayMetrics displayMetrics = displayContent.getDisplayMetrics();
                    mMinVisibleWidth = dipToPixel(MINIMUM_VISIBLE_WIDTH_IN_DP, displayMetrics);
                    mMinVisibleHeight = dipToPixel(MINIMUM_VISIBLE_HEIGHT_IN_DP, displayMetrics);
                    mMaxVisibleSize.set(displayBounds.width(), displayBounds.height());

                    mDragEnded = false;

                    try {
                        mClientCallback = win.mClient.asBinder();
                        mClientCallback.linkToDeath(this, 0 /* flags */);
                        Slog.d(TAG, "TaskPositioner: register() - linkToDeath complete");
                    } catch (RemoteException e) {
                        Slog.e(TAG, "TaskPositioner: register() - RemoteException, cleaning up", e);
                        // The caller has died, so clean up TaskPositioningController.
                        mService.mTaskPositioningController.finishTaskPositioning();
                        return;
                    }
                    mWindow = win;
                    mTask = win.getTask();
                    Slog.d(TAG, "TaskPositioner: register() - completed for win=" + win);
                }
            });
    }

    void unregister() {
        Slog.d(TAG, "TaskPositioner: unregister()");
        if (DEBUG_TASK_POSITIONING) {
            Slog.d(TAG, "Unregistering task positioner");
        }

        if (mClientChannel == null) {
            Slog.e(TAG, "TaskPositioner: Task positioner not registered");
            return;
        }

        mService.mTaskPositioningController.hideInputSurface(mDisplayContent.getDisplayId());
        mService.mInputManager.removeInputChannel(mClientChannel.getToken());

        mInputEventReceiver.dispose();
        mInputEventReceiver = null;
        mClientChannel.dispose();
        mClientChannel = null;

        mDragWindowHandle = null;
        mDragApplicationHandle = null;
        mDragEnded = true;

        // Notify InputMonitor to remove mDragWindowHandle.
        mDisplayContent.getInputMonitor().updateInputWindowsLw(true /*force*/);

        // Resume rotations after a drag.
        mDisplayContent.getDisplayRotation().resume();
        mDisplayContent = null;
        if (mClientCallback != null) {
            mClientCallback.unlinkToDeath(this, 0 /* flags */);
        }
        mWindow = null;
    }

    /**
     * Starts moving or resizing the task. This method should be only called from
     * {@link TaskPositioningController#startPositioningLocked} or unit tests.
     */
    void startDrag(boolean resize, boolean preserveOrientation, float startX, float startY) {
        Slog.d(TAG, "TaskPositioner: startDrag(resize=" + resize
                + ", preserveOrientation=" + preserveOrientation
                + ", startX=" + startX + ", startY=" + startY + ")");
        if (DEBUG_TASK_POSITIONING) {
            Slog.d(TAG, "startDrag: win=" + mWindow + ", resize=" + resize
                    + ", preserveOrientation=" + preserveOrientation + ", {" + startX + ", "
                    + startY + "}");
        }
        // Use the bounds of the task which accounts for
        // multiple app windows. Don't use any bounds from win itself as it
        // may not be the same size as the task.
        final Rect startBounds = mTmpRect;
        mTask.getBounds(startBounds);

        mCtrlType = CTRL_NONE;
        mStartDragX = startX;
        mStartDragY = startY;
        mPreserveOrientation = preserveOrientation;

        if (resize) {
            if (startX < startBounds.left) {
                mCtrlType |= CTRL_LEFT;
            }
            if (startX > startBounds.right) {
                mCtrlType |= CTRL_RIGHT;
            }
            if (startY < startBounds.top) {
                mCtrlType |= CTRL_TOP;
            }
            if (startY > startBounds.bottom) {
                mCtrlType |= CTRL_BOTTOM;
            }
            mResizing = mCtrlType != CTRL_NONE;
        }

        // In case of !isDockedInEffect we are using the union of all task bounds. These might be
        // made up out of multiple windows which are only partially overlapping. When that happens,
        // the orientation from the window of interest to the entire stack might diverge. However
        // for now we treat them as the same.
        mStartOrientationWasLandscape = startBounds.width() >= startBounds.height();
        mWindowOriginalBounds.set(startBounds);

        // Notify the app that resizing has started, even though we haven't received any new
        // bounds yet. This will guarantee that the app starts the backdrop renderer before
        // configuration changes which could cause an activity restart.
        if (mResizing) {
            Slog.d(TAG, "TaskPositioner: startDrag - resizing, notifyMoveLocked");
            notifyMoveLocked(startX, startY);

            // The WindowPositionerEventReceiver callbacks are delivered on the same handler so this
            // initial resize is always guaranteed to happen before subsequent drag resizes.
            mService.mH.post(() -> {
                Slog.d(TAG, "TaskPositioner: startDrag - posting resizeTask");
                mService.mAtmService.resizeTask(
                        mTask.mTaskId, startBounds, RESIZE_MODE_USER_FORCED);
            });
        }

        // Make sure we always have valid drag bounds even if the drag ends before any move events
        // have been handled.
        mWindowDragBounds.set(startBounds);
    }

    private void endDragLocked() {
        Slog.d(TAG, "TaskPositioner: endDragLocked()");
        mResizing = false;
        mTask.setDragResizing(false);
    }

    /** Returns true if the move operation should be ended. */
    @VisibleForTesting
    boolean notifyMoveLocked(float x, float y) {
        Slog.d(TAG, "TaskPositioner: notifyMoveLocked(x=" + x + ", y=" + y + ")");
        if (DEBUG_TASK_POSITIONING) {
            Slog.d(TAG, "notifyMoveLocked: {" + x + "," + y + "}");
        }

        if (mCtrlType != CTRL_NONE) {
            Slog.d(TAG, "TaskPositioner: notifyMoveLocked - resizing (resizeDrag)");
            resizeDrag(x, y);
            mTask.setDragResizing(true);
            return false;
        }

        // This is a moving or scrolling operation.
        // Only allow to move in stable area so the target window won't be covered by system bar.
        // Though {@link Task#resolveOverrideConfiguration} should also avoid the case.
        mDisplayContent.getStableRect(mTmpRect);
        // The task may be put in a limited display area.
        mTmpRect.intersect(mTask.getRootTask().getParent().getBounds());

        int nX = (int) x;
        int nY = (int) y;
        if (!mTmpRect.contains(nX, nY)) {
            Slog.d(TAG, "TaskPositioner: notifyMoveLocked - pointer out of bounds, clamping");
            // For a moving operation we allow the pointer to go out of the stack bounds, but
            // use the clamped pointer position for the drag bounds computation.
            nX = Math.min(Math.max(nX, mTmpRect.left), mTmpRect.right);
            nY = Math.min(Math.max(nY, mTmpRect.top), mTmpRect.bottom);
        }

        updateWindowDragBounds(nX, nY, mTmpRect);
        return false;
    }

    /**
     * The user is drag - resizing the window.
     *
     * @param x The x coordinate of the current drag coordinate.
     * @param y the y coordinate of the current drag coordinate.
     */
    @VisibleForTesting
    void resizeDrag(float x, float y) {
        Slog.d(TAG, "TaskPositioner: resizeDrag(x=" + x + ", y=" + y + ")");
        updateDraggedBounds(TaskResizingAlgorithm.resizeDrag(x, y, mStartDragX, mStartDragY,
                mWindowOriginalBounds, mCtrlType, mMinVisibleWidth, mMinVisibleHeight,
                mMaxVisibleSize, mPreserveOrientation, mStartOrientationWasLandscape));
    }

    private void updateDraggedBounds(Rect newBounds) {
        Slog.d(TAG, "TaskPositioner: updateDraggedBounds(newBounds=" + newBounds + ")");
        mWindowDragBounds.set(newBounds);

        checkBoundsForOrientationViolations(mWindowDragBounds);
    }

    /**
     * Validate bounds against orientation violations (if DEBUG_ORIENTATION_VIOLATIONS is set).
     *
     * @param bounds The bounds to be checked.
     */
    private void checkBoundsForOrientationViolations(Rect bounds) {
        Slog.d(TAG, "TaskPositioner: checkBoundsForOrientationViolations(bounds=" + bounds + ")");
        // When using debug check that we are not violating the given constraints.
        if (DEBUG_ORIENTATION_VIOLATIONS) {
            if (mStartOrientationWasLandscape != (bounds.width() >= bounds.height())) {
                Slog.e(TAG, "Orientation violation detected! should be "
                        + (mStartOrientationWasLandscape ? "landscape" : "portrait")
                        + " but is the other");
            } else {
                Slog.v(TAG, "new bounds size: " + bounds.width() + " x " + bounds.height());
            }
            if (mMinVisibleWidth > bounds.width() || mMinVisibleHeight > bounds.height()) {
                Slog.v(TAG, "Minimum requirement violated: Width(min, is)=(" + mMinVisibleWidth
                        + ", " + bounds.width() + ") Height(min,is)=("
                        + mMinVisibleHeight + ", " + bounds.height() + ")");
            }
            if (mMaxVisibleSize.x < bounds.width() || mMaxVisibleSize.y < bounds.height()) {
                Slog.v(TAG, "Maximum requirement violated: Width(min, is)=(" + mMaxVisibleSize.x
                        + ", " + bounds.width() + ") Height(min,is)=("
                        + mMaxVisibleSize.y + ", " + bounds.height() + ")");
            }
        }
    }

    private void updateWindowDragBounds(int x, int y, Rect rootTaskBounds) {
        Slog.d(TAG, "TaskPositioner: updateWindowDragBounds(x=" + x + ", y=" + y + ", rootTaskBounds=" + rootTaskBounds + ")");
        final int offsetX = Math.round(x - mStartDragX);
        final int offsetY = Math.round(y - mStartDragY);
        mWindowDragBounds.set(mWindowOriginalBounds);
        // Horizontally, at least mMinVisibleWidth pixels of the window should remain visible.
        final int maxLeft = rootTaskBounds.right - mMinVisibleWidth;
        final int minLeft = rootTaskBounds.left + mMinVisibleWidth - mWindowOriginalBounds.width();

        // Vertically, the top mMinVisibleHeight of the window should remain visible.
        // (This assumes that the window caption bar is at the top of the window).
        final int minTop = rootTaskBounds.top;
        final int maxTop = rootTaskBounds.bottom - mMinVisibleHeight;

        mWindowDragBounds.offsetTo(
                Math.min(Math.max(mWindowOriginalBounds.left + offsetX, minLeft), maxLeft),
                Math.min(Math.max(mWindowOriginalBounds.top + offsetY, minTop), maxTop));

        if (DEBUG_TASK_POSITIONING) Slog.d(TAG,
                "updateWindowDragBounds: " + mWindowDragBounds);
    }

    public String toShortString() {
        Slog.d(TAG, "TaskPositioner: toShortString()");
        return TAG;
    }

    static void setFactory(Factory factory) {
        Slog.d(TAG, "TaskPositioner: setFactory(" + factory + ")");
        sFactory = factory;
    }

    static TaskPositioner create(WindowManagerService service) {
        Slog.d(TAG, "TaskPositioner: create(" + service + ")");
        if (sFactory == null) {
            sFactory = new Factory() {};
        }

        return sFactory.create(service);
    }

    @Override
    public void binderDied() {
        Slog.d(TAG, "TaskPositioner: binderDied()");
        mService.mTaskPositioningController.finishTaskPositioning();
    }

    interface Factory {
        default TaskPositioner create(WindowManagerService service) {
            Slog.d(TAG, "TaskPositioner$Factory: create(" + service + ")");
            return new TaskPositioner(service);
        }
    }
}
