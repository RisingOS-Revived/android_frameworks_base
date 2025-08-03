/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MINI_WINDOW_EXT;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED_WINDOW_EXT;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.os.Process.THREAD_PRIORITY_DEFAULT;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_MINI_WINDOW_DIMMER;
import static android.view.WindowManager.LayoutParams.TYPE_PINNED_WINDOW_DISMISS_HINT;
import static android.window.TransitionInfo.FLAG_EXIT_POP_UP_VIEW_BY_DRAG;
import static android.window.TransitionInfo.FLAG_EXIT_POP_UP_VIEW_DISPLAY_ROTATION;
import static android.window.TransitionInfo.FLAG_LAUNCH_POP_UP_VIEW_FROM_RECENTS;
import static android.window.TransitionInfo.FLAG_SCHEDULE_POP_UP_VIEW;

import static com.android.server.wm.Transition.ChangeInfo.FLAG_CHANGE_SHOULD_SKIP_TRANSITIONS;

import static org.rising.DebugConstants.DEBUG_POP_UP;
import static org.rising.view.PopUpViewManager.FEATURE_SUPPORTED;

import android.app.ActivityOptions;
import android.app.WindowConfiguration;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.om.IOverlayManager;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.util.ArraySet;
import android.util.Slog;
import android.view.IWindow;
import android.view.InsetsSource;
import android.annotation.IntDef;
import java.lang.annotation.Retention;
import android.view.InsetsState;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.WindowInsets.Type;
import android.window.TransitionInfo;
import android.window.TransitionInfo.Change;

import com.android.server.ServiceThread;
import com.android.server.wm.ActivityStarter.Request;
import com.android.server.wm.LaunchParamsController.LaunchParams;
import com.android.server.wm.Transition.ChangeInfo;

import com.google.android.collect.Sets;
import java.lang.annotation.RetentionPolicy;

import java.util.ArrayList;

import org.rising.view.DisplayResolutionManager;
import org.rising.view.IDisplayResolutionListener;
import org.rising.view.PopUpViewManager;

public class PopUpWindowController {

    private static final String TAG = "PopUpWindowController";

    public static final String PACKAGE_NAME_SYSTEM_TOOL = "org.sun.systemtool";

    private static final String PACKAGE_NAME_PIXEL_LAUNCHER_OVERLAY =
            "com.google.android.apps.nexuslauncher.pop_up.overlay";

    public static final int REORDER_KEEP_IN_PLACE = 0;
    public static final int REORDER_MOVE_TO_TOP = 1;
    public static final int REORDER_MOVE_TO_ORIGINAL_POSITION = 2;

    @IntDef(value = { REORDER_KEEP_IN_PLACE, REORDER_MOVE_TO_TOP, REORDER_MOVE_TO_ORIGINAL_POSITION })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ReorderMode {}

    static final int MOVE_TO_BACK_TOUCH_OUTSIDE = 0;
    static final int MOVE_TO_BACK_FROM_LEAVE_BUTTON = 1;
    static final int MOVE_TO_BACK_NEW_MINI = 2;
    static final int MOVE_TO_BACK_NEW_PIN = 3;
    static final int MOVE_TO_BACK_NON_USER = 4;

    private static final long EXIT_POP_UP_DELAY = 200L;

    private static final int ID_DISPLAY_CUTOUT_LEFT = InsetsSource.createId(null, 0, Type.displayCutout());
    private static final int ID_DISPLAY_CUTOUT_TOP = InsetsSource.createId(null, 1, Type.displayCutout());
    private static final int ID_DISPLAY_CUTOUT_RIGHT = InsetsSource.createId(null, 2, Type.displayCutout());
    private static final int ID_DISPLAY_CUTOUT_BOTTOM = InsetsSource.createId(null, 3, Type.displayCutout());

    private final IDisplayResolutionListener.Stub mDisplayResolutionListener =
            new IDisplayResolutionListener.Stub() {
        @Override
        public void onDisplayResolutionChanged(int width, int height) {
            DimmerWindow.getInstance().onDensityChanged();
        }
    };

    private final Handler mHandler;
    private final ServiceThread mServiceThread;

    private ActivityTaskManagerService mAtmService;
    private Context mContext;
    private Vibrator mVibrator;
    private WindowManagerService mService;

    private SurfaceControl.Transaction mTransaction;

    private boolean mSkipNextTransitionFreeze;
    private boolean mTryExitWindowingMode;
    private boolean mTryExitWindowingModeByDrag;
    private boolean mLaunchPopUpViewFromRecents;
    private boolean mNextRecentIsPin;

    private WindowState mDimWinState = null;

    private static class InstanceHolder {
        private static final PopUpWindowController INSTANCE = new PopUpWindowController();
    }

    public static PopUpWindowController getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private PopUpWindowController() {
        mServiceThread = new ServiceThread(TAG, THREAD_PRIORITY_DEFAULT, false);
        mServiceThread.start();
        mHandler = new Handler(mServiceThread.getLooper());
    }

    public void init(Context context, WindowManagerService wms) {
        mContext = context;
        mService = wms;
        mAtmService = mService.mAtmService;
        mTransaction = mService.mTransactionFactory.get();
        mVibrator = mContext.getSystemService(Vibrator.class);
        PinnedWindowOverlayController.getInstance().init(mContext, mService.mH.getLooper(), wms);
    }

    void systemReady() {
        final DisplayResolutionManager drm =
                mContext.getSystemService(DisplayResolutionManager.class);
        drm.registerDisplayResolutionListener(mDisplayResolutionListener);
        PinnedWindowOverlayController.getInstance().systemReady();
        PopUpSettingsConfig.getInstance().init(mContext, mHandler);
        PopUpAppStarter.getInstance().init(mContext);
        PopUpBroadcastReceiver.getInstance().init(mContext, mHandler);

        final IOverlayManager om = IOverlayManager.Stub.asInterface(
                ServiceManager.getService(Context.OVERLAY_SERVICE));
        try {
            om.setEnabled(PACKAGE_NAME_PIXEL_LAUNCHER_OVERLAY, FEATURE_SUPPORTED, UserHandle.USER_SYSTEM);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to apply pop-up view overlay for pixel launcher");
        }
    }

    void onWindowAdd(ConfigurationContainer newParent, WindowState win) {
        if (newParent == null) {
            return;
        }
        final DisplayContent displayContent = win.getDisplayContent();
        if (displayContent.getDisplayId() == DEFAULT_DISPLAY &&
                win.mAttrs.type == TYPE_MINI_WINDOW_DIMMER) {
            mDimWinState = win;
            displayContent.assignWindowLayers(false);
        }
    }

    void onWindowRemove(WindowState win) {
        if (win.mAttrs.type == TYPE_MINI_WINDOW_DIMMER) {
            mDimWinState = null;
        }
    }

    boolean onWindowTokenAssignLayer(WindowToken token, SurfaceControl.Transaction t, int layer) {
        if (token.windowType != TYPE_MINI_WINDOW_DIMMER &&
                token.windowType != TYPE_PINNED_WINDOW_DISMISS_HINT) {
            return false;
        }
        if (token.mSurfaceControl == null) {
            return true;
        }
        if (mAtmService.isSleepingOrShuttingDownLocked()) {
            t.hide(token.mSurfaceControl);
        } else {
            t.show(token.mSurfaceControl);
            final DisplayContent displayContent = token.getDisplayContent();
            if (displayContent != null) {
                final Task targetTask;
                if (token.windowType == TYPE_PINNED_WINDOW_DISMISS_HINT) {
                    targetTask = PinnedWindowOverlayController.getInstance().getTask();
                } else {
                    targetTask = DimmerWindow.getInstance().getTask();
                }
                if (targetTask != null && targetTask.mSurfaceControl != null) {
                    if ((token.windowType == TYPE_PINNED_WINDOW_DISMISS_HINT ||
                            !targetTask.mWindowContainerExt.isFinishTopTask()) &&
                            targetTask.isAlwaysOnTop()) {
                        t.setRelativeLayer(token.mSurfaceControl, targetTask.mSurfaceControl, -1);
                    } else {
                        mTransaction.setLayer(token.mSurfaceControl, 1);
                        mTransaction.apply();
                    }
                }
            }
        }
        return true;
    }

    void onRotationChanged(Task task) {
        if (task.mWindowContainerExt.getTaskWindowSurfaceInfo() != null) {
            task.mWindowContainerExt.getTaskWindowSurfaceInfo().onRotationChanged();
        }
    }

    void onPrepareSurfaces(Task task, SurfaceControl.Transaction t) {
        if (task.mWindowContainerExt.getTaskWindowSurfaceInfo() != null) {
            task.mWindowContainerExt.getTaskWindowSurfaceInfo().onPrepareSurfaces(t);
        }
    }

    void onUserSwitched() {
        PopUpSettingsConfig.getInstance().updateAll();
        // Only proceed if service is properly initialized
        if (mService != null && mService.mSystemReady) {
            findAndExitAllPopUp();
        }
    }

    int getChangeFlags(ChangeInfo info, int flags) {
        if (shouldStartChangeTransition(info.mWindowingMode, info.mContainer.getWindowingMode())) {
            flags |= FLAG_SCHEDULE_POP_UP_VIEW;
            if (mLaunchPopUpViewFromRecents) {
                flags |= FLAG_LAUNCH_POP_UP_VIEW_FROM_RECENTS;
            }
            if (mTryExitWindowingModeByDrag) {
                flags |= FLAG_EXIT_POP_UP_VIEW_BY_DRAG;
            }
        }
        if (WindowConfiguration.isPopUpWindowMode(info.mContainer.getWindowingMode())
                && (info.mIsKeyguardGoingAway || info.mIsMoveTaskToBack)) {
            info.mFlags |= FLAG_CHANGE_SHOULD_SKIP_TRANSITIONS;
        }
        return flags;
    }

    DisplayContent getDefaultDisplayContent() {
        if (mService == null) {
            Slog.w(TAG, "getDefaultDisplayContent: mService is null");
            return null;
        }
        return mService.getDefaultDisplayContentLocked();
    }

    WindowState getDimWinState() {
        return mDimWinState;
    }

    void getPopUpViewTouchOffset(Session session, IWindow window, float[] offsets) {
        synchronized (mService.mGlobalLock) {
            final long ident = Binder.clearCallingIdentity();
            try {
                final WindowState win = mService.windowForClientLocked(session, window, false);
                if (offsets != null && offsets.length == 4) {
                    offsets[0] = 0.0f;
                    offsets[1] = 0.0f;
                    offsets[2] = 1.0f;
                    offsets[3] = 1.0f;
                    if (win != null && win.getWindowConfiguration().isPopUpWindowMode() &&
                            win.mActivityRecord != null) {
                        final Task rootTask = win.mActivityRecord.getRootTask(task -> task != null);
                        if (rootTask != null && rootTask.mWindowContainerExt.getTaskWindowSurfaceInfo() != null) {
                            final TaskWindowSurfaceInfo info = rootTask.mWindowContainerExt.getTaskWindowSurfaceInfo();
                            final Rect rect = info.getTaskWindowSurfaceBounds();
                            offsets[0] = rect.left;
                            offsets[1] = rect.top;
                            final float scale = info.getWindowSurfaceRealScale();
                            offsets[2] = scale;
                            offsets[3] = scale;
                        }
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    void resetBounds(Task task, int currentWindowingMode, int preferredWindowingMode) {
        if (WindowConfiguration.isPopUpWindowMode(currentWindowingMode) &&
                !WindowConfiguration.isPopUpWindowMode(preferredWindowingMode)) {
            task.setBounds(null);
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "setBounds to null for windowing mode change: currentMode="
                        + currentWindowingMode + "->" + preferredWindowingMode);
            }
        }
    }

    void removeChild(Task task) {
        if (tryExitPopUpView(task, true, true, true)) {
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "removeChild: exit PopUpView window");
            }
        }
    }

    void anyTaskForId(Task targetRootTask, Task task) {
        if (targetRootTask.getWindowingMode() == WINDOWING_MODE_MULTI_WINDOW &&
                task.getWindowConfiguration().isPinnedExtWindowMode() &&
                tryExitPopUpView(task, true, true, true)) {
            mSkipNextTransitionFreeze = true;
            if (DEBUG_POP_UP) {
                Slog.d(TAG, task + " switch to multi-win mode, go fullscreen first");
            }
        }
    }

    void ensureActivityConfiguration(ActivityRecord r) {
        if (r.getRootTask() != null && r.shouldBeVisible()
                && r.getRootTask().getWindowConfiguration().isPinnedExtWindowMode()
                && r.getRootTask().getTopNonFinishingActivity() == r) {
            mAtmService.mH.post(() -> {
                synchronized (mAtmService.mGlobalLock) {
                    r.mWindowContainerExt.setOrientation(r.getRootTask());
                }
            });
        }
    }

    boolean shouldSkipAppFocusChanged(Task newTask) {
        if (newTask != null && !newTask.getWindowConfiguration().isPopUpWindowMode()
                && TopActivityRecorder.getInstance().hasMiniWindow()) {
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "skip fullscreen app focus change due to mini-window showing");
            }
            return true;
        }
        if (newTask != null && newTask.getWindowConfiguration().isPinnedExtWindowMode()) {
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "pinned window should not be focusable");
            }
            return true;
        }
        if (newTask != null && newTask.mWindowContainerExt.getFreezerSkipAnim()) {
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "task is under NoWindowModeAnim, should not be focusable");
            }
            return true;
        }
        return false;
    }

    boolean shouldSkipRemoteAnimation(boolean isChanging) {
        return isChanging && mTryExitWindowingMode;
    }

    void onAppFocusChanged(ActivityRecord newFocus, Task newTask) {
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "onAppFocusChanged: newTask=" + newTask);
        }
        if (newFocus != null && newTask != null &&
                newTask.getWindowConfiguration().isPopUpWindowMode()) {
            final Task rootTask = newTask.getRootTask();
            if (rootTask != null) {
                newFocus.mWindowContainerExt.setOrientation(rootTask);
            }
        }
        if (newTask != null && newTask.getWindowConfiguration().isMiniExtWindowMode()) {
            newTask.mWindowContainerExt.setFinishTopTask(false);
        }
    }

    void findAndExitAllPopUp() {
        final Task miniWinTask = DimmerWindow.getInstance().getTask();
        final Task pinnedWinTask = PinnedWindowOverlayController.getInstance().getTask();
        if (miniWinTask != null) {
            moveActivityTaskToBack(miniWinTask, MOVE_TO_BACK_TOUCH_OUTSIDE);
        }
        if (pinnedWinTask != null) {
            moveActivityTaskToBack(pinnedWinTask, MOVE_TO_BACK_TOUCH_OUTSIDE);
        }
    }

    private void moveActivityTaskToBackInner(Task task, Task fullTask) {
        ActivityRecord fullTaskActivity = fullTask.getResumedActivity();
        if (fullTaskActivity == null) {
            fullTaskActivity = fullTask.getTopActivity(true, true);
        }
        ActivityRecord taskActivity = task.getResumedActivity();
        if (taskActivity == null) {
            taskActivity = task.getTopActivity(true, true);
        }
        task.startPausing(true, false, fullTaskActivity, "PopUpWindowController.moveActivityTaskToBackInner");
        if (taskActivity != null && task.getDisplayContent() != null) {
            if (taskActivity.getTask() != null &&
                    (taskActivity.getTask() == task || taskActivity.getTask().getParent() == task)) {
                task.moveTaskToBack(taskActivity.getTask());
            }
            final ActivityRecord resumedActivity = task.mRootWindowContainer.getTopResumedActivity();
            if (resumedActivity != null && !resumedActivity.isSleeping()) {
                mAtmService.setLastResumedActivityUncheckLocked(
                        resumedActivity, "PopUpWindowController.moveActivityTaskToBackInner");
            }
        }
    }

    void moveActivityTaskToBack(Task task, int reason) {
        synchronized (mAtmService.mGlobalLock) {
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "moveActivityTaskToBack, task=" + (task != null ? task : "null")
                        + ", reason=" + reasonToString(reason));
            }
            if (task != null && task.mWindowContainerExt.getTaskWindowSurfaceInfo() != null) {
                if (reason == MOVE_TO_BACK_TOUCH_OUTSIDE) {
                    TopActivityRecorder.getInstance().clearMiniWindow();
                }
                final TaskWindowSurfaceInfo info = task.mWindowContainerExt.getTaskWindowSurfaceInfo();
                info.playExitAnimation(reason == MOVE_TO_BACK_FROM_LEAVE_BUTTON,
                        info.getWindowSurfaceRealScale(),
                        () -> {
                            synchronized (mAtmService.mGlobalLock) {
                                if (task.mDisplayContent == null) {
                                    task.mDisplayContent = mService.getDefaultDisplayContentLocked();
                                }
                                final Task fullTask = TopActivityRecorder.getInstance().getTopFullscreenTask();
                                if (fullTask != null) {
                                    task.setAlwaysOnTop(false);
                                    task.mDisplayContent.assignWindowLayers(true);
                                    moveActivityTaskToBackInner(task, fullTask);
                                }
                                mHandler.postDelayed(()-> {
                                    tryExitPopUpView(task, true, reason != MOVE_TO_BACK_NEW_MINI, reason != MOVE_TO_BACK_NEW_PIN);
                                }, EXIT_POP_UP_DELAY);
                            }
                        }
                );
            }
        }
    }

    boolean getOrCreateRootTask(Task candidateTask, DisplayContent displayContent, int windowingMode) {
        if (!WindowConfiguration.isPopUpWindowMode(windowingMode)) {
            return false;
        }
        final Task pinnedWinTask = windowingMode == WINDOWING_MODE_PINNED_WINDOW_EXT ?
                PinnedWindowOverlayController.getInstance().getTask() : null;
        setWindowingModePopUpView(candidateTask, windowingMode);
        if (pinnedWinTask != null && pinnedWinTask != candidateTask.getRootTask()) {
            pinnedWinTask.setAlwaysOnTop(false);
            moveActivityTaskToBack(pinnedWinTask, MOVE_TO_BACK_NEW_PIN);
        }
        return true;
    }

    void setUpRootTask(Task rootTask, DisplayContent displayContent, int windowingMode) {
        if (!WindowConfiguration.isPopUpWindowMode(windowingMode)) {
            return;
        }
        final Task pinnedWinTask = windowingMode == WINDOWING_MODE_PINNED_WINDOW_EXT ?
                PinnedWindowOverlayController.getInstance().getTask() : null;
        setWindowingModePopUpView(rootTask, windowingMode);
        if (pinnedWinTask != null && pinnedWinTask != rootTask.getRootTask()) {
            pinnedWinTask.setAlwaysOnTop(false);
            moveActivityTaskToBack(pinnedWinTask, MOVE_TO_BACK_NEW_PIN);
        }
    }

    boolean startActivityFromRecents(Task task, ActivityOptions activityOptions) {
        if (tryExitPinnedWindow(task, activityOptions, true)) {
            if (activityOptions == null) {
                return true;
            }
            try {
                if (activityOptions.getRemoteAnimationAdapter() != null) {
                    activityOptions.getRemoteAnimationAdapter().getRunner().onAnimationCancelled();
                }
            } catch (Exception e) {
                Slog.e(TAG, "StartActivityFromRecents failed cancel task=" + task, e);
            }
            return true;
        }
        return false;
    }

    void startLockTaskMode(Task task) {
        if (task.getWindowConfiguration().isPopUpWindowMode()) {
            tryExitPopUpView(task, false, true, true);
        }
    }

    void notifyNextRecentIsPin() {
        mNextRecentIsPin = true;
    }

    private boolean tryExitPinnedWindow(Task task, ActivityOptions activityOptions, boolean isFromRecents) {
        if (isFromRecents && activityOptions == null) {
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "tryExitPinnedWindow skip exit for null options task: " + task);
            }
            return false;
        }
        if (task == null || task.getRootTask() == null) {
            return false;
        }
        if (task.getWindowConfiguration().isPinnedExtWindowMode() &&
                (activityOptions == null || activityOptions.getLaunchWindowingMode() == WINDOWING_MODE_UNDEFINED)) {
            final Task rootTask = task.getTask();
            if (rootTask != null) {
		if (DEBUG_POP_UP) {
		    Slog.d(TAG, "tryExitPinnedWindow - RecentsAnimationController access disabled for Android 16");
		}
                if (task.mTransitionController.isShellTransitionsEnabled()
                        && task.mTransitionController.isCollecting()) {
                    final Transition t = task.mTransitionController.getCollectingTransition();
                    t.abort();
                    if (DEBUG_POP_UP) {
                        Slog.d(TAG, "abort transition=" + t);
                    }
                }
                mTryExitWindowingMode = true;
                tryExitPopUpView(rootTask, false, false, true);
                if (DEBUG_POP_UP) {
                    Slog.d(TAG, "tryExitPinnedWindow targetTask=" + rootTask +
                            " activityOptions=" + activityOptions);
                }
                return true;
            }
        } else if (isFromRecents && task != null &&
                task.getWindowConfiguration().isMiniExtWindowMode()) {
            try {
                if (activityOptions.getRemoteAnimationAdapter() != null) {
                    activityOptions.getRemoteAnimationAdapter().getRunner().onAnimationCancelled();
                }
            } catch (Exception e) {
                Slog.w(TAG, "startActivityFromRecents failed cancel task=" + task, e);
            }
        } else if (isFromRecents &&
                activityOptions.getLaunchWindowingMode() == WINDOWING_MODE_PINNED_WINDOW_EXT) {
            if (mNextRecentIsPin) {
                mNextRecentIsPin = false;
                mAtmService.focusTopTask(DEFAULT_DISPLAY);
            } else {
                mLaunchPopUpViewFromRecents = true;
            }
        }
        return false;
    }

    boolean tryExitPopUpView(Task task, boolean skipAnim, boolean removeMini, boolean removePin) {
        if (task != null && task.getWindowConfiguration().isPopUpWindowMode()) {
            synchronized (mAtmService.mGlobalLock) {
                mAtmService.deferWindowLayout();
                try {
                    final boolean wasMiniWindow = task.getWindowConfiguration().isMiniExtWindowMode();
                    final Task rootTask = task.getRootTask();
                    if (rootTask != null) {
                        if (DEBUG_POP_UP) {
                            Slog.d(TAG, "tryExitPopUpView task=" + task +
                                    " skipAnim=" + skipAnim +
                                    " removeMini=" + removeMini +
                                    ", removePin=" + removePin +
                                    ", wasMiniWindow=" + wasMiniWindow);
                        }
                        rootTask.mWindowContainerExt.setFreezerSkipAnim(skipAnim);
                        if (!skipAnim) {
                            rootTask.mWindowContainerExt.prepareTransition();
                        }
                        rootTask.setAlwaysOnTop(false);
                        rootTask.setWindowingMode(WINDOWING_MODE_UNDEFINED);
                        rootTask.setBounds(null);
                        rootTask.mWindowContainerExt.setFreezerSkipAnim(false);
                        if (skipAnim) {
                            rootTask.mTaskSupervisor.mNoAnimActivities.clear();
                            rootTask.resetSurfaceControlTransforms();
                        }
                        if (!wasMiniWindow && removePin) {
                            TopActivityRecorder.getInstance().clearPinnedWindow();
                        }
                        if ((removeMini || TopActivityRecorder.getInstance().isTaskSystemTool(task))
                                && wasMiniWindow) {
                            TopActivityRecorder.getInstance().removeMiniWindowTask(task);
                        }
                        if (!skipAnim) {
                            rootTask.mWindowContainerExt.scheduleTransition();
                        }
                        return true;
                    }
                } finally {
                    mAtmService.continueWindowLayout();
                }
            }
        }
        return false;
    }

    void updateFocusedApp() {
        final DisplayContent defaultDisplay = mService.getDefaultDisplayContentLocked();
        defaultDisplay.mFocusedApp = null;
        final WindowState win = defaultDisplay.findFocusedWindow();
        if (win != null && win.getTask() != null) {
            mAtmService.setFocusedTask(win.getTask().mTaskId);
        }
    }

    void enterMiniWindowingMode(WindowState win) {
        synchronized (mAtmService.mGlobalLock) {
            if (win != null) {
                final Task task = win.getTask();
                final Task rootTask = task != null ? task.getRootTask() : null;
                if (rootTask == null) {
                    Slog.e(TAG, "enterMiniWindowingMode: the windowState doesn't have a root task. rootTask=" + rootTask);
                    return;
                }
                if (!rootTask.getWindowConfiguration().isPinnedExtWindowMode()) {
                    Slog.e(TAG, "enterMiniWindowingMode: You can only enter mini-window from pinned-window. rootTask=" + rootTask);
                    return;
                }
                if (!TopActivityRecorder.getInstance().moveTopPinnedToMini()) {
                    rootTask.mWindowContainerExt.prepareTransition();
                }
                rootTask.setWindowingMode(WINDOWING_MODE_MINI_WINDOW_EXT);
                mAtmService.setFocusedTask(rootTask.mTaskId);
                rootTask.mWindowContainerExt.scheduleTransition();
            }
        }
    }

    boolean enterPinnedWindowingMode() {
        return DimmerWindow.getInstance().enterPinnedWindowingMode();
    }

    void exitPinnedWindowingMode(ActivityTaskManagerService service, WindowState win) {
        synchronized (service.mGlobalLock) {
            if (win != null) {
                final Task task = win.getTask();
                final Task rootTask = task != null ? task.getRootTask() : null;
                if (rootTask == null) {
                    Slog.e(TAG, "exitPinnedWindowingMode: the windowState doesn't have a root task. rootTask=" + rootTask);
                    return;
                }
                if (!rootTask.getWindowConfiguration().isPinnedExtWindowMode()) {
                    Slog.e(TAG, "exitPinnedWindowingMode: You can only exit from pinned-window. rootTask=" + rootTask);
                    return;
                }
                TopActivityRecorder.getInstance().moveTopPinnedToFull();
                mTryExitWindowingMode = true;
                tryExitPopUpView(rootTask, false, false, true);
                mTryExitWindowingMode = false;
            }
        }
    }

    private void capturePopUpViewTaskSnapshot(Task task) {
        if (task != null) {
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "captureTaskSnapshot for task=" + task);
            }
            final ArraySet<Task> tasks = Sets.newArraySet(new Task[] {task});
            mService.mTaskSnapshotController.snapshotTasks(tasks);
            mService.mTaskSnapshotController.addSkipClosingAppSnapshotTasks(tasks);
        }
    }

    private void setWindowingModePopUpView(Task task, int windowingMode) {
        if (task != null) {
            if (!task.getWindowConfiguration().isPopUpWindowMode()) {
                capturePopUpViewTaskSnapshot(task);
                task.mWindowContainerExt.prepareTransition();
                task.setWindowingMode(windowingMode);
            }
            final Task rootTask = task.getRootTask();
            if (rootTask != null) {
                final Rect bounds = new Rect();
                rootTask.mWindowContainerExt.getTaskWindowSurfaceInfo().resetWindowBoundaryGapToOrigin();
                rootTask.getBounds(bounds);
                WindowResizingAlgorithm.getPopUpViewDefalutBounds(bounds);
                rootTask.setAlwaysOnTop(true);
                rootTask.setBounds(bounds);
            }
            task.mWindowContainerExt.scheduleTransition();
        }
    }

    void triggerVibrate() {
        mHandler.post(() -> {
            if (mVibrator != null) {
                // Create a predefined click effect
                VibrationEffect effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK);
                // Apply STRONG effect strength to increase intensity
                effect = effect.applyEffectStrength(VibrationEffect.EFFECT_STRENGTH_STRONG);
                // Vibrate with the strengthened effect
                mVibrator.vibrate(effect);
            }
        });
    }

    boolean isTryExitWindowingModeByDrag() {
        return mTryExitWindowingModeByDrag;
    }

    void setTryExitWindowingModeByDrag(boolean isExit) {
        if (mAtmService.getTransitionController().isShellTransitionsEnabled() && !isExit) {
            return;
        }
        mTryExitWindowingModeByDrag = isExit;
    }

    boolean isTryExitWindowingMode() {
        return mTryExitWindowingMode;
    }

    void setTryExitWindowingMode(boolean isExit) {
        mTryExitWindowingMode = isExit;
    }

    boolean isLaunchPopUpViewFromRecents() {
        return mLaunchPopUpViewFromRecents;
    }

    boolean shouldInitializeChangeTransition(Task task, int prevWinMode) {
        if (task.mWindowContainerExt.setPreFreezedWindowingMode(prevWinMode)) {
            if (task.mWindowContainerExt.getFreezerSkipAnim()) {
                return false;
            }
            if (mTryExitWindowingModeByDrag) {
                final TaskWindowSurfaceInfo info = new TaskWindowSurfaceInfo(
                        task.mWindowContainerExt.getTaskWindowSurfaceInfo(), prevWinMode);
                task.mTmpPrevBounds.set(info.getTaskWindowSurfaceBounds());
            }
        }
        return true;
    }

    boolean shouldSkipNextTransitionFreeze() {
        if (!mSkipNextTransitionFreeze) {
            return false;
        }
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "entering multi-window, skip transition freeze");
        }
        mSkipNextTransitionFreeze = false;
        return true;
    }

    boolean shouldStartChangeTransition(int prevWinMode, int newWinMode) {
        if (WindowConfiguration.isMiniExtWindowMode(prevWinMode) !=
                WindowConfiguration.isMiniExtWindowMode(newWinMode)) {
            return true;
        }
        if (WindowConfiguration.isPinnedExtWindowMode(prevWinMode) !=
                WindowConfiguration.isPinnedExtWindowMode(newWinMode)) {
            return true;
        }
        return false;
    }

    void notifyFinishTransition() {
        mTryExitWindowingModeByDrag = false;
        mLaunchPopUpViewFromRecents = false;
    }

    InsetsState adjustInsetsForWindow(WindowState target, InsetsState state) {
        if (target != null && target.mActivityRecord != null &&
                target.mActivityRecord.getWindowConfiguration().isPopUpWindowMode()) {
            state = new InsetsState(state);
            state.removeSource(ID_DISPLAY_CUTOUT_LEFT);
            state.removeSource(ID_DISPLAY_CUTOUT_TOP);
            state.removeSource(ID_DISPLAY_CUTOUT_RIGHT);
            state.removeSource(ID_DISPLAY_CUTOUT_BOTTOM);
            handleImeInsetsForPopUpView(target, state);
        }
        return state;
    }

    private void handleImeInsetsForPopUpView(WindowState target, InsetsState state) {
        final InsetsSource imeSource = state.peekSource(InsetsSource.ID_IME);
        if (imeSource == null) {
            return;
        }
        if (!target.mActivityRecord.getWindowConfiguration().isMiniExtWindowMode()) {
            if (target.mActivityRecord.getWindowConfiguration().isPinnedExtWindowMode()) {
                InsetsSource is = new InsetsSource(imeSource);
                is.setVisible(false);
                is.setFrame(0, 0, 0, 0);
                state.addSource(is);
            }
            return;
        }
        final Task task = target.getTask();
        final Task rootTask = task != null ? task.getRootTask() : null;
        final InsetsSource newImeSource = new InsetsSource(imeSource);
        if (rootTask != null && rootTask.mWindowContainerExt.getTaskWindowSurfaceInfo() != null) {
            final TaskWindowSurfaceInfo windowInfo = rootTask.mWindowContainerExt.getTaskWindowSurfaceInfo();
            final Rect displayFrame = target.getDisplayFrame();
            final Rect displayRect = windowInfo.getTaskWindowSurfaceBounds();
            final Rect frame = newImeSource.getFrame();
            final Rect visibleFrame = newImeSource.getVisibleFrame();
            final float scale = windowInfo.getWindowSurfaceRealScale();
            if (frame != null && !frame.isEmpty()) {
                final int frameHeight = Math.max(0, displayRect.bottom - frame.top);
                newImeSource.setFrame(displayFrame.left, displayFrame.bottom - Math.round(
                        (frameHeight * 1.0f) / scale), displayFrame.right, displayFrame.bottom);
            }
            if (visibleFrame != null && !visibleFrame.isEmpty()) {
                final int vfHeight = Math.max(0, displayRect.bottom - visibleFrame.top);
                newImeSource.setVisibleFrame(new Rect(displayFrame.left, displayFrame.bottom - Math.round(
                        (vfHeight * 1.0f) / scale), displayFrame.right, displayFrame.bottom));
            }
        } else {
            newImeSource.setVisible(false);
            newImeSource.setFrame(0, 0, 0, 0);
        }
        state.addSource(newImeSource);
    }

    void calculateTransitionInfo(ArrayList<ChangeInfo> sortedTargets, TransitionInfo out) {
        boolean rotated = false;
        for (int i = 0; i < sortedTargets.size(); i++) {
            final ChangeInfo info = sortedTargets.get(i);
            if (info.mContainer instanceof DisplayContent &&
                    info.mRotation != info.mContainer.getWindowConfiguration().getRotation()) {
                rotated = true;
            }
        }
        if (!rotated) {
            return;
        }
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "Abort pop-up view flags for display ratated");
        }
        for (int i = 0; i < out.getChanges().size(); i++) {
            final Change change = out.getChanges().get(i);
            change.setFlags(change.getFlags() | FLAG_EXIT_POP_UP_VIEW_DISPLAY_ROTATION);
        }
        for (int i = 0; i < sortedTargets.size(); i++) {
            final ChangeInfo info = sortedTargets.get(i);
            info.mReadyFlags = info.mReadyFlags | FLAG_EXIT_POP_UP_VIEW_DISPLAY_ROTATION;
        }
    }

    void computeLaunchParams(LaunchParams params, ActivityOptions options, Task task) {
        if (!WindowConfiguration.isPinnedExtWindowMode(params.mWindowingMode)
                && tryExitPinnedWindow(task, options, false)) {
            params.mWindowingMode = WINDOWING_MODE_FULLSCREEN;
            if (options != null) {
                options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);
            }
        }
    }

    void computeBeforeExecuteRequest(Request request) {
        if (!FEATURE_SUPPORTED) {
            if (request.activityOptions != null && request.activityOptions.isPopUpWindowMode()) {
                request.activityOptions.setLaunchWindowingMode(WINDOWING_MODE_UNDEFINED);
            }
            return;
        }

        if (DEBUG_POP_UP) {
            Slog.d(TAG, "computeBeforeExecuteRequest, caller=" + request.callingPackage
                    + ", intent=" + request.intent);
        }

        if ((request.intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TASK) != 0) {
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "computeBeforeExecuteRequest, skip: original intent requires clear task");
            }
            return;
        }

        final String callerPackage = request.callingPackage;
        final ComponentName component = request.intent.getComponent();
        final String targetPackage = component != null ? component.getPackageName() : "";

        final String currentTopFullscreenPackage = TopActivityRecorder.getInstance().getTopFullscreenPackage();
        final String currentTopMiniPackage = TopActivityRecorder.getInstance().getTopMiniWindowPackage();
        final String currentTopPinnedPackage = TopActivityRecorder.getInstance().getTopPinnedWindowPackage();

        if (request.activityOptions != null && request.activityOptions.isFromNotification()
                && request.activityOptions.isMiniWindowingMode()) {
            // We are jumping to notification. SystemUI already set mini-window options.
            // Do more checks before actually enter mini-window.

            if (currentTopMiniPackage.equals(targetPackage)) {
                // Target package is already in mini-window. Do nothing here.
                return;
            }

            if (currentTopFullscreenPackage.equals(targetPackage) ||
                    currentTopPinnedPackage.equals(targetPackage)) {
                // Target package is in top fullscreen / pinned-window.
                // Reset to undefined windowing mode so that app can run in previous windowing mode.
                request.activityOptions.setLaunchWindowingMode(WINDOWING_MODE_UNDEFINED);
                return;
            }

            // Reset to fullscreen windowing mode for blacklist targets.
            if (PopUpSettingsConfig.getInstance().inNotificationBlacklist(targetPackage)) {
                if (DEBUG_POP_UP) {
                    Slog.d(TAG, "computeBeforeExecuteRequest, skip: in Notification target blacklist");
                }
                request.activityOptions.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);
                return;
            }

            // All done. Here we go.
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "computeBeforeExecuteRequest, configure: enter Notification");
            }
            return;
        }

        if (currentTopMiniPackage.equals(callerPackage)) {
            // Caller app is in top mini-window. Let's start other packages in mini-window as well.
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "computeBeforeExecuteRequest, configure: starting outside activity from mini-window");
            }
            if (request.activityOptions == null) {
                request.activityOptions = SafeActivityOptions.fromBundle(
                    ActivityOptions.makeBasic().toBundle(), Binder.getCallingPid(), Binder.getCallingUid());
            }
            request.activityOptions.setLaunchWindowingMode(WINDOWING_MODE_MINI_WINDOW_EXT);
            return;
        }
    }

    private String reasonToString(int reason) {
        switch (reason) {
            case MOVE_TO_BACK_TOUCH_OUTSIDE:
                return "TOUCH_OUTSIDE";
            case MOVE_TO_BACK_FROM_LEAVE_BUTTON:
                return "FROM_LEAVY_BUTTON";
            case MOVE_TO_BACK_NEW_MINI:
                return "NEW_MINI";
            case MOVE_TO_BACK_NEW_PIN:
                return "NEW_PIN";
            case MOVE_TO_BACK_NON_USER:
                return "NON_USER";
            default:
                return "UNKNOWN";
        }
    }
}
