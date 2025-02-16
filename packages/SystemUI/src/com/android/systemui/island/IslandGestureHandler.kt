/*
 * SPDX-FileCopyrightText: 2023-2024 the risingOS Android Project
 * SPDX-FileCopyrightText: 2025 DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.island

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class IslandGestureHandler(
    context: Context,
    private val view: View,
    private val threshold: Float,
    private val onDismiss: (Int) -> Unit,
    private val onSingleTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onCleanup: () -> Unit
) {
    private val halfThreshold = threshold / 2
    private var isLongPress = false
    private var isDismissed = false

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (isLongPress) return false
            val newTranslationX = view.translationX - distanceX
            view.translationX = newTranslationX.coerceIn(-view.width.toFloat(), view.width.toFloat())
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (isLongPress) return false
            if (e1 != null && e2 != null) {
                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y
                if (abs(deltaX) > abs(deltaY) && abs(deltaX) > threshold) {
                    onDismiss(if (deltaX > 0) 1 else -1)
                    return true
                }
            }
            return false
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onSingleTap()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            isLongPress = true
            onLongPress()
        }
    }

    private val gestureDetector = GestureDetector(context, gestureListener)

    fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP) {
            return onTouchUp(event)
        }
        return true
    }

    private fun onTouchUp(event: MotionEvent): Boolean {
        if (isLongPress) {
            isLongPress = false
            return true
        }
        if (isDismissed) return true
        
        if (abs(view.translationX) >= halfThreshold) {
            if (abs(view.translationX) >= threshold) {
                onDismiss(if (view.translationX > 0) 1 else -1)
            } else {
                isDismissed = true
                onCleanup()
            }
        } else {
            view.animate().translationX(0f).alpha(1f).start()
        }
        return true
    }

    fun setDismissed(dismissed: Boolean) {
        isDismissed = dismissed
    }
} 
