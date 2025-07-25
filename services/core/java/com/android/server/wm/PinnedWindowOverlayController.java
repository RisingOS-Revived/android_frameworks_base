/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import static com.android.server.wm.PopUpWindowController.MOVE_TO_BACK_TOUCH_OUTSIDE;
import static com.android.server.wm.WindowResizingAlgorithm.BOUNDARY_GAP;

import static org.rising.DebugConstants.DEBUG_POP_UP;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Slog;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.IWindow;
import android.view.InsetsState;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.WindowManagerPolicyConstants;
import android.widget.ImageButton;

import com.android.internal.R;

import com.android.server.wm.ActivityRecord;
import com.android.server.wm.DisplayContent;
import com.android.server.wm.Task;
import com.android.server.wm.WindowManagerService;

class PinnedWindowOverlayController {

    private static final String TAG = "PinnedWindowOverlayController";
    static final String WIN_TITLE = "PinnedWindowOverlayView";

    private static final int MSG_ADD_OVERLAY_WINDOW = 0;
    private static final int MSG_UPDATE_OVERLAY_WINDOW = 1;
    private static final int MSG_HIDE_OVERLAY_WINDOW = 2;

    private final Rect mBound;
    private final Rect mRemoveBound;
    private final float[] mVels;
    private final WindowManagerPolicyConstants.PointerEventListener mPointerListener;

    private final H mHandler;
    private final HandlerThread mHandlerThread;

    private AudioManager mAudioManager;
    private Context mContext;
    private GestureDetector mGestureDetector;

    private View mMenuButtonContainer;
    private ImageButton mMuteButton;
    private ImageButton mScaleButton;
    private LayoutParams mParams;

    private int mDragSlop;
    private boolean mCheckForDragging;
    private boolean mDragging;
    private boolean mIsMute;
    private boolean mIsSizeSmall;
    private boolean mIsTouchInWindow;
    private int mTouchDownX;
    private int mTouchDownY;

    private VelocityTracker mVelocityTracker;
    private PinnedWindowOverlayView mView;
    private WindowManager mWindowManager;
    private WindowManagerService mWmService;

    private Task mTask;
    private TaskWindowSurfaceInfo mTaskWindowSurfaceInfo;

    private static class InstanceHolder {
        private static final PinnedWindowOverlayController INSTANCE = new PinnedWindowOverlayController();
    }

    private class H extends Handler {
        H(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ADD_OVERLAY_WINDOW:
                    if (DEBUG_POP_UP) {
                        Slog.d(TAG, "updateWindowState: handle add");
                    }
                    addView();
                    break;
                case MSG_UPDATE_OVERLAY_WINDOW:
                    updateView();
                    break;
                case MSG_HIDE_OVERLAY_WINDOW:
                    if (DEBUG_POP_UP) {
                        Slog.d(TAG, "hide overlay window view");
                    }
                    if (mView != null) {
                        mView.setVisibility(View.GONE);
                    }
                    break;
            }
        }
    }

    static PinnedWindowOverlayController getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private PinnedWindowOverlayController() {
        mIsSizeSmall = true;
        mIsMute = false;
        mDragging = false;
        mIsTouchInWindow = false;
        mVels = new float[2];
        mBound = new Rect();
        mRemoveBound = new Rect();
        mHandlerThread = new HandlerThread("PinnedWindowOverlayHandler");
        mHandlerThread.start();
        mHandler = new H(mHandlerThread.getLooper());
        mPointerListener = event -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (mBound.contains((int) event.getX(), (int) event.getY()) && !isMenuViewShowing()) {
                        mIsTouchInWindow = true;
                        if (mTask != null && mVelocityTracker == null) {
                            mVelocityTracker = VelocityTracker.obtain();
                        }
                        if (mVelocityTracker != null) {
                            mVelocityTracker.clear();
                            mVelocityTracker.addMovement(event);
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (mVelocityTracker != null && mIsTouchInWindow) {
                        if (mRemoveBound.contains((int) event.getX(), (int) event.getY()) && !isMenuViewShowing()) {
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_HIDE_OVERLAY_WINDOW));
                        }
                        computeCurrentVelocity();
                        mVelocityTracker.recycle();
                        mVelocityTracker = null;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mVelocityTracker != null && mIsTouchInWindow) {
                        mVelocityTracker.addMovement(event);
                    }
                    break;
            }
        };
    }

    void init(Context context, Looper looper, WindowManagerService wms) {
        mContext = context;
        mWmService = wms;
        mAudioManager = context.getSystemService(AudioManager.class);
        mWindowManager = context.getSystemService(WindowManager.class);
        mDragSlop = context.getResources().getDimensionPixelSize(R.dimen.config_viewConfigurationTouchSlop);
    }

    void systemReady() {
        mGestureDetector = new GestureDetector(mContext, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent event) {
                if (DEBUG_POP_UP) {
                    Slog.d(TAG, "onLongPress: event=" + event);
                }
                mHandler.post(() -> mMenuButtonContainer.setVisibility(View.VISIBLE));
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent event) {
                if (DEBUG_POP_UP) {
                    Slog.d(TAG, "onSingleTapUp: event=" + event);
                }
                enterMiniWindowingMode();
                setTask(null);
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent event) {
                if (DEBUG_POP_UP) {
                    Slog.d(TAG, "onDoubleTap: event=" + event);
                }
                exitPinnedWindowingMode();
                setTask(null);
                return true;
            }
        });
    }

    Task getTask() {
        return mTask;
    }

    void setTask(Task task) {
        if (mTask == task) {
            return;
        }
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "setTask: " + (task != null ? task : "null"));
        }
        mTask = task;
        mTaskWindowSurfaceInfo = getTaskWindowSurfaceInfo();
        final Task rootTask = mTask != null ? mTask.getRootTask() : null;
        if (rootTask != null) {
            updateMenuIconState(rootTask);
        }
        mBound.setEmpty();
        updateWindowState(mTask != null);
    }

    boolean onTouchEvent(MotionEvent event) {
        if (mGestureDetector != null) {
            mGestureDetector.onTouchEvent(event);
        }

        final int x = (int) event.getX();
        final int y = (int) event.getY();
        final boolean fromMouse = event.getToolType(event.getActionIndex()) == MotionEvent.TOOL_TYPE_MOUSE;
        final int actionMasked = event.getActionMasked();
        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
                if (!fromMouse) {
                    if (DEBUG_POP_UP) {
                        Slog.d(TAG, "onTouchEvent: Down: " + event);
                    }
                    mCheckForDragging = true;
                    mTouchDownX = x;
                    mTouchDownY = y;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mDragging) {
                    if (actionMasked == MotionEvent.ACTION_UP && mWmService != null) {
                        final IWindow window = getIWindow();
                        if (window != null) {
                            mWmService.mTaskPositioningController.finishTaskPositioning(window);
                        }
                    }
                    mDragging = false;
                    return !mCheckForDragging;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!mDragging && mCheckForDragging && ((fromMouse || passedSlop(x, y)) && !isMenuViewShowing())) {
                    mCheckForDragging = false;
                    mDragging = true;
                    if (mWmService != null) {
                        final IWindow window = getIWindow();
                        if (window != null) {
                            mWmService.mTaskPositioningController.startMovingTask(window,
                                    event.getRawX(), event.getRawY());
                        }
                    }
                    if (mTaskWindowSurfaceInfo != null && mTaskWindowSurfaceInfo.cancelPopUpViewAnimation()) {
                        if (DEBUG_POP_UP) {
                            Slog.d(TAG, "PopUpView: cancel PopUpViewAnimation when start drag. mTask=" + mTask);
                        }
                    }
                }
                if (mTask != null && mTask.getWindowConfiguration().isPinnedExtWindowMode()
                        && mTask.mSurfaceAnimator.hasLeash()) {
                    if (DEBUG_POP_UP) {
                        Slog.d(TAG, "PopUpView: cancel Window animation when start drag. mTask=" + mTask);
                    }
                    mTask.cancelAnimation();
                }
                break;
            case MotionEvent.ACTION_OUTSIDE:
                if (mMenuButtonContainer != null) {
                    mMenuButtonContainer.setVisibility(View.GONE);
                }
                return true;
        }
        return mDragging || mCheckForDragging;
    }

    void updateWindowState(boolean show) {
        if (show && mView == null) {
            mHandler.sendEmptyMessage(MSG_ADD_OVERLAY_WINDOW);
        } else if (!show) {
            mHandler.sendEmptyMessage(MSG_HIDE_OVERLAY_WINDOW);
        } else {
            mHandler.sendEmptyMessage(MSG_UPDATE_OVERLAY_WINDOW);
        }
    }

    private TaskWindowSurfaceInfo getTaskWindowSurfaceInfo() {
        if (mTask != null) {
            return mTask.mWindowContainerExt.getTaskWindowSurfaceInfo();
        }
        return null;
    }

    private void updateMenuIconState(Task rootTask) {
        final TaskWindowSurfaceInfo info = rootTask.mWindowContainerExt.getTaskWindowSurfaceInfo();
        mIsSizeSmall = info.isPinnedWindowSmall();
        mIsMute = info.getMute();
        if (mView != null) {
            mView.updateScaleIconResource(mIsSizeSmall);
            mView.updateMuteIconResource(mIsMute);
        }
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "updateMenuIconState: task=" + rootTask +
                    ", mIsSizeSmall=" + mIsSizeSmall + ", mIsMute=" + mIsMute);
        }
    }

    private void addView() {
        if (mTaskWindowSurfaceInfo != null && mView == null) {
            mView = (PinnedWindowOverlayView) LayoutInflater.from(mContext)
                    .inflate(R.layout.pinned_window_overlay, null);
            mWmService.registerPointerEventListener(mPointerListener, mContext.getDisplayId());
            final Rect taskBound = mTaskWindowSurfaceInfo.getTaskWindowSurfaceBounds();
            setUpWindow(taskBound);
            mBound.set(taskBound);
            setUpMenuView();
        }
        if (mView != null) {
            mView.updateScaleIconResource(mIsSizeSmall);
            mView.updateMuteIconResource(mIsMute);
            mView.setVisibility(View.VISIBLE);
        }
    }

    private void setUpWindow(Rect taskBound) {
        mParams = new LayoutParams();
        mParams.type = LayoutParams.TYPE_APPLICATION_OVERLAY;
        mParams.format = PixelFormat.RGBA_8888;
        mParams.flags = LayoutParams.FLAG_NOT_FOCUSABLE |
                        LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                        LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        mParams.privateFlags = LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS |
                               LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY;
        mParams.layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        mParams.setFitInsetsTypes(0);
        mParams.gravity = Gravity.LEFT | Gravity.TOP;
        mParams.setTitle(WIN_TITLE);

        if (mWindowManager != null) {
            mParams.x = taskBound.left;
            mParams.y = taskBound.top;
            mParams.width = taskBound.width();
            mParams.height = taskBound.height();
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "addView: bound = " + taskBound);
            }
            mWindowManager.addView(mView, mParams);
            final WindowInsetsController insetsController = mView.getWindowInsetsController();
            if (insetsController != null) {
                insetsController.hide(WindowInsets.Type.statusBars());
                insetsController.hide(WindowInsets.Type.navigationBars());
            }
        }
    }

    private void setUpMenuView() {
        mMenuButtonContainer = mView.findViewById(R.id.pinned_window_menu_container);
        mMenuButtonContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                mMenuButtonContainer.setVisibility(View.GONE);
            }
        });
        mScaleButton = (ImageButton) mView.findViewById(R.id.pinned_window_menu_scale_button);
        mScaleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                PopUpWindowController.getInstance().triggerVibrate();
                mMenuButtonContainer.setVisibility(View.GONE);
                triggerPinnedWindowResize();
                updateWindowState(true);
                mView.updateScaleIconResource(mIsSizeSmall);
            }
        });
        mMuteButton = (ImageButton) mView.findViewById(R.id.pinned_window_menu_mute_button);
        mMuteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                PopUpWindowController.getInstance().triggerVibrate();
                mMenuButtonContainer.setVisibility(View.GONE);
                triggerPinnedWindowMute();
                mView.updateMuteIconResource(mIsMute);
            }
        });
        mView.updateResources(mBound, mIsSizeSmall);
    }

    private void updateView() {
        if (mTaskWindowSurfaceInfo == null) {
            return;
        }
        final Rect taskBound = mTaskWindowSurfaceInfo.getTaskWindowSurfaceBounds();
        if (mView != null && !mBound.equals(taskBound) && mWindowManager != null) {
            mBound.set(taskBound);
            mParams.x = taskBound.left;
            mParams.y = taskBound.top;
            mParams.width = taskBound.width();
            mParams.height = taskBound.height();
            mView.updateResources(taskBound, mIsSizeSmall);
            mWindowManager.updateViewLayout(mView, mParams);
        }
        if (mView != null) {
            mView.setVisibility(View.VISIBLE);
        }
    }

    private IWindow getIWindow() {
        synchronized (mWmService.mAtmService.mGlobalLock) {
            if (mTask != null && mTask.getTopVisibleAppMainWindow() != null) {
                return mTask.getTopVisibleAppMainWindow().getIWindow();
            }
            return null;
        }
    }

    private void exitPinnedWindowingMode() {
        if (mTask != null) {
            PopUpWindowController.getInstance().exitPinnedWindowingMode(
                    mWmService.mAtmService, mTask.getTopVisibleAppMainWindow());
        }
    }

    private void enterMiniWindowingMode() {
        if (mTask != null) {
            PopUpWindowController.getInstance().enterMiniWindowingMode(mTask.getTopVisibleAppMainWindow());
        }
    }

    private void triggerPinnedWindowResize() {
        if (mTask == null) {
            return;
        }
        synchronized (mTask.mAtmService.mGlobalLock) {
            final Task rootTask = mTask != null ? mTask.getRootTask() : null;
            if (rootTask != null && rootTask.getWindowConfiguration().isPinnedExtWindowMode()) {
                final TaskWindowSurfaceInfo info = rootTask.mWindowContainerExt.getTaskWindowSurfaceInfo();
                mIsSizeSmall = info.isPinnedWindowSmall();
                final Point startPos = getPosition(rootTask);
                final float startWinScale = info.getWindowSurfaceRealScale();
                info.setWindowSurfaceScale(
                        WindowResizingAlgorithm.getDefaultPinnedWindowScale(
                                rootTask.getConfiguration().orientation, !mIsSizeSmall));
                mIsSizeSmall = !mIsSizeSmall;
                final Rect surfaceBounds = info.getTaskWindowSurfaceBounds();
                final Rect bounds = new Rect();
                rootTask.getBounds(bounds);
                final Rect displayBound = new Rect();
                if (rootTask.mDisplayContent != null) {
                    final InsetsState state = rootTask.mDisplayContent.getInsetsStateController().getRawInsetsState();
                    displayBound.set(state.getDisplayFrame());
                    displayBound.inset(state.calculateInsets(
                            displayBound, WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout(), true));
                }
                final Rect boundaryGap = info.getWindowBoundaryGap();
                boundaryGap.bottom = BOUNDARY_GAP;
                boundaryGap.top = surfaceBounds.top <= displayBound.top + BOUNDARY_GAP ? BOUNDARY_GAP : 0;
                if (surfaceBounds.bottom < displayBound.bottom - BOUNDARY_GAP) {
                    boundaryGap.bottom = 0;
                }
                final Point pos = new Point();
                WindowResizingAlgorithm.getCenterByBoundaryGap(
                        bounds, displayBound, boundaryGap,
                        info.getPinnedWindowVerticalPosRatio(displayBound),
                        info.getWindowCenterPosition(),
                        info.getWindowSurfaceScale(), pos);
                info.setWindowCenterPosition(pos);
                info.setPinnedWindowVerticalPosRatio(
                        info.getWindowCenterPosition(),
                        displayBound, false);
                final Point endPos = getPosition(rootTask);
                final float endWinScale = info.getWindowSurfaceRealScale();
                info.playToggleResizeWindowAnimation(startPos, endPos,
                        startWinScale, endWinScale, new PopUpAnimationController.OnAnimationEndCallback() {
                    @Override
                    public final void onAnimationEnded() {
                        synchronized (mTask.mAtmService.mGlobalLock) {
                            mTask.mAtmService.mWindowManager.requestTraversal();
                        }
                    }
                });
            }
        }
    }

    void triggerPinnedWindowMute(Task task) {
        if (task == null) {
            return;
        }
        synchronized (task.mAtmService.mGlobalLock) {
            final Task rootTask = task != null ? task.getRootTask() : null;
            final ActivityRecord topActivity = rootTask != null ?
                    rootTask.getTopNonFinishingActivity() : null;
            if (rootTask != null && topActivity != null) {
                final TaskWindowSurfaceInfo info = rootTask.mWindowContainerExt.getTaskWindowSurfaceInfo();
                final boolean success = setPinnedWindowMuted(
                        topActivity.info.applicationInfo.packageName,
                        !info.getMute());
                if (success) {
                    info.toggleMute();
                }
                mIsMute = info.getMute();
            }
        }
    }

    private void triggerPinnedWindowMute() {
        if (mTask == null) {
            return;
        }
        synchronized (mTask.mAtmService.mGlobalLock) {
            final Task rootTask = mTask != null ? mTask.getRootTask() : null;
            final ActivityRecord topActivity = rootTask != null ?
                    rootTask.getTopNonFinishingActivity() : null;
            if (rootTask != null && topActivity != null) {
                final TaskWindowSurfaceInfo info = rootTask.mWindowContainerExt.getTaskWindowSurfaceInfo();
                final boolean success = setPinnedWindowMuted(
                        topActivity.info.applicationInfo.packageName,
                        !info.getMute());
                if (success) {
                    info.toggleMute();
                }
                mIsMute = info.getMute();
            }
        }
    }

    void triggerTmpWindowMute(Task task, boolean mute) {
        if (task == null) {
            return;
        }
        synchronized (task.mAtmService.mGlobalLock) {
            final Task rootTask = task != null ? task.getRootTask() : null;
            final ActivityRecord topActivity = rootTask != null ?
                    rootTask.getTopNonFinishingActivity() : null;
            if (rootTask != null && topActivity != null) {
                setPinnedWindowMuted(
                        topActivity.info.applicationInfo.packageName, mute);
            }
        }
    }

    private boolean setPinnedWindowMuted(String packageName, boolean mute) {
        return mAudioManager.setAppMute(packageName, mute) == AudioManager.SUCCESS;
    }

    boolean isOverlayViewShowing() {
        return mView != null && mView.getVisibility() == View.VISIBLE;
    }

    private boolean isMenuViewShowing() {
        return isOverlayViewShowing() && mMenuButtonContainer != null && mMenuButtonContainer.getVisibility() == View.VISIBLE;
    }

    private boolean passedSlop(int x, int y) {
        return Math.abs(x - mTouchDownX) > mDragSlop || Math.abs(y - mTouchDownY) > mDragSlop;
    }

    private Point getPosition(Task rootTask) {
        final Point pos = new Point();
        if (rootTask != null) {
            final DisplayContent displayContent = mWmService.getDefaultDisplayContentLocked();
            final Rect displayBounds = new Rect();
            displayContent.getBounds(displayBounds);
            final Rect bounds = new Rect();
            rootTask.getBounds(bounds);
            final TaskWindowSurfaceInfo info = rootTask.mWindowContainerExt.getTaskWindowSurfaceInfo();
            info.setWindowSurfaceScaleFactor(
                    WindowResizingAlgorithm.getPositionAndScaleFactorForTask(
                            bounds, displayBounds, info.getWindowCenterPosition(),
                            info.getWindowSurfaceScale(),
                            rootTask.getWindowConfiguration().isPinnedExtWindowMode(), pos));
        }
        return pos;
    }

    float[] computeCurrentVelocity() {
        synchronized (this) {
            if (mVelocityTracker != null && mIsTouchInWindow) {
                mVelocityTracker.computeCurrentVelocity(1000,
                        ViewConfiguration.get(mWmService.mContext).getScaledMaximumFlingVelocity());
                mVels[0] = mVelocityTracker.getXVelocity();
                mVels[1] = mVelocityTracker.getYVelocity();
            }
            mIsTouchInWindow = false;
        }
        return mVels;
    }

    void setRemoveBound(Rect buttonBound) {
        mRemoveBound.set(buttonBound);
    }
}
