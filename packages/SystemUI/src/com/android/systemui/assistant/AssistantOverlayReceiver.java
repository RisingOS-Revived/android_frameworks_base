/*
 * Copyright (C) 2023-2024 The risingOS Android Project
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
package com.android.systemui.assistant;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import com.android.systemui.res.R;

public class AssistantOverlayReceiver extends BroadcastReceiver {
    private static final String TAG = "AssistantOverlayReceiver";
    private static final String ACTION_ASSISTANT_STATE_CHANGED = "com.android.server.policy.ASSISTANT_STATE_CHANGED";
    private static final String ASSISTANT_STATE = "assistant_listening";

    private View mOverlayView;
    private View mAnimationView;
    private boolean isRegistered = false;
    private WindowManager mWindowManager;
    private final WindowManager.LayoutParams mLayoutParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
    );

    public AssistantOverlayReceiver() {}

    public void register(Context context) {
        if (!isRegistered) {
            IntentFilter filter = new IntentFilter(ACTION_ASSISTANT_STATE_CHANGED);
            context.registerReceiver(this, filter, Context.RECEIVER_EXPORTED);
            isRegistered = true;
            mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        }
    }

    public void unregister(Context context) {
        if (isRegistered) {
            context.unregisterReceiver(this);
            isRegistered = false;
        }
    }

    private void createOverlayView(Context context) {
        if (mOverlayView == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            mOverlayView = inflater.inflate(R.layout.assistant_overlay_layout, null);
            mLayoutParams.gravity = Gravity.BOTTOM;
            mLayoutParams.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            mLayoutParams.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE;
            mOverlayView.setBackgroundColor(Color.argb(0, 0, 0, 0));
            mOverlayView.setOnTouchListener((v, event) -> false);
            if (mWindowManager != null) {
                mWindowManager.addView(mOverlayView, mLayoutParams);
            }
            mAnimationView = mOverlayView.findViewById(R.id.assistant_lottie_animation);
        }
    }

    private void showOverlay(Context context) {
        createOverlayView(context);
        mOverlayView.setVisibility(View.VISIBLE);
        mAnimationView.setScaleX(0f);
        mAnimationView.setScaleY(0f);
        mAnimationView.setVisibility(View.VISIBLE);
        mAnimationView.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .setListener(null)
            .start();
    }

    private void hideOverlay() {
        if (mAnimationView != null && mOverlayView != null 
            && mOverlayView.getVisibility() == View.VISIBLE) {
            if (mAnimationView != null) {
                mAnimationView.setVisibility(View.GONE);
            }
            if (mOverlayView != null) {
                mOverlayView.setVisibility(View.GONE);
            }
            if (mWindowManager != null && mOverlayView != null) {
                mWindowManager.removeView(mOverlayView);
            }
            mOverlayView = null;
            mAnimationView = null;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_ASSISTANT_STATE_CHANGED.equals(intent.getAction())) return;
        boolean isListening = intent.getBooleanExtra(ASSISTANT_STATE, false);
        if (isListening && (mOverlayView == null || mOverlayView.getVisibility() != View.VISIBLE)) {
            showOverlay(context);
        } else if (!isListening && mOverlayView != null && mOverlayView.getVisibility() == View.VISIBLE) {
            hideOverlay();
        }
    }
}
