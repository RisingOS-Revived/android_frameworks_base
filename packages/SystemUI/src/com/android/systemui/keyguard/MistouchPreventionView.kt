/*
 * Copyright (C) 2025 The AxionAOSP Project
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
package com.android.systemui.keyguard

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewOverlay
import android.widget.RelativeLayout
import android.widget.TextView
import com.android.systemui.res.R

class MistouchPreventionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr, defStyleRes) {

    private var listener: VolumeKeyCallback? = null

    private lateinit var mistouchDescription: TextView
    private lateinit var mistouchExitDescription: TextView
    private lateinit var mistouchTitle: TextView

    interface VolumeKeyCallback {
        fun onVolumeUpPressed()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP && event.action == KeyEvent.ACTION_UP) {
            listener?.onVolumeUpPressed()
            listener = null
        }
        return true
    }

    fun addCallback(callback: VolumeKeyCallback) {
        listener = callback
    }

    fun removeCallback(callback: VolumeKeyCallback) {
        if (listener == callback) {
            listener = null
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        mistouchDescription = findViewById(R.id.mistouch_description)
        mistouchTitle = findViewById(R.id.keyguard_mistouch_title)
        mistouchExitDescription = findViewById(R.id.keyguard_mistouch_exit_desciption)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mistouchDescription.setText(R.string.keyguard_mistouch_description)
        mistouchTitle.setText(R.string.keyguard_mistouch_title)
        mistouchExitDescription.setText(R.string.keyguard_mistouch_exit_desciption)
    }
}
