/*
 * Copyright (C) 2015 The Android Open Source Project
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
 *
 * Changes from Qualcomm Innovation Center are provided under the following license:
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.systemui.statusbar.connectivity;

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.wifi.WifiManager.TrafficStateCallback.DATA_ACTIVITY_IN;
import static android.net.wifi.WifiManager.TrafficStateCallback.DATA_ACTIVITY_INOUT;
import static android.net.wifi.WifiManager.TrafficStateCallback.DATA_ACTIVITY_OUT;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.text.Html;
import android.net.wifi.ScanResult;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;
import com.android.settingslib.AccessibilityContentDescriptions;
import com.android.settingslib.SignalIcon.IconGroup;
import com.android.settingslib.SignalIcon.MobileIconGroup;
import com.android.settingslib.graph.SignalDrawable;
import com.android.settingslib.mobile.TelephonyIcons;
import com.android.settingslib.wifi.WifiStatusTracker;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.pipeline.wifi.domain.interactor.WifiInteractor;

import java.io.PrintWriter;
import java.util.BitSet;

/** @deprecated use {@link WifiInteractor} instead. */
@Deprecated
public class WifiSignalController extends SignalController<WifiState, IconGroup> {
    private final boolean mHasMobileDataFeature;
    private final WifiStatusTracker mWifiTracker;
    private final IconGroup mUnmergedWifiIconGroup = WifiIcons.UNMERGED_WIFI;
    private final MobileIconGroup mCarrierMergedWifiIconGroup = TelephonyIcons.CARRIER_MERGED_WIFI;
    private final WifiManager mWifiManager;

    private final Handler mBgHandler;

    private final IconGroup mDefaultWifiIconGroup;
    private final IconGroup mWifi4IconGroup;
    private final IconGroup mWifi5IconGroup;
    private final IconGroup mWifi6IconGroup;
    private final IconGroup mWifi7IconGroup;

    public WifiSignalController(
            Context context,
            boolean hasMobileDataFeature,
            CallbackHandler callbackHandler,
            NetworkControllerImpl networkController,
            WifiManager wifiManager,
            WifiStatusTrackerFactory trackerFactory,
            @Background Handler bgHandler) {
        super(
                "WifiSignalController",
                context,
                TRANSPORT_WIFI,
                callbackHandler,
                networkController);
        mBgHandler = bgHandler;
        mWifiManager = wifiManager;
        mWifiTracker = trackerFactory.createTracker(this::handleStatusUpdated, bgHandler);
        mWifiTracker.setListening(true);
        mHasMobileDataFeature = hasMobileDataFeature;
        if (wifiManager != null) {
            wifiManager.registerTrafficStateCallback(context.getMainExecutor(),
                    new WifiTrafficStateCallback());
        }

        mDefaultWifiIconGroup = new IconGroup(
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

        mWifi4IconGroup = new IconGroup(
                "Wi-Fi 4 Icons",
                WifiIcons.WIFI_4_SIGNAL_STRENGTH,
                WifiIcons.QS_WIFI_4_SIGNAL_STRENGTH,
                AccessibilityContentDescriptions.WIFI_CONNECTION_STRENGTH,
                WifiIcons.WIFI_NO_NETWORK,
                WifiIcons.QS_WIFI_NO_NETWORK,
                WifiIcons.WIFI_NO_NETWORK,
                WifiIcons.QS_WIFI_NO_NETWORK,
                AccessibilityContentDescriptions.WIFI_NO_CONNECTION
                );

        mWifi5IconGroup = new IconGroup(
                "Wi-Fi 5 Icons",
                WifiIcons.WIFI_5_SIGNAL_STRENGTH,
                WifiIcons.QS_WIFI_5_SIGNAL_STRENGTH,
                AccessibilityContentDescriptions.WIFI_CONNECTION_STRENGTH,
                WifiIcons.WIFI_NO_NETWORK,
                WifiIcons.QS_WIFI_NO_NETWORK,
                WifiIcons.WIFI_NO_NETWORK,
                WifiIcons.QS_WIFI_NO_NETWORK,
                AccessibilityContentDescriptions.WIFI_NO_CONNECTION
                );

        mWifi6IconGroup = new IconGroup(
                "Wi-Fi 6 Icons",
                WifiIcons.WIFI_6_SIGNAL_STRENGTH,
                WifiIcons.QS_WIFI_6_SIGNAL_STRENGTH,
                AccessibilityContentDescriptions.WIFI_CONNECTION_STRENGTH,
                WifiIcons.WIFI_NO_NETWORK,
                WifiIcons.QS_WIFI_NO_NETWORK,
                WifiIcons.WIFI_NO_NETWORK,
                WifiIcons.QS_WIFI_NO_NETWORK,
                AccessibilityContentDescriptions.WIFI_NO_CONNECTION
                );

        mWifi7IconGroup = new IconGroup(
                "Wi-Fi 7 Icons",
                WifiIcons.WIFI_7_SIGNAL_STRENGTH,
                WifiIcons.QS_WIFI_7_SIGNAL_STRENGTH,
                AccessibilityContentDescriptions.WIFI_CONNECTION_STRENGTH,
                WifiIcons.WIFI_NO_NETWORK,
                WifiIcons.QS_WIFI_NO_NETWORK,
                WifiIcons.WIFI_NO_NETWORK,
                WifiIcons.QS_WIFI_NO_NETWORK,
                AccessibilityContentDescriptions.WIFI_NO_CONNECTION
                );

        mCurrentState.iconGroup = mLastState.iconGroup = mDefaultWifiIconGroup;
    }

    @Override
    protected WifiState cleanState() {
        return new WifiState();
    }

    void refreshLocale() {
        mWifiTracker.refreshLocale();
    }

    @Override
    public void notifyListeners(SignalCallback callback) {
        if (mCurrentState.isCarrierMerged) {
            if (mCurrentState.isDefault || !mNetworkController.isRadioOn()) {
                notifyListenersForCarrierWifi(callback);
            }
        } else {
            notifyListenersForNonCarrierWifi(callback);
        }
    }

    private void notifyListenersForNonCarrierWifi(SignalCallback callback) {
        // only show wifi in the cluster if connected or if wifi-only
        boolean visibleWhenEnabled = mContext.getResources().getBoolean(
                R.bool.config_showWifiIndicatorWhenEnabled);
        boolean wifiVisible = mCurrentState.enabled && (
                (mCurrentState.connected && mCurrentState.inetCondition == 1)
                        || !mHasMobileDataFeature || mCurrentState.isDefault
                        || visibleWhenEnabled);
        String wifiDesc = mCurrentState.connected ? mCurrentState.ssid : null;
        boolean ssidPresent = wifiVisible && mCurrentState.ssid != null;
        String contentDescription = getTextIfExists(getContentDescription()).toString();
        if (mCurrentState.inetCondition == 0) {
            contentDescription += ("," + mContext.getString(R.string.data_connection_no_internet));
        }
        IconState statusIcon = new IconState(
                wifiVisible, getCurrentIconId(), contentDescription);
        IconState qsIcon = new IconState(mCurrentState.connected,
                mWifiTracker.isCaptivePortal ? R.drawable.ic_qs_wifi_disconnected
                        : getQsCurrentIconId(), contentDescription);
        boolean isDefault = mCurrentState.isDefault || (!mNetworkController.isRadioOn()
                && !mNetworkController.isEthernetDefault());
        WifiIndicators wifiIndicators = new WifiIndicators(
                mCurrentState.enabled, statusIcon, qsIcon,
                ssidPresent && mCurrentState.activityIn,
                ssidPresent && mCurrentState.activityOut,
                wifiDesc, mCurrentState.isTransient, mCurrentState.statusLabel, isDefault
        );
        callback.setWifiIndicators(wifiIndicators);
    }

    private void notifyListenersForCarrierWifi(SignalCallback callback) {
        MobileIconGroup icons = mCarrierMergedWifiIconGroup;
        String contentDescription = getTextIfExists(getContentDescription()).toString();
        CharSequence dataContentDescriptionHtml = getTextIfExists(icons.dataContentDescription);

        CharSequence dataContentDescription = Html.fromHtml(
                dataContentDescriptionHtml.toString(), 0).toString();
        if (mCurrentState.inetCondition == 0) {
            dataContentDescription = mContext.getString(R.string.data_connection_no_internet);
        }
        boolean sbVisible = mCurrentState.enabled && mCurrentState.connected
                && mCurrentState.isDefault;
        IconState statusIcon =
                new IconState(sbVisible, getCurrentIconIdForCarrierWifi(), contentDescription);
        int typeIcon = sbVisible ? icons.dataType : 0;
        int qsTypeIcon = 0;
        // TODO(b/178561525) Populate volteIcon value as necessary
        int volteIcon = 0;
        IconState qsIcon = null;
        if (sbVisible) {
            qsTypeIcon = icons.dataType;
            qsIcon = new IconState(mCurrentState.connected, getQsCurrentIconIdForCarrierWifi(),
                    contentDescription);
        }
        CharSequence description =
                mNetworkController.getNetworkNameForCarrierWiFi(mCurrentState.subId);
        MobileDataIndicators mobileDataIndicators = new MobileDataIndicators(
                statusIcon, qsIcon, typeIcon, qsTypeIcon,
                mCurrentState.activityIn, mCurrentState.activityOut, volteIcon,
                dataContentDescription, dataContentDescriptionHtml, description,
                mCurrentState.subId, /* roaming= */ false, /* showTriangle= */ true,
                /* isDefault= */ qsIcon != null
        );
        callback.setMobileDataIndicators(mobileDataIndicators);
    }

    private int getCurrentIconIdForCarrierWifi() {
        int level = mCurrentState.level;
        // The WiFi signal level returned by WifiManager#calculateSignalLevel start from 0, so
        // WifiManager#getMaxSignalLevel + 1 represents the total level buckets count.
        int totalLevel = mWifiManager.getMaxSignalLevel() + 1;
        // A carrier merged connection could come from a WIFI *or* CELLULAR transport, so we can't
        // use [mCurrentState.inetCondition], which only checks the WIFI status. Instead, check if
        // the default connection is validated at all.
        boolean noInternet = !mCurrentState.isDefaultConnectionValidated;
        if (mCurrentState.connected) {
            return SignalDrawable.getState(level, totalLevel, noInternet);
        } else if (mCurrentState.enabled) {
            return SignalDrawable.getEmptyState(totalLevel);
        } else {
            return 0;
        }
    }

    private int getQsCurrentIconIdForCarrierWifi() {
        return getCurrentIconIdForCarrierWifi();
    }


    private void updateIconGroup() {
	if (mCurrentState.wifiStandard == ScanResult.WIFI_STANDARD_11N) {
            mCurrentState.iconGroup = mWifi4IconGroup;
        } else if (mCurrentState.wifiStandard == ScanResult.WIFI_STANDARD_11AC) {
            mCurrentState.iconGroup = mWifi5IconGroup;
        } else if (mCurrentState.wifiStandard == ScanResult.WIFI_STANDARD_11AX) {
            mCurrentState.iconGroup = mWifi6IconGroup;
        } else if (mCurrentState.wifiStandard == ScanResult.WIFI_STANDARD_11BE) {
            mCurrentState.iconGroup = mWifi7IconGroup;
        } else {
            mCurrentState.iconGroup = mDefaultWifiIconGroup;
        }

    }
    /**
     * Fetches wifi initial state replacing the initial sticky broadcast.
     */
    public void fetchInitialState() {
        doInBackground(() -> {
            mWifiTracker.fetchInitialState();
            copyWifiStates();
            notifyListenersIfNecessary();
        });
    }

    /**
     * Extract wifi state directly from broadcasts about changes in wifi state.
     */
    void handleBroadcast(Intent intent) {
        doInBackground(() -> {
            mWifiTracker.handleBroadcast(intent);
            copyWifiStates();
            notifyListenersIfNecessary();
        });
    }

    private void handleStatusUpdated() {
        // The WifiStatusTracker callback comes in on the main thread, but the rest of our data
        // access happens on the bgHandler
        doInBackground(() -> {
            copyWifiStates();
            notifyListenersIfNecessary();
        });
    }

    private void doInBackground(Runnable action) {
        if (Thread.currentThread() != mBgHandler.getLooper().getThread()) {
            mBgHandler.post(action);
        } else {
            action.run();
        }
    }

    private void copyWifiStates() {
        // Data access should only happen on our bg thread
        Preconditions.checkState(mBgHandler.getLooper().isCurrentThread());

        mCurrentState.enabled = mWifiTracker.enabled;
        mCurrentState.isDefault = mWifiTracker.isDefaultNetwork;
        mCurrentState.connected = mWifiTracker.connected;
        mCurrentState.ssid = mWifiTracker.ssid;
        mCurrentState.rssi = mWifiTracker.rssi;
        mCurrentState.level = mWifiTracker.level;
        mCurrentState.statusLabel = mWifiTracker.statusLabel;
        mCurrentState.isCarrierMerged = mWifiTracker.isCarrierMerged;
        mCurrentState.subId = mWifiTracker.subId;
        mCurrentState.wifiStandard = mWifiTracker.wifiStandard;
        updateIconGroup();
    }

    boolean isCarrierMergedWifi(int subId) {
        return mCurrentState.isDefault
                && mCurrentState.isCarrierMerged && (mCurrentState.subId == subId);
    }

    @Override
    void updateConnectivity(BitSet connectedTransports, BitSet validatedTransports) {
        mCurrentState.inetCondition = validatedTransports.get(mTransportType) ? 1 : 0;
        // Because a carrier merged connection can come from either a CELLULAR *or* WIFI transport,
        // we need to also store if either transport is validated to correctly display the carrier
        // merged case.
        mCurrentState.isDefaultConnectionValidated =
                validatedTransports.get(TRANSPORT_CELLULAR)
                        || validatedTransports.get(TRANSPORT_WIFI);
        notifyListenersIfNecessary();
    }

    @VisibleForTesting
    void setActivity(int wifiActivity) {
        mCurrentState.activityIn = wifiActivity == DATA_ACTIVITY_INOUT
                || wifiActivity == DATA_ACTIVITY_IN;
        mCurrentState.activityOut = wifiActivity == DATA_ACTIVITY_INOUT
                || wifiActivity == DATA_ACTIVITY_OUT;
        notifyListenersIfNecessary();
    }

    @Override
    public void dump(PrintWriter pw) {
        super.dump(pw);
        mWifiTracker.dump(pw);
        dumpTableData(pw);
    }

    /**
     * Handler to receive the data activity on wifi.
     */
    private class WifiTrafficStateCallback implements WifiManager.TrafficStateCallback {
        @Override
        public void onStateChanged(int state) {
            setActivity(state);
        }
    }
}
