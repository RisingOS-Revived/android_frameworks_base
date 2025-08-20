/*
 * Copyright (C) 2008 The Android Open Source Project
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
// QTI_BEGIN: 2023-03-13: WLAN: Add correct copyright marking in Wifi Icon files
 *
 * Changes from Qualcomm Innovation Center are provided under the following license:
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
// QTI_END: 2023-03-13: WLAN: Add correct copyright marking in Wifi Icon files
 */

package com.android.systemui.statusbar.connectivity;

import static com.android.settingslib.flags.Flags.newStatusBarIcons;

import com.android.settingslib.AccessibilityContentDescriptions;
import com.android.systemui.res.R;
import com.android.settingslib.SignalIcon.IconGroup;

/** */
public class WifiIcons {

    public static final int[] WIFI_FULL_ICONS = getIconsBasedOnFlag();

    /**
     * Check the aconfig flag to decide on which icons to use. Can be removed once the flag is gone
     */
    private static int[] getIconsBasedOnFlag() {
        if (newStatusBarIcons()) {
            // TODO(b/396664075):
            // The new wifi icons only define a range of [0, 3]. Since this array is indexed on
            // level, we can simulate the range squash by mapping both level 3 to drawn-level 2, and
            // level 4 to drawn-level 3
            return new int[] {
                com.android.settingslib.R.drawable.ic_wifi_0,
                com.android.settingslib.R.drawable.ic_wifi_1,
                com.android.settingslib.R.drawable.ic_wifi_2,
                com.android.settingslib.R.drawable.ic_wifi_3
            };
        } else {
            return new int[] {
                com.android.internal.R.drawable.ic_wifi_signal_0,
                com.android.internal.R.drawable.ic_wifi_signal_1,
                com.android.internal.R.drawable.ic_wifi_signal_2,
                com.android.internal.R.drawable.ic_wifi_signal_3,
                com.android.internal.R.drawable.ic_wifi_signal_4
            };
        }
    }

    public static final int[] WIFI_NO_INTERNET_ICONS = getErrorIconsBasedOnFlag();

    private static int [] getErrorIconsBasedOnFlag() {
        if (newStatusBarIcons()) {
            // See above note, new wifi icons only have 3 bars, so levels 2 and 3 are the same
            return new int[] {
                com.android.settingslib.R.drawable.ic_wifi_0_error,
                com.android.settingslib.R.drawable.ic_wifi_1_error,
                com.android.settingslib.R.drawable.ic_wifi_2_error,
                com.android.settingslib.R.drawable.ic_wifi_3_error
            };
        } else {
            return new int[] {
                com.android.settingslib.R.drawable.ic_no_internet_wifi_signal_0,
                com.android.settingslib.R.drawable.ic_no_internet_wifi_signal_1,
                com.android.settingslib.R.drawable.ic_no_internet_wifi_signal_2,
                com.android.settingslib.R.drawable.ic_no_internet_wifi_signal_3,
                com.android.settingslib.R.drawable.ic_no_internet_wifi_signal_4
            };
        }
    }

// QTI_BEGIN: 2024-06-02: WLAN: SystemUI: Wifi generation icons in Notification bar.
    public static final int[] WIFI_4_FULL_ICONS = {
// QTI_END: 2024-06-02: WLAN: SystemUI: Wifi generation icons in Notification bar.
// QTI_BEGIN: 2019-05-30: WLAN: wifi: Resolve issue with Wi-Fi icon in status bar.
            com.android.internal.R.drawable.ic_wifi_4_signal_0,
            com.android.internal.R.drawable.ic_wifi_4_signal_1,
            com.android.internal.R.drawable.ic_wifi_4_signal_2,
            com.android.internal.R.drawable.ic_wifi_4_signal_3,
            com.android.internal.R.drawable.ic_wifi_4_signal_4
    };

// QTI_END: 2019-05-30: WLAN: wifi: Resolve issue with Wi-Fi icon in status bar.
// QTI_BEGIN: 2024-06-02: WLAN: SystemUI: Wifi generation icons in Notification bar.
    public static final int[] WIFI_4_NO_INTERNET_ICONS = {
// QTI_END: 2024-06-02: WLAN: SystemUI: Wifi generation icons in Notification bar.
// QTI_BEGIN: 2019-05-30: WLAN: wifi: Resolve issue with Wi-Fi icon in status bar.
            R.drawable.ic_qs_wifi_4_0,
            R.drawable.ic_qs_wifi_4_1,
            R.drawable.ic_qs_wifi_4_2,
            R.drawable.ic_qs_wifi_4_3,
            R.drawable.ic_qs_wifi_4_4
    };

// QTI_END: 2019-05-30: WLAN: wifi: Resolve issue with Wi-Fi icon in status bar.
// QTI_BEGIN: 2024-06-02: WLAN: SystemUI: Wifi generation icons in Notification bar.
    public static final int[] WIFI_5_FULL_ICONS = {
// QTI_END: 2024-06-02: WLAN: SystemUI: Wifi generation icons in Notification bar.
// QTI_BEGIN: 2019-05-30: WLAN: wifi: Resolve issue with Wi-Fi icon in status bar.
            com.android.internal.R.drawable.ic_wifi_5_signal_0,
            com.android.internal.R.drawable.ic_wifi_5_signal_1,
            com.android.internal.R.drawable.ic_wifi_5_signal_2,
            com.android.internal.R.drawable.ic_wifi_5_signal_3,
            com.android.internal.R.drawable.ic_wifi_5_signal_4
    };

// QTI_END: 2019-05-30: WLAN: wifi: Resolve issue with Wi-Fi icon in status bar.
// QTI_BEGIN: 2024-06-02: WLAN: SystemUI: Wifi generation icons in Notification bar.
    public static final int[] WIFI_5_NO_INTERNET_ICONS = {
// QTI_END: 2024-06-02: WLAN: SystemUI: Wifi generation icons in Notification bar.
// QTI_BEGIN: 2019-05-30: WLAN: wifi: Resolve issue with Wi-Fi icon in status bar.
            R.drawable.ic_qs_wifi_5_0,
            R.drawable.ic_qs_wifi_5_1,
            R.drawable.ic_qs_wifi_5_2,
            R.drawable.ic_qs_wifi_5_3,
            R.drawable.ic_qs_wifi_5_4
    };

// QTI_END: 2019-05-30: WLAN: wifi: Resolve issue with Wi-Fi icon in status bar.
// QTI_BEGIN: 2024-06-02: WLAN: SystemUI: Wifi generation icons in Notification bar.
    public static final int[] WIFI_6_FULL_ICONS = {
// QTI_END: 2024-06-02: WLAN: SystemUI: Wifi generation icons in Notification bar.
// QTI_BEGIN: 2019-05-30: WLAN: wifi: Resolve issue with Wi-Fi icon in status bar.
            com.android.internal.R.drawable.ic_wifi_6_signal_0,
            com.android.internal.R.drawable.ic_wifi_6_signal_1,
            com.android.internal.R.drawable.ic_wifi_6_signal_2,
            com.android.internal.R.drawable.ic_wifi_6_signal_3,
            com.android.internal.R.drawable.ic_wifi_6_signal_4
    };

// QTI_END: 2019-05-30: WLAN: wifi: Resolve issue with Wi-Fi icon in status bar.
// QTI_BEGIN: 2024-06-02: WLAN: SystemUI: Wifi generation icons in Notification bar.
    public static final int[] WIFI_6_NO_INTERNET_ICONS = {
// QTI_END: 2024-06-02: WLAN: SystemUI: Wifi generation icons in Notification bar.
// QTI_BEGIN: 2019-05-30: WLAN: wifi: Resolve issue with Wi-Fi icon in status bar.
            R.drawable.ic_qs_wifi_6_0,
            R.drawable.ic_qs_wifi_6_1,
            R.drawable.ic_qs_wifi_6_2,
            R.drawable.ic_qs_wifi_6_3,
            R.drawable.ic_qs_wifi_6_4
    };

// QTI_END: 2019-05-30: WLAN: wifi: Resolve issue with Wi-Fi icon in status bar.
// QTI_BEGIN: 2024-06-02: WLAN: SystemUI: Wifi generation icons in Notification bar.
    public static final int[] WIFI_7_FULL_ICONS = {
// QTI_END: 2024-06-02: WLAN: SystemUI: Wifi generation icons in Notification bar.
// QTI_BEGIN: 2023-02-17: WLAN: wifi: Display Wi-Fi standard in signal icons for Wi-Fi 7 APs
            com.android.internal.R.drawable.ic_wifi_7_signal_0,
            com.android.internal.R.drawable.ic_wifi_7_signal_1,
            com.android.internal.R.drawable.ic_wifi_7_signal_2,
            com.android.internal.R.drawable.ic_wifi_7_signal_3,
            com.android.internal.R.drawable.ic_wifi_7_signal_4
    };

// QTI_END: 2023-02-17: WLAN: wifi: Display Wi-Fi standard in signal icons for Wi-Fi 7 APs
// QTI_BEGIN: 2024-06-02: WLAN: SystemUI: Wifi generation icons in Notification bar.
    public static final int[] WIFI_7_NO_INTERNET_ICONS = {
// QTI_END: 2024-06-02: WLAN: SystemUI: Wifi generation icons in Notification bar.
// QTI_BEGIN: 2023-02-17: WLAN: wifi: Display Wi-Fi standard in signal icons for Wi-Fi 7 APs
            R.drawable.ic_qs_wifi_7_0,
            R.drawable.ic_qs_wifi_7_1,
            R.drawable.ic_qs_wifi_7_2,
            R.drawable.ic_qs_wifi_7_3,
            R.drawable.ic_qs_wifi_7_4
    };

// QTI_END: 2023-02-17: WLAN: wifi: Display Wi-Fi standard in signal icons for Wi-Fi 7 APs
    public static final int[][] QS_WIFI_SIGNAL_STRENGTH = {
            WIFI_NO_INTERNET_ICONS,
            WIFI_FULL_ICONS
    };

// QTI_BEGIN: 2019-05-30: WLAN: wifi: Resolve issue with Wi-Fi icon in status bar.
    static final int[][] WIFI_SIGNAL_STRENGTH = QS_WIFI_SIGNAL_STRENGTH;
// QTI_END: 2019-05-30: WLAN: wifi: Resolve issue with Wi-Fi icon in status bar.
// QTI_BEGIN: 2019-04-03: WLAN: wifi: Wifi generation and WPA3 UI enhancements for Access points.

    public static final int[][] QS_WIFI_4_SIGNAL_STRENGTH = {
// QTI_END: 2019-04-03: WLAN: wifi: Wifi generation and WPA3 UI enhancements for Access points.
// QTI_BEGIN: 2019-05-30: WLAN: wifi: Resolve issue with Wi-Fi icon in status bar.
            WIFI_4_NO_INTERNET_ICONS,
            WIFI_4_FULL_ICONS
    };

    static final int[][] WIFI_4_SIGNAL_STRENGTH = QS_WIFI_4_SIGNAL_STRENGTH;
// QTI_END: 2019-05-30: WLAN: wifi: Resolve issue with Wi-Fi icon in status bar.
// QTI_BEGIN: 2019-04-03: WLAN: wifi: Wifi generation and WPA3 UI enhancements for Access points.

    public static final int[][] QS_WIFI_5_SIGNAL_STRENGTH = {
// QTI_END: 2019-04-03: WLAN: wifi: Wifi generation and WPA3 UI enhancements for Access points.
// QTI_BEGIN: 2019-05-30: WLAN: wifi: Resolve issue with Wi-Fi icon in status bar.
            WIFI_5_NO_INTERNET_ICONS,
            WIFI_5_FULL_ICONS
    };

    static final int[][] WIFI_5_SIGNAL_STRENGTH = QS_WIFI_5_SIGNAL_STRENGTH;
// QTI_END: 2019-05-30: WLAN: wifi: Resolve issue with Wi-Fi icon in status bar.
// QTI_BEGIN: 2019-04-03: WLAN: wifi: Wifi generation and WPA3 UI enhancements for Access points.

    public static final int[][] QS_WIFI_6_SIGNAL_STRENGTH = {
// QTI_END: 2019-04-03: WLAN: wifi: Wifi generation and WPA3 UI enhancements for Access points.
// QTI_BEGIN: 2019-05-30: WLAN: wifi: Resolve issue with Wi-Fi icon in status bar.
            WIFI_6_NO_INTERNET_ICONS,
            WIFI_6_FULL_ICONS
    };

    static final int[][] WIFI_6_SIGNAL_STRENGTH = QS_WIFI_6_SIGNAL_STRENGTH;
// QTI_END: 2019-05-30: WLAN: wifi: Resolve issue with Wi-Fi icon in status bar.

// QTI_BEGIN: 2023-02-17: WLAN: wifi: Display Wi-Fi standard in signal icons for Wi-Fi 7 APs
    public static final int[][] QS_WIFI_7_SIGNAL_STRENGTH = {
            WIFI_7_NO_INTERNET_ICONS,
            WIFI_7_FULL_ICONS
    };

    static final int[][] WIFI_7_SIGNAL_STRENGTH = QS_WIFI_7_SIGNAL_STRENGTH;

// QTI_END: 2023-02-17: WLAN: wifi: Display Wi-Fi standard in signal icons for Wi-Fi 7 APs
    public static final int QS_WIFI_DISABLED = com.android.internal.R.drawable.ic_wifi_signal_0;
    public static final int QS_WIFI_NO_NETWORK = com.android.internal.R.drawable.ic_wifi_signal_0;
    public static final int WIFI_NO_NETWORK = QS_WIFI_NO_NETWORK;

    static final int WIFI_LEVEL_COUNT = WIFI_SIGNAL_STRENGTH[0].length;

    public static final IconGroup UNMERGED_WIFI = new IconGroup(
            "Wi-Fi Icons",
            WifiIcons.WIFI_SIGNAL_STRENGTH,
            WifiIcons.QS_WIFI_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.WIFI_CONNECTION_STRENGTH,
            WifiIcons.WIFI_NO_NETWORK,
            WifiIcons.QS_WIFI_NO_NETWORK,
            WifiIcons.WIFI_NO_NETWORK,
            WifiIcons.QS_WIFI_NO_NETWORK,
            AccessibilityContentDescriptions.WIFI_NO_CONNECTION
    );
}
