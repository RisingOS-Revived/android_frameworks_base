/*
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package android.telephony;

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.Objects;

/**
 * Holds the SNPN information.
 * @hide
 */
public final class SnpnInfo implements Parcelable {

    /**
     * This value indicates that the integer field is unreported.
     */
    public static final int UNAVAILABLE = Integer.MAX_VALUE;

    // Lifted from Default carrier configs and max range of SSRSRP
    // Boundaries: [-140 dB, -44 dB]
    private int[] mSsRsrpThresholds = new int[] {
            -110, /* SIGNAL_STRENGTH_POOR */
            -90, /* SIGNAL_STRENGTH_MODERATE */
            -80, /* SIGNAL_STRENGTH_GOOD */
            -65,  /* SIGNAL_STRENGTH_GREAT */
    };

    /** Defines the SNPN network ID.
     * This field has six bytes:
     * mNid[0] is the most significant byte
     * mNid[5] is the least significant byte
     */
    private byte[] mNid;

    /** Defines the mobile country code. */
    private String mMcc;

    /** Defines the mobile network code. */
    private String mMnc;

    /** 5 or 6 digit numeric code (MCC + MNC) */
    private String mOperatorNumeric;

    /** Defines the SNPN signal strength. */
    private int mSignalStrength;

    /** Defines the SNPN signal quality. */
    private int mSignalQuality;

    private int mLevel;

    /**
     * Returns the SNPN network ID.
     * @hide
     */
    public byte[] getNid() {
        return mNid;
    }

    /**
     * Returns the mobile country code.
     * @hide
     */
    public String getMcc() {
        return mMcc;
    }

    /**
     * Returns the mobile network code.
     * @hide
     */
    public String getMnc() {
        return mMnc;
    }

    /**
     * Returns the operator numeric code (MCC + MNC)
     * @hide
     */
    public String getOperatorNumeric() {
        return mOperatorNumeric;
    }

    /**
     * Returns the SNPN signal strength.
     * @hide
     */
    public int getSignalStrength() {
        return mSignalStrength;
    }

    /**
     * Returns the SNPN signal quality.
     * @hide
     */
    public int getSignalQuality() {
        return mSignalQuality;
    }

    /**
     * Returns the SNPN signal strength level.
     * @hide
     */
    public int getLevel() {
        return mLevel;
    }

    /** @hide */
    public SnpnInfo() {
        mNid = null;
        mMcc = null;
        mMnc = null;
        mOperatorNumeric = null;
        mSignalStrength = UNAVAILABLE;
        mSignalQuality = UNAVAILABLE;
        mLevel = CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
    }

    /**
     * SnpnInfo constructor.
     *
     * @param mNid SNPN network ID.
     * @param mMcc Mobile country code.
     * @param mMnc Mobile network code.
     * @param operatorNumeric operator numeric code.
     * @param mSignalStrength Indicates the SNPN signal strength.
     * @param mSignalQuality Indicates the SNPN signal quality.
     * @hide
     */
    public SnpnInfo(byte[] nid, String mcc, String mnc, String operatorNumeric, int signalStrength,
            int signalQuality) {
        mNid = nid;
        mMcc = mcc;
        mMnc = mnc;
        mOperatorNumeric = operatorNumeric;
        mSignalStrength = signalStrength;
        mSignalQuality = signalQuality;
        updateLevel();
    }

    /**
     * Construct a SnpnInfo object from the given SnpnInfo object.
     * @hide
     */
    public SnpnInfo(SnpnInfo s) {
        mNid = s.mNid;
        mMcc = s.mMcc;
        mMnc = s.mMnc;
        mOperatorNumeric = s.mOperatorNumeric;
        mSignalStrength = s.mSignalStrength;
        mSignalQuality = s.mSignalQuality;
        mLevel = s.mLevel;
    }

    /**
     * Construct a SnpnInfo object from the given parcel.
     * @hide
     */
    private SnpnInfo(Parcel in) {
        int arrayLength = in.readInt();
        if (arrayLength > 0) {
            mNid = new byte[arrayLength];
            in.readByteArray(mNid);
        } else {
            mNid = null;
        }
        mMcc = in.readString();
        mMnc = in.readString();
        mOperatorNumeric = in.readString();
        mSignalStrength = in.readInt();
        mSignalQuality = in.readInt();
        mLevel = in.readInt();
    }

    /** @hide */
    public void updateLevel() {
      int ssRsrpLevel = SignalStrength.INVALID;
      ssRsrpLevel = updateLevelWithMeasure(mSignalStrength, mSsRsrpThresholds);
      mLevel = ssRsrpLevel;
    }

    /**
     * Update level with corresponding measure and thresholds.
     *
     * @param measure corresponding signal measure
     * @param thresholds corresponding signal thresholds
     * @return level of the signal strength
     */
    private int updateLevelWithMeasure(int measure, int[] thresholds) {
        int level;
        if (measure == CellInfo.UNAVAILABLE) {
            level = CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        } else if (measure >= thresholds[3]) {
            level = CellSignalStrength.SIGNAL_STRENGTH_GREAT;
        } else if (measure >= thresholds[2]) {
            level = CellSignalStrength.SIGNAL_STRENGTH_GOOD;
        } else if (measure >= thresholds[1]) {
            level = CellSignalStrength.SIGNAL_STRENGTH_MODERATE;
        }  else if (measure >= thresholds[0]) {
            level = CellSignalStrength.SIGNAL_STRENGTH_POOR;
        } else {
            level = CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        }
        return level;
    }

    /**
     * String representation of the SnpnInfo.
     */
    @Override
    public String toString() {
        return "mNid: " + getNid() + ", mMcc: " + getMcc() + "mMnc: " + getMnc() +
                "mOperatorNumeric: " + getOperatorNumeric() + "mSignalStrength: " +
                getSignalStrength() + "mSignalQuality: " + getSignalQuality() +
                "mLevel: " + mLevel;
    }

    /**
     * Describe the contents of this object.
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Write this object to a Parcel.
     */
    @Override
    public void writeToParcel(Parcel out, int flags) {
        if (mNid != null && mNid.length > 0) {
             out.writeInt(mNid.length);
             out.writeByteArray(mNid);
        } else {
             out.writeInt(0);
        }
        out.writeString(mMcc);
        out.writeString(mMnc);
        out.writeString(mOperatorNumeric);
        out.writeInt(mSignalStrength);
        out.writeInt(mSignalQuality);
        out.writeInt(mLevel);
    }

    /**
     * Parcel creator class.
     */
    public static final @NonNull Parcelable.Creator<SnpnInfo> CREATOR = new Creator<SnpnInfo>() {
        public SnpnInfo createFromParcel(Parcel in) {
            return new SnpnInfo(in);
        }
        public SnpnInfo[] newArray(int size) {
            return new SnpnInfo[size];
        }
    };

    @Override
    public int hashCode() {
        int result = Objects.hash(mMcc, mMnc, mOperatorNumeric, mSignalStrength, mSignalQuality,
                mLevel);
        result = 31 * result + Arrays.hashCode(mNid);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        SnpnInfo other = (SnpnInfo) obj;
        return (Arrays.equals(mNid, other.mNid)
                && TextUtils.equals(mMcc, other.mMcc)
                && TextUtils.equals(mMnc, other.mMnc)
                && TextUtils.equals(mOperatorNumeric, other.mOperatorNumeric)
                && mSignalStrength == other.mSignalStrength
                && mSignalQuality == other.mSignalQuality
                && mLevel == other.mLevel);
    }
}
