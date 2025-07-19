/*
 * Copyright (C) 2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.wm.shell.popupview;

import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.window.TransitionInfo.FLAG_POP_UP_VIEW;

import static org.rising.DebugConstants.DEBUG_POP_UP;

import android.app.ActivityManager;
import android.app.WindowConfiguration;
import android.content.Context;
import android.os.IBinder;
import android.util.ArrayMap;
import android.util.Slog;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

public class PopUpViewTransitionHandler implements Transitions.TransitionHandler {

    private static final String TAG = "PopUpViewTransitionHandler";

    private final ArrayMap<IBinder, Transitions.TransitionFinishCallback> mTransitionCallbacks = new ArrayMap<>();

    private final Transitions mTransitions;

    final PopUpViewTransitionAnimationRunner mAnimationRunner;

    public PopUpViewTransitionHandler(Context context, ShellInit shellInit, Transitions transitions) {
        mTransitions = transitions;
        mAnimationRunner = new PopUpViewTransitionAnimationRunner(context, this);
        if (Transitions.ENABLE_SHELL_TRANSITIONS) {
            shellInit.addInitCallback(() -> {
                mTransitions.addHandler(PopUpViewTransitionHandler.this);
            }, this);
        }
    }

    @Override
    public boolean startAnimation(IBinder binder, TransitionInfo info, SurfaceControl.Transaction startT,
            SurfaceControl.Transaction endT, Transitions.TransitionFinishCallback callback) {
        if (info.getType() == TRANSIT_CHANGE) {
            boolean hasPopUpView = false;
            for (TransitionInfo.Change change : info.getChanges()) {
                if ((change.getFlags() & FLAG_POP_UP_VIEW) != 0) {
                    hasPopUpView = true;
                }
            }
            if (hasPopUpView) {
                if (DEBUG_POP_UP) {
                    Slog.d(TAG, "handled, transition=" + binder + ", info=" + info);
                }
                mTransitionCallbacks.put(binder, callback);
                mAnimationRunner.startAnimation(binder, info, startT, endT);
                return true;
            }
        }
        return false;
    }

    @Override
    public WindowContainerTransaction handleRequest(IBinder binder, TransitionRequestInfo info) {
        final ActivityManager.RunningTaskInfo taskInfo = info.getTriggerTask();
        if (taskInfo == null || !WindowConfiguration.isPopUpWindowMode(taskInfo.getWindowingMode())) {
            return null;
        }
        return new WindowContainerTransaction();
    }

    @Override
    public void setAnimScaleSetting(float scale) {
        mAnimationRunner.setAnimScaleSetting(scale);
    }

    void onAnimationFinished(IBinder binder) {
        final Transitions.TransitionFinishCallback callback = mTransitionCallbacks.remove(binder);
        if (callback == null) {
            throw new IllegalStateException("No finish callback found");
        }
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "onAnimationFinished!");
        }
        callback.onTransitionFinished(null);
    }
}
