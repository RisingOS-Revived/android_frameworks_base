/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.keyguard;

import static com.android.systemui.util.PluralMessageFormaterKt.icuMessageFormat;

import android.content.Context;
import android.telephony.SubscriptionInfo;
import android.util.AttributeSet;
import android.util.Log;

import com.android.systemui.Dependency;
import com.android.systemui.res.R;

// QTI_BEGIN: 2020-02-10: Android_UI: SystemUI: Add resource for customization puk prompt
import java.util.HashMap;
import java.util.Map;
// QTI_END: 2020-02-10: Android_UI: SystemUI: Add resource for customization puk prompt

/**
 * Displays a PIN pad for entering a PUK (Pin Unlock Kode) provided by a carrier.
 */
public class KeyguardSimPukView extends KeyguardSimInputView {
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    public static final String TAG = "KeyguardSimPukView";
// QTI_BEGIN: 2020-02-10: Android_UI: SystemUI: Add resource for customization puk prompt
    private Map<String, String> mWrongPukCodeMessageMap =  new HashMap<>(4);
// QTI_END: 2020-02-10: Android_UI: SystemUI: Add resource for customization puk prompt

    public KeyguardSimPukView(Context context) {
        this(context, null);
    }

    public KeyguardSimPukView(Context context, AttributeSet attrs) {
        super(context, attrs);
// QTI_BEGIN: 2020-02-10: Android_UI: SystemUI: Add resource for customization puk prompt
        updateWrongPukMessageMap(context);
    }

    void updateWrongPukMessageMap(Context context) {
        String[] customizationConfigs = context.getResources().
// QTI_END: 2020-02-10: Android_UI: SystemUI: Add resource for customization puk prompt
                getStringArray(com.android.settingslib.R.array.kg_wrong_puk_code_message_list);
// QTI_BEGIN: 2020-02-10: Android_UI: SystemUI: Add resource for customization puk prompt
        if ( customizationConfigs.length == 0 ){
            Log.d(TAG, "There is no customization PUK prompt");
            return;
        }
        for(String config : customizationConfigs ) {
            String[] kv = config.trim().split(":");
            if ( kv.length != 2) {
                Log.e(TAG, "invalid key value config " + config);
                continue;
            }
            mWrongPukCodeMessageMap.put(kv[0], kv[1]);
        }
    }

    private String getMessageTextForWrongPukCode(int subId) {
        String message = null;
        SubscriptionInfo info = Dependency.get(KeyguardUpdateMonitor.class)
// QTI_END: 2020-02-10: Android_UI: SystemUI: Add resource for customization puk prompt
                    .getSubscriptionInfoForSubId(subId);
// QTI_BEGIN: 2020-02-10: Android_UI: SystemUI: Add resource for customization puk prompt
        if ( info != null ) {
            String mccMNC = info.getMccString()+info.getMncString();
            message = mWrongPukCodeMessageMap.get(mccMNC);
        }
        return message;
// QTI_END: 2020-02-10: Android_UI: SystemUI: Add resource for customization puk prompt
    }

    @Override
    protected int getPromptReasonStringRes(int reason) {
        // No message on SIM Puk
        return 0;
    }

    String getPukPasswordErrorMessage(
            int attemptsRemaining, boolean isDefault, boolean isEsimLocked, int subId) {
        String displayMessage;

        if (attemptsRemaining == 0) {
            String message = getMessageTextForWrongPukCode(subId);
// QTI_BEGIN: 2020-02-10: Android_UI: SystemUI: Add resource for customization puk prompt
            if ( message == null ) {
                displayMessage = getContext().getString(R.string.kg_password_wrong_puk_code_dead);
            }else {
                displayMessage = message;
            }
// QTI_END: 2020-02-10: Android_UI: SystemUI: Add resource for customization puk prompt
        } else if (attemptsRemaining > 0) {
            int msgId = isDefault ? R.string.kg_password_default_puk_message :
                    R.string.kg_password_wrong_puk_code;
            displayMessage = icuMessageFormat(getResources(), msgId, attemptsRemaining);
        } else {
            int msgId = isDefault ? R.string.kg_puk_enter_puk_hint :
                    R.string.kg_password_puk_failed;
            displayMessage = getContext().getString(msgId);
        }
        if (isEsimLocked) {
            displayMessage = getResources()
                    .getString(R.string.kg_sim_lock_esim_instructions, displayMessage);
        }
        if (DEBUG) {
            Log.d(TAG, "getPukPasswordErrorMessage:"
                    + " attemptsRemaining=" + attemptsRemaining
                    + " displayMessage=" + displayMessage);
        }
        return displayMessage;
    }

    @Override
    protected int getPasswordTextViewId() {
        return R.id.pukEntry;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        if (mEcaView instanceof EmergencyCarrierArea) {
            ((EmergencyCarrierArea) mEcaView).setCarrierTextVisible(true);
        }
    }

    @Override
    public void startAppearAnimation() {
        // noop.
    }

    @Override
    public CharSequence getTitle() {
        return getContext().getString(
                com.android.internal.R.string.keyguard_accessibility_sim_puk_unlock);
    }
}
