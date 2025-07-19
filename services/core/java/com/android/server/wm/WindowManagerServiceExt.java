/*
 * Copyright (C) 2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import android.content.Context;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.view.Display;

class WindowManagerServiceExt {

    private static class InstanceHolder {
        private static final WindowManagerServiceExt INSTANCE = new WindowManagerServiceExt();
    }

    static WindowManagerServiceExt getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private WindowManagerService mWms;
    private Context mContext;
    private Display mDisplay;

    private static final int TYPE_DISABLED = 0;
    private static final int TYPE_AOSP_FHD_DEFAULT = 1;
    private static final int TYPE_AOSP_QHD_DEFAULT = 2;
    private static final int TYPE_FORCED = 3;

    private static final int FHD_WIDTH = 1080;
    private static final int QHD_WIDTH = 1440;
    private static final float SCALE = (float) FHD_WIDTH / QHD_WIDTH;

    private int mWidth = -1;
    private int mHeight = -1;

    void init(WindowManagerService wms) {
        mWms = wms;
        mContext = wms.mContext;
        TopActivityRecorder.getInstance().initWms(wms);
        PopUpWindowController.getInstance().init(mContext, wms);
        mDisplay = mContext.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
    }

    void systemReady() {
        PopUpWindowController.getInstance().systemReady();
        updateResolutionIfNeeded();
    }

    void onUserSwitched() {
    }

    int getDensityWithScale(int density) {
        final int width = getDisplayResolution().x;
        if (width > 0) {
            return (int) (density * getDensityScale(width));
        }
        return density;
    }

    Point getDisplayResolution() {
        updateResolutionIfNeeded();
        return new Point(mWidth, mHeight);
    }

    private void updateResolutionIfNeeded() {
        if ((mWidth < 0 || mHeight < 0) && mDisplay != null) {
            final Display.Mode mode = mDisplay.getMode();
            mWidth = mode.getPhysicalWidth();
            mHeight = mode.getPhysicalHeight();
        }
    }

    private static float getDensityScale(int currentWidth) {
        final int deviceType = getDeviceType();
        if (deviceType != TYPE_AOSP_FHD_DEFAULT && deviceType != TYPE_AOSP_QHD_DEFAULT) {
            return 1f;
        }
        return (float) currentWidth / FHD_WIDTH;
    }

    private static int getDeviceType() {
        return android.os.SystemProperties.getInt("ro.rising.display.resolution_switch", TYPE_DISABLED);
    }
}
