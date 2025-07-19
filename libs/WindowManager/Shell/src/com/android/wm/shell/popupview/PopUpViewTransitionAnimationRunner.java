/*
 * Copyright (C) 2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.wm.shell.popupview;

import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.window.TransitionInfo.FLAG_EXIT_POP_UP_VIEW_BY_DRAG;
import static android.window.TransitionInfo.FLAG_LAUNCH_POP_UP_VIEW_FROM_RECENTS;
import static android.window.TransitionInfo.FLAG_POP_UP_VIEW;

import static org.rising.DebugConstants.DEBUG_POP_UP;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.IBinder;
import android.util.Slog;
import android.view.Choreographer;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import java.util.ArrayList;
import java.util.List;

class PopUpViewTransitionAnimationRunner {

    private static final String TAG = "PopUpViewTransitionAnimationRunner";

    private final PopUpViewTransitionAnimationSpec mAnimationSpec;
    private final PopUpViewTransitionHandler mController;

    PopUpViewTransitionAnimationRunner(Context context, PopUpViewTransitionHandler controller) {
        mAnimationSpec = new PopUpViewTransitionAnimationSpec(context);
        mController = controller;
    }

    void startAnimation(IBinder binder, TransitionInfo info, SurfaceControl.Transaction startT, SurfaceControl.Transaction endT) {
        createAnimator(info, startT, endT, () -> {
            mController.onAnimationFinished(binder);
        }).start();
    }

    void setAnimScaleSetting(float scale) {
        mAnimationSpec.setAnimScaleSetting(scale);
    }

    Animator createAnimator(TransitionInfo info, SurfaceControl.Transaction startT,
            SurfaceControl.Transaction endT, Runnable runnable) {
        final List<PopUpViewTransitionAnimationAdapter> adapters = createAnimationAdapters(info, startT);
        final ValueAnimator valueAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
        long maxDurationHint = 0;
        if (!adapters.isEmpty()) {
            for (PopUpViewTransitionAnimationAdapter adapter : adapters) {
                maxDurationHint = Math.max(maxDurationHint, adapter.getDurationHint());
            }
            valueAnimator.addUpdateListener(anim -> {
                final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
                t.setFrameTimelineVsync(Choreographer.getInstance().getVsyncId());
                for (PopUpViewTransitionAnimationAdapter adapter : adapters) {
                    if (DEBUG_POP_UP) {
                        Slog.d(TAG, "onAnimationUpdate, adapter=" + adapter);
                    }
                    adapter.onAnimationUpdate(t, valueAnimator.getCurrentPlayTime());
                }
                t.apply();
            });
            prepareForFirstFrame(startT, adapters);
        }
        valueAnimator.setDuration(maxDurationHint);
        valueAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationCancel(Animator animator) {
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
            }

            @Override
            public void onAnimationStart(Animator animator) {
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                for (PopUpViewTransitionAnimationAdapter adapter : adapters) {
                    adapter.onAnimationEnd(endT);
                }
                runnable.run();
            }
        });
        startT.apply();
        return valueAnimator;
    }

    private List<PopUpViewTransitionAnimationAdapter> createAnimationAdapters(
            TransitionInfo info, SurfaceControl.Transaction t) {
        final ArrayList adapters = new ArrayList();
        for (TransitionInfo.Change change : info.getChanges()) {
            final int flags = change.getFlags();
            if ((flags & FLAG_POP_UP_VIEW) == 0) {
                continue;
            }
            if (change.getMode() != TRANSIT_CHANGE) {
                if (change.getMode() == TRANSIT_TO_BACK) {
                    if (DEBUG_POP_UP) {
                        Slog.d(TAG, "createAnimationAdapters, fade out");
                    }
                    adapters.add(new PopUpViewTransitionAnimationAdapter(
                            mAnimationSpec.createFadeOutAnimation(change), change));
                }
            } else {
                if (DEBUG_POP_UP) {
                    Slog.d(TAG, "createAnimationAdapters, change.mPopUpView=" + change.mPopUpView);
                }
                if ((flags & FLAG_LAUNCH_POP_UP_VIEW_FROM_RECENTS) != 0) {
                    if (DEBUG_POP_UP) {
                        Slog.d(TAG, "createAnimationAdapters, launch from recents");
                    }
                    adapters.add(new PopUpViewTransitionAnimationAdapter(
                            mAnimationSpec.createLaunchFromRecentsAnimation(change), change));
                } else if ((flags & FLAG_EXIT_POP_UP_VIEW_BY_DRAG) != 0) {
                    if (DEBUG_POP_UP) {
                        Slog.d(TAG, "createAnimationAdapters, by drag");
                    }
                    for (PopUpViewTransitionAnimationAdapter adapter :
                            mAnimationSpec.createChangeAnimationAdapters(info, t)) {
                        adapters.add(adapter);
                    }
                } else {
                    if (DEBUG_POP_UP) {
                        Slog.d(TAG, "createAnimationAdapters, default");
                    }
                    adapters.add(new PopUpViewTransitionAnimationAdapter(
                            mAnimationSpec.createAnimation(change), change));
                }
            }
        }
        return adapters;
    }

    private void prepareForFirstFrame(SurfaceControl.Transaction t, List<PopUpViewTransitionAnimationAdapter> adapters) {
        t.setFrameTimelineVsync(Choreographer.getInstance().getVsyncId());
        for (PopUpViewTransitionAnimationAdapter adapter : adapters) {
            adapter.prepareForFirstFrame(t);
        }
    }
}
