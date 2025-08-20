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
 */

// QTI_BEGIN: 2023-12-17: Data: SystemUI: Enhanced 5g icon
/*
  Changes from Qualcomm Innovation Center are provided under the following license:

// QTI_END: 2023-12-17: Data: SystemUI: Enhanced 5g icon
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
  Copyright (c) 2023-2024 Qualcomm Innovation Center, Inc. All rights reserved.
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2023-12-17: Data: SystemUI: Enhanced 5g icon
  SPDX-License-Identifier: BSD-3-Clause-Clear
*/

// QTI_END: 2023-12-17: Data: SystemUI: Enhanced 5g icon
package com.android.settingslib.mobile;

import com.android.settingslib.R;
import com.android.settingslib.SignalIcon.MobileIconGroup;
import com.android.settingslib.flags.Flags;

import java.util.HashMap;
import java.util.Map;

/**
 * Telephony related icons and strings for SysUI and Settings.
 */
public class TelephonyIcons {
    //***** Data connection icons
    public static final int FLIGHT_MODE_ICON = R.drawable.stat_sys_airplane_mode;

    public static final int ICON_LTE =
            flagged(R.drawable.ic_lte_mobiledata, R.drawable.ic_lte_mobiledata_updated);
    public static final int ICON_LTE_PLUS =
            flagged(R.drawable.ic_lte_plus_mobiledata, R.drawable.ic_lte_plus_mobiledata_updated);
    public static final int ICON_G =
            flagged(R.drawable.ic_g_mobiledata, R.drawable.ic_g_mobiledata_updated);
    public static final int ICON_E =
            flagged(R.drawable.ic_e_mobiledata, R.drawable.ic_e_mobiledata_updated);
    public static final int ICON_H =
            flagged(R.drawable.ic_h_mobiledata, R.drawable.ic_h_mobiledata_updated);
    public static final int ICON_H_PLUS =
            flagged(R.drawable.ic_h_plus_mobiledata, R.drawable.ic_h_plus_mobiledata_updated);
    public static final int ICON_3G =
            flagged(R.drawable.ic_3g_mobiledata, R.drawable.ic_3g_mobiledata_updated);
    public static final int ICON_4G =
            flagged(R.drawable.ic_4g_mobiledata, R.drawable.ic_4g_mobiledata_updated);
    public static final int ICON_4G_PLUS =
            flagged(R.drawable.ic_4g_plus_mobiledata, R.drawable.ic_4g_plus_mobiledata_updated);
    public static final int ICON_4G_LTE =
            flagged(R.drawable.ic_4g_lte_mobiledata, R.drawable.ic_4g_lte_mobiledata_updated);
    public static final int ICON_4G_LTE_PLUS =
            flagged(R.drawable.ic_4g_lte_plus_mobiledata,
                    R.drawable.ic_4g_lte_plus_mobiledata_updated);
    public static final int ICON_5G_E =
            flagged(R.drawable.ic_5g_e_mobiledata, R.drawable.ic_5g_e_mobiledata_updated);
    public static final int ICON_1X =
            flagged(R.drawable.ic_1x_mobiledata, R.drawable.ic_1x_mobiledata_updated);
    public static final int ICON_5G =
            flagged(R.drawable.ic_5g_mobiledata, R.drawable.ic_5g_mobiledata_updated);
    public static final int ICON_5G_SA =
            flagged(R.drawable.ic_5g_sa_mobiledata, R.drawable.ic_5g_sa_mobiledata_updated);
    public static final int ICON_5G_PLUS =
            flagged(R.drawable.ic_5g_plus_mobiledata, R.drawable.ic_5g_plus_mobiledata_updated);
    public static final int ICON_CWF =
            flagged(R.drawable.ic_carrier_wifi, R.drawable.ic_carrier_wifi_updated);
    public static final int ICON_5G_BASIC = R.drawable.ic_5g_mobiledata;
    public static final int ICON_5G_UWB = R.drawable.ic_5g_uwb_mobiledata;
// QTI_BEGIN: 2023-12-17: Data: SystemUI: Enhanced 5g icon
    public static final int ICON_5G_PLUS_PLUS = R.drawable.ic_5g_plus_plus_mobiledata;
// QTI_END: 2023-12-17: Data: SystemUI: Enhanced 5g icon
// QTI_BEGIN: 2024-05-22: Android_UI: SystemUI: Display 5GA icon for 3CC
    public static final int ICON_5G_A = R.drawable.ic_5g_a_mobiledata;
// QTI_END: 2024-05-22: Android_UI: SystemUI: Display 5GA icon for 3CC
    public static final int ICON_VOWIFI = R.drawable.ic_vowifi;
    public static final int ICON_VOWIFI_CALLING = R.drawable.ic_vowifi_calling;
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
    public static final int ICON_5G_BASIC_6RX = R.drawable.ic_5g_6rx_mobiledata;
    public static final int ICON_5G_UWB_6RX = R.drawable.ic_5g_uwb_6rx_mobiledata;
    public static final int ICON_5G_PLUS_PLUS_6RX = R.drawable.ic_5g_plus_plus_6rx_mobiledata;
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons

    /** Make it slightly more obvious which resource we are using */
    private static int flagged(int oldIcon, int newIcon) {
        if (Flags.newStatusBarIcons()) {
            return newIcon;
        }
        return oldIcon;
    }

    public static final MobileIconGroup CARRIER_NETWORK_CHANGE = new MobileIconGroup(
            "CARRIER_NETWORK_CHANGE",
            R.string.carrier_network_change_mode,
            /* dataType= */ 0
    );

    public static final MobileIconGroup THREE_G = new MobileIconGroup(
            "3G",
            R.string.data_connection_3g,
            TelephonyIcons.ICON_3G
    );

    public static final MobileIconGroup WFC = new MobileIconGroup(
            "WFC",
            /* dataContentDescription= */ 0,
            /* dataType= */ 0);

    public static final MobileIconGroup UNKNOWN = new MobileIconGroup(
            "Unknown",
            /* dataContentDescription= */ 0,
            /* dataType= */ 0);

    public static final MobileIconGroup E = new MobileIconGroup(
            "E",
            R.string.data_connection_edge,
            TelephonyIcons.ICON_E
    );

    public static final MobileIconGroup ONE_X = new MobileIconGroup(
            "1X",
            R.string.data_connection_cdma,
            TelephonyIcons.ICON_1X
    );

    public static final MobileIconGroup G = new MobileIconGroup(
            "G",
            R.string.data_connection_gprs,
            TelephonyIcons.ICON_G
    );

    public static final MobileIconGroup H = new MobileIconGroup(
            "H",
            R.string.data_connection_3_5g,
            TelephonyIcons.ICON_H
    );

    public static final MobileIconGroup H_PLUS = new MobileIconGroup(
            "H+",
            R.string.data_connection_3_5g_plus,
            TelephonyIcons.ICON_H_PLUS
    );

    public static final MobileIconGroup FOUR_G = new MobileIconGroup(
            "4G",
            R.string.data_connection_4g,
            TelephonyIcons.ICON_4G
    );

    public static final MobileIconGroup FOUR_G_PLUS = new MobileIconGroup(
// QTI_BEGIN: 2016-06-23: Telephony: Add support for LTE CarrierAgregation
            "4G+",
// QTI_END: 2016-06-23: Telephony: Add support for LTE CarrierAgregation
            R.string.data_connection_4g_plus,
            TelephonyIcons.ICON_4G_PLUS
    );

    public static final MobileIconGroup LTE = new MobileIconGroup(
            "LTE",
            R.string.data_connection_lte,
            TelephonyIcons.ICON_LTE
    );

    public static final MobileIconGroup LTE_PLUS = new MobileIconGroup(
            "LTE+",
            R.string.data_connection_lte_plus,
            TelephonyIcons.ICON_LTE_PLUS
    );

    public static final MobileIconGroup FOUR_G_LTE = new MobileIconGroup(
            "4G LTE",
            R.string.data_connection_4g_lte,
            TelephonyIcons.ICON_4G_LTE
    );

    public static final MobileIconGroup FOUR_G_LTE_PLUS = new MobileIconGroup(
            "4G LTE+",
            R.string.data_connection_4g_lte_plus,
            TelephonyIcons.ICON_4G_LTE_PLUS
    );

    public static final MobileIconGroup LTE_CA_5G_E = new MobileIconGroup(
            "5Ge",
            R.string.data_connection_5ge_html,
            TelephonyIcons.ICON_5G_E
    );

    public static final MobileIconGroup NR_5G = new MobileIconGroup(
            "5G",
            R.string.data_connection_5g,
            TelephonyIcons.ICON_5G
    );

    public static final MobileIconGroup NR_5G_SA = new MobileIconGroup(
            "5G SA",
            R.string.data_connection_5g_sa,
            TelephonyIcons.ICON_5G_SA);

    public static final MobileIconGroup NR_5G_PLUS = new MobileIconGroup(
            "5G_PLUS",
            R.string.data_connection_5g_plus,
            TelephonyIcons.ICON_5G_PLUS
    );

    public static final MobileIconGroup DATA_DISABLED = new MobileIconGroup(
            "DataDisabled",
            R.string.cell_data_off_content_description,
            0
    );

    public static final MobileIconGroup NOT_DEFAULT_DATA = new MobileIconGroup(
            "NotDefaultData",
            R.string.not_default_data_content_description,
            /* dataType= */ 0
    );

    public static final MobileIconGroup CARRIER_MERGED_WIFI = new MobileIconGroup(
            "CWF",
            R.string.data_connection_carrier_wifi,
            TelephonyIcons.ICON_CWF
    );

    // When adding a new MobileIconGroup, check if the dataContentDescription has to be filtered
    // in QSCarrier#hasValidTypeContentDescription
    //
    public static final MobileIconGroup FIVE_G = new MobileIconGroup(
// QTI_BEGIN: 2018-12-18: Android_UI: SystemUI: Use same URI format for SSSS and DSDS
            "5G",
            R.string.data_connection_5g,
// QTI_END: 2018-12-18: Android_UI: SystemUI: Use same URI format for SSSS and DSDS
            TelephonyIcons.ICON_5G);

    public static final MobileIconGroup FIVE_G_BASIC = new MobileIconGroup(
// QTI_BEGIN: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
            "5GBasic",
            R.string.data_connection_5g_basic,
// QTI_END: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
            TelephonyIcons.ICON_5G_BASIC);

    public static final MobileIconGroup FIVE_G_UWB = new MobileIconGroup(
// QTI_BEGIN: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
            "5GUWB",
            R.string.data_connection_5g_uwb,
// QTI_END: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
            TelephonyIcons.ICON_5G_UWB);

// QTI_BEGIN: 2024-05-22: Android_UI: SystemUI: Display 5GA icon for 3CC
    public static final MobileIconGroup FIVE_G_A = new MobileIconGroup(
            "5GA",
            R.string.data_connection_5g_a,
            TelephonyIcons.ICON_5G_A);

// QTI_END: 2024-05-22: Android_UI: SystemUI: Display 5GA icon for 3CC
// QTI_BEGIN: 2023-12-17: Data: SystemUI: Enhanced 5g icon
    public static final MobileIconGroup FIVE_G_PLUS_PLUS = new MobileIconGroup(
            "5G_PLUS_PLUS",
            R.string.data_connection_5g_plus_plus,
            TelephonyIcons.ICON_5G_PLUS_PLUS);

// QTI_END: 2023-12-17: Data: SystemUI: Enhanced 5g icon
    public static final MobileIconGroup FIVE_G_SA = new MobileIconGroup(
// QTI_BEGIN: 2018-12-18: Android_UI: SystemUI: Use same URI format for SSSS and DSDS
            "5GSA",
            R.string.data_connection_5g_sa,
// QTI_END: 2018-12-18: Android_UI: SystemUI: Use same URI format for SSSS and DSDS
            TelephonyIcons.ICON_5G_SA);

    public static final MobileIconGroup VOWIFI = new MobileIconGroup(
// QTI_BEGIN: 2020-06-01: Android_UI: SystemUI: support VoWIFI icons
            "VoWIFI",
            0,
// QTI_END: 2020-06-01: Android_UI: SystemUI: support VoWIFI icons
            TelephonyIcons.ICON_VOWIFI);

    public static final MobileIconGroup VOWIFI_CALLING = new MobileIconGroup(
// QTI_BEGIN: 2020-06-01: Android_UI: SystemUI: support VoWIFI icons
            "VoWIFICall",
            0,
// QTI_END: 2020-06-01: Android_UI: SystemUI: support VoWIFI icons
            TelephonyIcons.ICON_VOWIFI_CALLING);

// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
    public static final MobileIconGroup FIVE_G_BASIC_6RX = new MobileIconGroup(
            "5GBasic_6Rx",
            R.string.data_connection_5g_basic,
            TelephonyIcons.ICON_5G_BASIC_6RX);

    public static final MobileIconGroup FIVE_G_UWB_6RX = new MobileIconGroup(
            "5GUWB_6Rx",
            R.string.data_connection_5g_uwb,
            TelephonyIcons.ICON_5G_UWB_6RX);

    public static final MobileIconGroup FIVE_G_PLUS_PLUS_6RX = new MobileIconGroup(
            "5G_PLUS_PLUS_6Rx",
            R.string.data_connection_5g_plus_plus,
            TelephonyIcons.ICON_5G_PLUS_PLUS_6RX);

// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
    /** Mapping icon name(lower case) to the icon object. */
    public static final Map<String, MobileIconGroup> ICON_NAME_TO_ICON;
    static {
        ICON_NAME_TO_ICON = new HashMap<>();
        ICON_NAME_TO_ICON.put("carrier_network_change", CARRIER_NETWORK_CHANGE);
        ICON_NAME_TO_ICON.put("3g", THREE_G);
        ICON_NAME_TO_ICON.put("wfc", WFC);
        ICON_NAME_TO_ICON.put("unknown", UNKNOWN);
        ICON_NAME_TO_ICON.put("e", E);
        ICON_NAME_TO_ICON.put("1x", ONE_X);
        ICON_NAME_TO_ICON.put("g", G);
        ICON_NAME_TO_ICON.put("h", H);
        ICON_NAME_TO_ICON.put("h+", H_PLUS);
        ICON_NAME_TO_ICON.put("4g", FOUR_G);
        ICON_NAME_TO_ICON.put("4g+", FOUR_G_PLUS);
        ICON_NAME_TO_ICON.put("4glte", FOUR_G_LTE);
        ICON_NAME_TO_ICON.put("4glte+", FOUR_G_LTE_PLUS);
        ICON_NAME_TO_ICON.put("5ge", LTE_CA_5G_E);
        ICON_NAME_TO_ICON.put("lte", LTE);
        ICON_NAME_TO_ICON.put("lte+", LTE_PLUS);
        ICON_NAME_TO_ICON.put("5g", NR_5G);
        ICON_NAME_TO_ICON.put("5gsa", NR_5G_SA);
        ICON_NAME_TO_ICON.put("5g_plus", NR_5G_PLUS);
// QTI_BEGIN: 2019-07-19: RIL: Show 5GUWB icon for mmWave using AOSP interface
        ICON_NAME_TO_ICON.put("5guwb", FIVE_G_UWB);
// QTI_END: 2019-07-19: RIL: Show 5GUWB icon for mmWave using AOSP interface
// QTI_BEGIN: 2023-12-17: Data: SystemUI: Enhanced 5g icon
        ICON_NAME_TO_ICON.put("5g_plus_plus", FIVE_G_PLUS_PLUS);
// QTI_END: 2023-12-17: Data: SystemUI: Enhanced 5g icon
        ICON_NAME_TO_ICON.put("datadisable", DATA_DISABLED);
        ICON_NAME_TO_ICON.put("notdefaultdata", NOT_DEFAULT_DATA);
// QTI_BEGIN: 2024-05-22: Android_UI: SystemUI: Display 5GA icon for 3CC
        ICON_NAME_TO_ICON.put("5ga", FIVE_G_A);
// QTI_END: 2024-05-22: Android_UI: SystemUI: Display 5GA icon for 3CC
    }

    public static final int[] MOBILE_CALL_STRENGTH_ICONS = {
        R.drawable.ic_mobile_call_strength_0,
        R.drawable.ic_mobile_call_strength_1,
        R.drawable.ic_mobile_call_strength_2,
        R.drawable.ic_mobile_call_strength_3,
        R.drawable.ic_mobile_call_strength_4
    };
}
