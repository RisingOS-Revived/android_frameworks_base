/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import static android.graphics.PixelFormat.RGBA_8888;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
import static android.view.WindowManager.LayoutParams.TYPE_PINNED_WINDOW_DISMISS_HINT;

import static org.rising.DebugConstants.DEBUG_POP_UP;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.internal.R;

public class PinnedWindowDismissView extends FrameLayout {

    private static final String TAG = "PinnedWindowDismissView";

    private static final float ALPHA_CUTOFF_THRESHOLD = 0.01f;

    private static final long ANIMATION_START_DELAY_SHOW = 100L;
    private static final long ANIMATION_START_DELAY_HIDE = 200L;

    private static final long DEFAULT_FADE_DURATION = 100L;
    private static final long DEFAULT_FADE_DURATION_DISMISS = 150L;

    private static final TimeInterpolator DEFAULT_INTERPOLATOR = new PathInterpolator(0.17f, 0.0f, 0.83f, 1.0f);

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Runnable mFadeAnimationEndRunnable;

    private final int mButtonPaddingHeight;
    private final int mButtonPaddingWidth;
    private final Rect mButtonRect;

    private View mButtonView;
    private View mButtonImageView;
    private TextView mButtonTextView;

    private boolean mAddedToWm;
    private boolean mIsVisible;

    private Handler mHandler;
    private ViewPropertyAnimator mCurrentAnimation;
    private WindowManager.LayoutParams mWmLayoutParams;

    public PinnedWindowDismissView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mButtonRect = new Rect();
        mFadeAnimationEndRunnable = () -> {
            updateVisibility();
            if (!mIsVisible) {
                dismiss();
            }
        };
        mContext = context;
        mWindowManager = context.getSystemService(WindowManager.class);
        mButtonPaddingHeight = getContext().getResources().getDimensionPixelSize(
                R.dimen.pinned_window_dismiss_button_padding_height);
        mButtonPaddingWidth = getContext().getResources().getDimensionPixelSize(
                R.dimen.pinned_window_dismiss_button_padding_width);

        mAddedToWm = false;
        mIsVisible = false;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed && mButtonView != null) {
            int[] xy = new int[2];
            mButtonView.getLocationOnScreen(xy);
            mButtonRect.set(xy[0], xy[1], xy[0] + mButtonView.getWidth(), xy[1] + mButtonView.getHeight());
            mButtonRect.inset(-mButtonPaddingWidth, 0, -mButtonPaddingWidth, -mButtonPaddingHeight);
            PinnedWindowOverlayController.getInstance().setRemoveBound(mButtonRect);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mButtonView = findViewById(R.id.pinned_window_dismiss_button);
        mButtonImageView = findViewById(R.id.pinned_window_dismiss_button_image_view);
        mButtonTextView = (TextView) findViewById(R.id.pinned_window_dismiss_button_text_view);
        setAlpha(0.0f);
    }

    void showOnScreen(Looper looper) {
        if (mAddedToWm) {
            return;
        }
        mHandler = new Handler(looper);
        mWmLayoutParams = new WindowManager.LayoutParams(LayoutParams.FILL_PARENT,
                getContext().getResources().getDimensionPixelSize(
                        R.dimen.pinned_window_dismiss_gradient_bg_height),
                TYPE_PINNED_WINDOW_DISMISS_HINT,
                FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_NO_LIMITS, RGBA_8888);
        mWmLayoutParams.setTrustedOverlay();
        mWmLayoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        mWmLayoutParams.setTitle(TAG);
        mWmLayoutParams.privateFlags |= SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
        mWmLayoutParams.gravity = Gravity.AXIS_SPECIFIED | Gravity.TOP;
        mWmLayoutParams.setFitInsetsTypes(0);
        mWmLayoutParams.x = 0;
        mWmLayoutParams.y = 0;
        try {
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "start showOnScreen");
            }
            mAddedToWm = true;
            mHandler.post(() -> mWindowManager.addView(this, mWmLayoutParams));
        } catch (Exception e) {
            Slog.e(TAG, "add to wm occur error", e);
        }
        animateToVisibility(true, ANIMATION_START_DELAY_SHOW, DEFAULT_FADE_DURATION);
    }

    void hideOnScreen(boolean isDismiss) {
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "start hideOnScreen");
        }
        animateToVisibility(false, isDismiss ? ANIMATION_START_DELAY_HIDE : 0L,
                isDismiss ? DEFAULT_FADE_DURATION_DISMISS : DEFAULT_FADE_DURATION);
    }

    boolean crossOver(float x, float y) {
        final boolean contains = mButtonRect.contains((int) x, (int) y);
        mButtonView.setSelected(contains);
        mButtonImageView.setSelected(contains);
        if (contains) {
            mButtonTextView.setTextColor(getContext().getColor(R.color.system_neutral1_900));
        } else {
            TypedArray ta = getContext().obtainStyledAttributes(new int[] {R.attr.colorAccentPrimary});
            final int colorAccent = ta.getColor(0, 0);
            ta.recycle();
            mButtonTextView.setTextColor(colorAccent);
        }
        return contains;
    }

    private void dismiss() {
        if (mAddedToWm) {
            try {
                if (DEBUG_POP_UP) {
                    Slog.d(TAG, "start dismiss");
                }
                mAddedToWm = false;
                mHandler.post(() -> mWindowManager.removeView(this));
            } catch (Exception e) {
                Slog.e(TAG, "remove from wm occur error", e);
            }
        }
    }

    private void animateToVisibility(boolean isVisible, long startDelay, long duration) {
        if (mIsVisible != isVisible) {
            mIsVisible = isVisible;
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "animateToVisibility: isVisible=" + isVisible);
            }
            if (mCurrentAnimation != null) {
                mCurrentAnimation.cancel();
                mCurrentAnimation = null;
                if (mIsVisible) {
                    dismiss();
                }
            }
            final float finalAlpha = mIsVisible ? 1.0f : 0.0f;
            if (Float.compare(getAlpha(), finalAlpha) != 0) {
                setVisibility(View.VISIBLE);
                mCurrentAnimation = animate().alpha(finalAlpha).setStartDelay(startDelay)
                        .setInterpolator(DEFAULT_INTERPOLATOR)
                        .setDuration(duration)
                        .withEndAction(mFadeAnimationEndRunnable);
            } else if (!mIsVisible) {
                dismiss();
            }
        }
    }

    private void updateVisibility() {
        if (getAlpha() < ALPHA_CUTOFF_THRESHOLD && getVisibility() != View.INVISIBLE) {
            setVisibility(View.INVISIBLE);
        } else if (getAlpha() > ALPHA_CUTOFF_THRESHOLD && getVisibility() != View.VISIBLE) {
            final int oldFocusability = getDescendantFocusability();
            setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            setVisibility(View.VISIBLE);
            setDescendantFocusability(oldFocusability);
        }
    }
}
