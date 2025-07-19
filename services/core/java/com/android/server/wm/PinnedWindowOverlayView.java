/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.android.internal.R;

public class PinnedWindowOverlayView extends FrameLayout {

    private final Resources mResources;

    private LinearLayout mMenuButtonContainer;
    private ImageButton mMuteButton;
    private ImageButton mScaleButton;
    private int mRadius;

    public PinnedWindowOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mResources = context.getResources();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        initViews();
    }

    private void initViews() {
        mMenuButtonContainer = (LinearLayout) findViewById(R.id.pinned_window_menu_container);
        mScaleButton = (ImageButton) findViewById(R.id.pinned_window_menu_scale_button);
        mScaleButton.setImageResource(R.drawable.pinned_window_scale_large);
        mMuteButton = (ImageButton) findViewById(R.id.pinned_window_menu_mute_button);
        mMuteButton.setImageResource(R.drawable.pinned_window_mute);
    }

    void updateScaleIconResource(boolean isPinnedWindowSizeSmall) {
        if (isPinnedWindowSizeSmall) {
            mScaleButton.setImageResource(R.drawable.pinned_window_scale_large);
        } else {
            mScaleButton.setImageResource(R.drawable.pinned_window_scale_small);
        }
    }

    void updateMuteIconResource(boolean isPinnedWindowMute) {
        if (isPinnedWindowMute) {
            mMuteButton.setImageResource(R.drawable.pinned_window_unmute);
        } else {
            mMuteButton.setImageResource(R.drawable.pinned_window_mute);
        }
    }

    void updateResources(Rect taskBound, boolean isSizeSmall) {
        if (taskBound.width() > taskBound.height()) {
            mMenuButtonContainer.setOrientation(LinearLayout.HORIZONTAL);
        } else {
            mMenuButtonContainer.setOrientation(LinearLayout.VERTICAL);
        }
        if (isSizeSmall) {
            mRadius = mResources.getDimensionPixelSize(R.dimen.pinned_window_menu_corner_radius_small);
        } else {
            mRadius = mResources.getDimensionPixelSize(R.dimen.pinned_window_menu_corner_radius_large);
        }
        ((GradientDrawable) mMenuButtonContainer.getBackground()).setCornerRadius(mRadius);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return PinnedWindowOverlayController.getInstance().onTouchEvent(event);
    }
}
