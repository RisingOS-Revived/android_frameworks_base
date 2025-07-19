/*
 * Copyright (C) 2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package android.view;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;

/** @hide */
class MotionEventExt {

    static final int GET_RAW_X = 0;
    static final int GET_RAW_Y = 1;

    float mDisplayOffsetX = 0.0f;
    float mDisplayOffsetY = 0.0f;
    float mDisplayScaleX = 1.0f;
    float mDisplayScaleY = 1.0f;

    void obtain(MotionEvent other) {
        mDisplayOffsetX = other.mEventExt.mDisplayOffsetX;
        mDisplayOffsetY = other.mEventExt.mDisplayOffsetY;
        mDisplayScaleX = other.mEventExt.mDisplayScaleX;
        mDisplayScaleY = other.mEventExt.mDisplayScaleY;
    }

    void setRawDisplayOffset(float x, float y) {
        mDisplayOffsetX = x;
        mDisplayOffsetY = y;
    }

    void setRawDisplayScale(float x, float y) {
        mDisplayScaleX = x;
        mDisplayScaleY = y;
    }

    float covertRawValue(float origin, int type) {
        if (type == GET_RAW_X) {
            return (origin - mDisplayOffsetX) / mDisplayScaleX;
        }
        return (origin - mDisplayOffsetY) / mDisplayScaleY;
    }
}
