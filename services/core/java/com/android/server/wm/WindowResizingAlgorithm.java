/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;

import static org.rising.DebugConstants.DEBUG_POP_UP;

import android.graphics.Point;
import android.graphics.Rect;
import android.util.Slog;
import android.view.Surface;

class WindowResizingAlgorithm {

    private static final String TAG = "WindowResizingAlgorithm";

    static final int BOUNDARY_GAP = 44;

    private static final int POPUP_VIEW_DEFALUT_RATIO_HEIGHT = 16;
    private static final int POPUP_VIEW_DEFALUT_RATIO_WIDTH = 9;
    private static final float POPUP_VIEW_DEFALUT_RATIO =
            (float) POPUP_VIEW_DEFALUT_RATIO_HEIGHT / POPUP_VIEW_DEFALUT_RATIO_WIDTH;

    static final float PINNED_WINDOW_SCALE_LARGE_LAND = 0.517f;
    static final float PINNED_WINDOW_SCALE_LARGE_PORT = 0.43f;
    static final float PINNED_WINDOW_SCALE_SMALL_LAND = 0.28f;
    static final float PINNED_WINDOW_SCALE_SMALL_PORT = 0.28f;

    static final float MINI_WINDOW_SCALE_EXIT_MAX = 0.86f;
    static final float MINI_WINDOW_SCALE_EXIT_MIN = 0.6f;
    static final float MINI_WINDOW_SCALE_REVERSE_ORIENTATION_EXIT_MAX = 1.0f;
    static final float MINI_WINDOW_SCALE_REVERSE_ORIENTATION_EXIT_MIN = 0.66f;
    private static final float MINI_WINDOW_SCALE_LAND_DISPLAY_LAND = 0.68f;
    private static final float MINI_WINDOW_SCALE_LAND_DISPLAY_PORT = 0.9f;
    private static final float MINI_WINDOW_SCALE_PORT_DISPLAY_LAND = 0.9f;
    private static final float MINI_WINDOW_SCALE_PORT_DISPLAY_PORT = 0.75f;

    private static final float VELOCITY_X_SPEED_THRESHOLD = 3000f;
    private static final float VELOCITY_Y_SPEED_THRESHOLD = 3000f;

    static float getDefaultMiniWindowScale(int orientation, int displayRotation) {
        final boolean isPortrait = displayRotation == Surface.ROTATION_0 || displayRotation == Surface.ROTATION_180;
        if (orientation == ORIENTATION_LANDSCAPE) {
            return isPortrait ? MINI_WINDOW_SCALE_LAND_DISPLAY_PORT : MINI_WINDOW_SCALE_LAND_DISPLAY_LAND;
        }
        return isPortrait ? MINI_WINDOW_SCALE_PORT_DISPLAY_PORT : MINI_WINDOW_SCALE_PORT_DISPLAY_LAND;
    }

    static float getDefaultMiniWindowScale(int orientation, boolean isPortrait) {
        if (orientation == ORIENTATION_LANDSCAPE) {
            return isPortrait ? MINI_WINDOW_SCALE_LAND_DISPLAY_PORT : MINI_WINDOW_SCALE_LAND_DISPLAY_LAND;
        }
        return isPortrait ? MINI_WINDOW_SCALE_PORT_DISPLAY_PORT : MINI_WINDOW_SCALE_PORT_DISPLAY_LAND;
    }

    static float getDefaultPinnedWindowScale(int orientation, boolean isResizeSmall) {
        if (orientation == ORIENTATION_LANDSCAPE) {
            return isResizeSmall ? PINNED_WINDOW_SCALE_SMALL_LAND : PINNED_WINDOW_SCALE_LARGE_LAND;
        }
        return isResizeSmall ? PINNED_WINDOW_SCALE_SMALL_PORT : PINNED_WINDOW_SCALE_LARGE_PORT;
    }

    static Rect getBoundaryGapAfterMoving(int displayWidth, Rect displayBound,
            Rect windowBound, float x, float y, float velX, float velY) {
        final Rect out = new Rect();
        final boolean isWindowOnLeftSide = x < (float) (displayWidth / 2);
        final boolean shouldSpringLeft = isWindowOnLeftSide ?
                velX < VELOCITY_X_SPEED_THRESHOLD : velX < -VELOCITY_X_SPEED_THRESHOLD;

        if (Math.abs(velX) <= VELOCITY_X_SPEED_THRESHOLD &&
                Math.abs(velY) <= VELOCITY_Y_SPEED_THRESHOLD) {
            out.top = windowBound.top <= displayBound.top + BOUNDARY_GAP ? BOUNDARY_GAP : 0;
            out.bottom = windowBound.bottom >= displayBound.bottom - BOUNDARY_GAP ? BOUNDARY_GAP : 0;
            out.left = shouldSpringLeft ? BOUNDARY_GAP : 0;
            out.right = shouldSpringLeft ? 0 : BOUNDARY_GAP;
            if (DEBUG_POP_UP) {
                Slog.d(TAG, "outBoundaryGap: " + out + ", vel X = " + velX + ", vel Y = " + velY +
                        ", up: (" + x + ", " + y + "), isWindowOnLeftSide: " + isWindowOnLeftSide +
                        ", shouldSpringLeft: " + shouldSpringLeft + ", windowBound: " + windowBound +
                        ", displayBound: " + displayBound);
            }
            return out;
        }
        if (velY < -VELOCITY_Y_SPEED_THRESHOLD) {
            out.top = BOUNDARY_GAP;
        } else {
            out.top = windowBound.top <= displayBound.top + BOUNDARY_GAP ? BOUNDARY_GAP : 0;
        }
        if (velY > VELOCITY_Y_SPEED_THRESHOLD) {
            out.bottom = BOUNDARY_GAP;
        } else {
            out.bottom = windowBound.bottom >= displayBound.bottom - BOUNDARY_GAP ? BOUNDARY_GAP : 0;
        }
        out.left = shouldSpringLeft ? BOUNDARY_GAP : 0;
        out.right = shouldSpringLeft ? 0 : BOUNDARY_GAP;
        if (DEBUG_POP_UP) {
            Slog.d(TAG, "outBoundaryGap: " + out + ", vel X = " + velX + ", vel Y = " + velY +
                    ", up: (" + x + ", " + y + "), isWindowOnLeftSide: " + isWindowOnLeftSide +
                    ", shouldSpringLeft: " + shouldSpringLeft + ", windowBound: " + windowBound +
                    ", displayBound: " + displayBound);
        }
        return out;
    }

    static void getCenterByBoundaryGap(Rect bound, Rect displayBound, Rect boundaryGap,
            float verticalPosRatio, Point center, float scale, Point outPos) {
        outPos.set(center);
        final int w = bound.width();
        final int h = bound.height();
        final float s = scale / 2.0f;
        outPos.y = (int) (displayBound.height() * verticalPosRatio);
        if (boundaryGap.left > 0) {
            outPos.x = displayBound.left + boundaryGap.left + (int) (w * s);
        }
        if (boundaryGap.top > 0) {
            outPos.y = displayBound.top + boundaryGap.top + (int) (h * s);
        }
        if (boundaryGap.right > 0) {
            outPos.x = (displayBound.right - boundaryGap.right) - (int) (w * s);
        }
        if (boundaryGap.bottom > 0) {
            outPos.y = (displayBound.bottom - boundaryGap.bottom) - (int) (h * s);
        }
    }

    static float getPositionAndScaleFactorForTask(Rect bound, Rect displayBound, Point center,
            float scale, boolean preservedScale, Point outPos) {
        final int w = bound.width();
        final int h = bound.height();
        final int disW = displayBound.width();
        final int disH = displayBound.height();
        int newW = w;
        int newH = h;
        float outFactor = 1.0f;
        if (!preservedScale && ((w > h && disW < disH) || (w < h && disW > disH))) {
            final int shortEdge = Math.min(w, h);
            final int longEdge = Math.max(w, h);
            final float r = (float) shortEdge / longEdge;
            newW = (int) (w * r);
            newH = (int) (h * r);
            outFactor = r;
        }
        final float s = scale / 2.0f;
        outPos.set(center.x - (int) (newW * s), center.y - (int) (newH * s));
        return outFactor;
    }

    static float getScaleForTask(Rect bound, Rect displayBound, Point center, float x, float y) {
        final int w = bound.width();
        final int h = bound.height();
        final int disW = displayBound.width();
        final int disH = displayBound.height();
        final float delta;
        final float ratio;

        if (disW > disH) {
            delta = x - center.x;
            if (w > h) {
                ratio = w;
            } else {
                ratio = disH / POPUP_VIEW_DEFALUT_RATIO;
            }
        } else {
            delta = y - center.y;
            if (w < h) {
                ratio = h;
            } else {
                ratio = disW / POPUP_VIEW_DEFALUT_RATIO;
            }
        }
        return (delta >= 0.0f ? delta : 0.0f) / (ratio / 2.0f);
    }

    static void getPopUpViewDefalutBounds(Rect outBounds) {
        if (outBounds != null && !outBounds.isEmpty()) {
            final int h = outBounds.height();
            final int w = outBounds.width();
            final int x = outBounds.left;
            final int y = outBounds.top;
            final float oriRatio = Math.max(h, w) / Math.min(h, w);
            if (Math.abs(oriRatio - POPUP_VIEW_DEFALUT_RATIO) > 0.01f) {
                if (h > w) {
                    outBounds.set(0, 0, w, (int) (w * POPUP_VIEW_DEFALUT_RATIO));
                } else {
                    outBounds.set(0, 0, (int) (h * POPUP_VIEW_DEFALUT_RATIO), h);
                }
                outBounds.offsetTo(x, y);
            }
        }
    }
}
