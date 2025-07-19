/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.rising.view;

import android.graphics.Point;

import org.rising.view.IDisplayResolutionListener;

/** @hide */
interface IDisplayResolutionManagerService {

    /* Get display resolution as Point */
    Point getDisplayResolution();

    /* Set display resolution by specific display width */
    void setDisplayResolution(int width);

    /* Register listener to listen display resolution change */
    boolean registerDisplayResolutionListener(in IDisplayResolutionListener listener);

    /* Unregister listener to listen display resolution change */
    boolean unregisterDisplayResolutionListener(in IDisplayResolutionListener listener);
}
