/*
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package android.telephony;

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.util.Objects;

/**
 * Holds the CAG information.
 * @hide
 */
public final class CagInfo implements Parcelable {

    /**
     * This value indicates that the long field is unreported.
     */

    public static final long UNAVAILABLE_LONG = Long.MAX_VALUE;
    /** Defines the name of CAG Cell. */
    private String mCagName;

    /** Defines the CAG ID of CAG Cell. */
    private long mCagId;

    /** Indicates if PLMN is CAG only access. */
    private boolean mCagOnlyAccess;

    /** Indicates the presence of [PLMN, CAG_ID] combination. */
    private boolean mCagInAllowedList;

    /**
     * Returns the name of CAG cell.
     * @hide
     */
    public String getCagName() {
        return mCagName;
    }

    /**
     * Returns the CAG ID of CAG cell.
     * @hide
     */
    public long getCagId() {
        return mCagId;
    }

    /**
     * Returns whether PLMN is CAG only access.
     * FALSE – PLMN is not CAG only access.
     * TRUE – PLMN is CAG only access.
     * @hide
     */
    public boolean getCagOnlyAccess() {
        return mCagOnlyAccess;
    }

    /**
     * Returns whether [PLMN, CAG_ID] combination is present.
     * FALSE – [PLMN, CAG_ID] combination is not present.
     * TRUE – [PLMN, CAG_ID] combination is present.
     * @hide
     */
    public boolean getCagInAllowedList() {
        return mCagInAllowedList;
    }

    /** @hide */
    public CagInfo() {
        mCagName = new String();
        mCagId = UNAVAILABLE_LONG;
        mCagOnlyAccess = false;
        mCagInAllowedList = false;
    }

    /**
     * CagInfo constructor.
     *
     * @param mCagName Name of CAG cell.
     * @param mCagId CAG ID of CAG cell.
     * @param mCagOnlyAccess Indicates if PLMN is CAG only access.
     * @param mCagInAllowedList Indicates the presence of [PLMN, CAG_ID] combination.
     * @hide
     */
    public CagInfo(String cagName, long cagId, boolean cagOnlyAccess, boolean cagInAllowedList) {
        mCagName = cagName;
        mCagId = cagId;
        mCagOnlyAccess = cagOnlyAccess;
        mCagInAllowedList = cagInAllowedList;
    }

    /**
     * Construct a CagInfo object from the given parcel.
     * @hide
     */
    private CagInfo(Parcel in) {
        mCagName = in.readString();
        mCagId = in.readLong();
        mCagOnlyAccess = in.readBoolean();
        mCagInAllowedList = in.readBoolean();
    }

    /**
     * String representation of the CagInfo.
     */
    @Override
    public String toString() {
        return "mCagName: " + getCagName() + ", mCagId: " + getCagId() + "mCagOnlyAccess: " +
                getCagOnlyAccess() + "mCagInAllowedList: " + getCagInAllowedList();
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
        out.writeString(mCagName);
        out.writeLong(mCagId);
        out.writeBoolean(mCagOnlyAccess);
        out.writeBoolean(mCagInAllowedList);
    }

    /**
     * Parcel creator class.
     */
    public static final @NonNull Parcelable.Creator<CagInfo> CREATOR = new Creator<CagInfo>() {
        public CagInfo createFromParcel(Parcel in) {
            return new CagInfo(in);
        }
        public CagInfo[] newArray(int size) {
            return new CagInfo[size];
        }
    };

    @Override
    public int hashCode() {
        return Objects.hash(mCagName, mCagId, mCagOnlyAccess, mCagInAllowedList);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        CagInfo other = (CagInfo) obj;
        return (TextUtils.equals(mCagName, other.mCagName)
                && mCagId == other.mCagId
                && mCagOnlyAccess == other.mCagOnlyAccess
                && mCagInAllowedList == other.mCagInAllowedList);
    }
}
