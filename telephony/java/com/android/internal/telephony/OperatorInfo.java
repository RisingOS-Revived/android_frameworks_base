/*
 * Copyright (C) 2006 The Android Open Source Project
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

// QTI_BEGIN: 2022-09-11: Telephony: CAG and SNPN feature
/*
 * Changes from Qualcomm Innovation Center are provided under the following license:
 *
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

// QTI_END: 2022-09-11: Telephony: CAG and SNPN feature
package com.android.internal.telephony;

import android.compat.annotation.UnsupportedAppUsage;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
// QTI_BEGIN: 2022-09-11: Telephony: CAG and SNPN feature
import android.telephony.CagInfo;
import android.telephony.SnpnInfo;
// QTI_END: 2022-09-11: Telephony: CAG and SNPN feature

/**
 * @hide
 */
public class OperatorInfo implements Parcelable {
// QTI_BEGIN: 2022-09-11: Telephony: CAG and SNPN feature
    /** Invalid access mode */
    /** @hide */
    public static final int ACCESS_MODE_INVALID = 0;

    /** PLMN access mode */
    /** @hide */
    public static final int ACCESS_MODE_PLMN = 1;

    /** SNPN access mode */
    /** @hide */
    public static final int ACCESS_MODE_SNPN = 2;

// QTI_END: 2022-09-11: Telephony: CAG and SNPN feature
    public enum State {
        UNKNOWN,
        AVAILABLE,
        @UnsupportedAppUsage
        CURRENT,
        @UnsupportedAppUsage
        FORBIDDEN;
    }

    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    private String mOperatorAlphaLong;
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    private String mOperatorAlphaShort;
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    private String mOperatorNumeric;

    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    private State mState = State.UNKNOWN;
    private int mRan = AccessNetworkType.UNKNOWN;
// QTI_BEGIN: 2022-09-11: Telephony: CAG and SNPN feature
    /**
     * Describes the access mode
     */
    private int mAccessMode = ACCESS_MODE_PLMN;

    /** Defines the CAG information. */
    private CagInfo mCagInfo;

    /** Defines the SNPN information. */
    private SnpnInfo mSnpnInfo;
// QTI_END: 2022-09-11: Telephony: CAG and SNPN feature


    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    public String
    getOperatorAlphaLong() {
        return mOperatorAlphaLong;
    }

    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    public String
    getOperatorAlphaShort() {
        return mOperatorAlphaShort;
    }

    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    public String
    getOperatorNumeric() {
        return mOperatorNumeric;
    }

    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    public State
    getState() {
        return mState;
    }

    public int getRan() {
        return mRan;
    }

// QTI_BEGIN: 2022-09-11: Telephony: CAG and SNPN feature
    /** Returns the access mode */
    /** @hide */
    public int getAccessMode() {
        return mAccessMode;
    }

    /**
     * Returns the CAG information.
     * @hide
     */
    public CagInfo getCagInfo() {
        return mCagInfo;
    }

    /**
     * Returns the SNPN information.
     * @hide
     */
    public SnpnInfo getSnpnInfo() {
        return mSnpnInfo;
    }

// QTI_END: 2022-09-11: Telephony: CAG and SNPN feature
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    OperatorInfo(String operatorAlphaLong,
                String operatorAlphaShort,
                String operatorNumeric,
                State state) {

        mOperatorAlphaLong = operatorAlphaLong;
        mOperatorAlphaShort = operatorAlphaShort;
        mOperatorNumeric = operatorNumeric;

        mState = state;
    }

    OperatorInfo(String operatorAlphaLong,
                String operatorAlphaShort,
                String operatorNumeric,
                State state,
                int ran) {
        this (operatorAlphaLong, operatorAlphaShort, operatorNumeric, state);
        mRan = ran;
    }

    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    public OperatorInfo(String operatorAlphaLong,
                String operatorAlphaShort,
                String operatorNumeric,
                String stateString) {
        this (operatorAlphaLong, operatorAlphaShort,
                operatorNumeric, rilStateToState(stateString));
    }

    public OperatorInfo(String operatorAlphaLong,
                String operatorAlphaShort,
                String operatorNumeric,
                int ran) {
        this (operatorAlphaLong, operatorAlphaShort, operatorNumeric);
        mRan = ran;
    }

    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.P, trackingBug = 115609023)
    public OperatorInfo(String operatorAlphaLong,
            String operatorAlphaShort,
            String operatorNumeric) {
        this(operatorAlphaLong, operatorAlphaShort, operatorNumeric, State.UNKNOWN);
    }

// QTI_BEGIN: 2022-09-11: Telephony: CAG and SNPN feature
    /** @hide */
    public OperatorInfo(String operatorAlphaLong,
                String operatorAlphaShort,
                String operatorNumeric,
                int ran, int accessMode,
                CagInfo cagInfo, SnpnInfo snpnInfo) {
        this(operatorAlphaLong, operatorAlphaShort, operatorNumeric, ran);
        mAccessMode = accessMode;
        mCagInfo = cagInfo;
        mSnpnInfo = snpnInfo;
    }

    /** @hide */
    public OperatorInfo(String operatorAlphaLong,
                String operatorAlphaShort,
                String operatorNumeric,
                State state,
                int ran, int accessMode,
                CagInfo cagInfo, SnpnInfo snpnInfo) {
        this (operatorAlphaLong, operatorAlphaShort, operatorNumeric, state, ran);
        mAccessMode = accessMode;
        mCagInfo = cagInfo;
        mSnpnInfo = snpnInfo;
    }

    /**
     * Construct a SnpnInfo object from the given parcel.
     * @hide
     */
    private OperatorInfo(Parcel in) {
        mOperatorAlphaLong = in.readString();
        mOperatorAlphaShort = in.readString();
        mOperatorNumeric = in.readString();
        mState = (State) in.readSerializable(
                com.android.internal.telephony.OperatorInfo.State.class.getClassLoader(),
                com.android.internal.telephony.OperatorInfo.State.class);
        mRan = in.readInt();
        mAccessMode = in.readInt();
        mCagInfo = in.readParcelable(CagInfo.class.getClassLoader(), CagInfo.class);
        mSnpnInfo = in.readParcelable(SnpnInfo.class.getClassLoader(), SnpnInfo.class);
    }

// QTI_END: 2022-09-11: Telephony: CAG and SNPN feature
    /**
     * See state strings defined in ril.h RIL_REQUEST_QUERY_AVAILABLE_NETWORKS
     */
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    private static State rilStateToState(String s) {
        if (s.equals("unknown")) {
            return State.UNKNOWN;
        } else if (s.equals("available")) {
            return State.AVAILABLE;
        } else if (s.equals("current")) {
            return State.CURRENT;
        } else if (s.equals("forbidden")) {
            return State.FORBIDDEN;
        } else {
            throw new RuntimeException(
                "RIL impl error: Invalid network state '" + s + "'");
        }
    }


    @Override
    public String toString() {
        return "OperatorInfo " + mOperatorAlphaLong
                + "/" + mOperatorAlphaShort
                + "/" + mOperatorNumeric
                + "/" + mState
// QTI_BEGIN: 2022-09-11: Telephony: CAG and SNPN feature
                + "/" + mRan
                + "/" + mAccessMode
                + "/" + mCagInfo
                + "/" + mSnpnInfo;
// QTI_END: 2022-09-11: Telephony: CAG and SNPN feature
    }

    /**
     * Parcelable interface implemented below.
     * This is a simple effort to make OperatorInfo parcelable rather than
     * trying to make the conventional containing object (AsyncResult),
     * implement parcelable.  This functionality is needed for the
     * NetworkQueryService to fix 1128695.
     */

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Implement the Parcelable interface.
     * Method to serialize a OperatorInfo object.
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mOperatorAlphaLong);
        dest.writeString(mOperatorAlphaShort);
        dest.writeString(mOperatorNumeric);
        dest.writeSerializable(mState);
        dest.writeInt(mRan);
// QTI_BEGIN: 2022-09-11: Telephony: CAG and SNPN feature
        dest.writeInt(mAccessMode);
        dest.writeParcelable(mCagInfo, 0);
        dest.writeParcelable(mSnpnInfo, 0);
// QTI_END: 2022-09-11: Telephony: CAG and SNPN feature
    }

    /**
     * Implement the Parcelable interface
     * Method to deserialize a OperatorInfo object, or an array thereof.
     */
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    public static final Creator<OperatorInfo> CREATOR =
            new Creator<OperatorInfo>() {
                @Override
                public OperatorInfo createFromParcel(Parcel in) {
// QTI_BEGIN: 2022-09-11: Telephony: CAG and SNPN feature
                    return new OperatorInfo(in);
// QTI_END: 2022-09-11: Telephony: CAG and SNPN feature
                }

                @Override
                public OperatorInfo[] newArray(int size) {
                    return new OperatorInfo[size];
                }
            };
}
