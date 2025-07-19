/*
 * Copyright (C) 2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.rising.content;

/** @hide */
public class ContextExt {

    private ContextExt() {}

    /**
     * Use with {@link #getSystemService} to retrieve a
     * {@link org.rising.view.DisplayResolutionManager} for managing display resolution.
     *
     * @hide
     * @see #getSystemService
     * @see org.rising.view.DisplayResolutionManager
     */
    public static final String DISPLAY_RESOLUTION_MANAGER_SERVICE = "resolution_ext";
}
