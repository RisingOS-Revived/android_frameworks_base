/*
 * SPDX-FileCopyrightText: 2023-2024 the risingOS Android Project
 * SPDX-FileCopyrightText: 2024 Project Infinity-X
 * SPDX-FileCopyrightText: 2025 DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.island

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.Interpolator

class IslandAnimator(private val view: View) {

    companion object {
        const val ANIMATION_DURATION = 600L
        const val ANIMATION_DELAY = 150L
        const val DISMISS_ANIMATION_DURATION = 300L
        private const val SCALE_START = 0f
        private const val SCALE_PEAK = 1.1f
        private const val SCALE_END = 1f
        private const val ALPHA_START = 0f
        private const val ALPHA_END = 1f
    }

    private val showInterpolator: Interpolator = AccelerateDecelerateInterpolator()
    private val dismissInterpolator: Interpolator = AccelerateInterpolator()

    fun createShowAnimator(): AnimatorSet {
        return AnimatorSet().apply {
            duration = ANIMATION_DURATION
            interpolator = showInterpolator
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, SCALE_START, SCALE_PEAK, SCALE_END),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, SCALE_START, SCALE_PEAK, SCALE_END),
                ObjectAnimator.ofFloat(view, View.ALPHA, ALPHA_START, ALPHA_END)
            )
        }
    }

    fun createDismissAnimator(): AnimatorSet {
        return AnimatorSet().apply {
            duration = ANIMATION_DURATION
            interpolator = dismissInterpolator
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, SCALE_END, SCALE_PEAK, SCALE_START),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, SCALE_END, SCALE_PEAK, SCALE_START),
                ObjectAnimator.ofFloat(view, View.ALPHA, ALPHA_END, ALPHA_START)
            )
        }
    }

    fun createDismissWithDirectionAnimator(direction: Int, onEnd: () -> Unit) {
        view.animate()
            .translationX(direction * view.width.toFloat())
            .alpha(0f)
            .setDuration(DISMISS_ANIMATION_DURATION)
            .setInterpolator(dismissInterpolator)
            .withEndAction(onEnd)
            .start()
    }
} 
