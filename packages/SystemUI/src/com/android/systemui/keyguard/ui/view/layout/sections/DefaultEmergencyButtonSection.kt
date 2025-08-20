/*
    Copyright (c) 2025 Qualcomm Innovation Center, Inc. All rights reserved.
    SPDX-License-Identifier: BSD-3-Clause-Clear
*/

package com.android.systemui.keyguard.ui.view.layout.sections


import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.keyguard.EmergencyButton
import com.android.keyguard.EmergencyButtonController
import com.android.keyguard.EmergencyCarrierArea
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware

import javax.inject.Inject

class DefaultEmergencyButtonSection
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    private val emergencyButtonControllerFactory: EmergencyButtonController.Factory,
) : KeyguardSection() {

    private val keyguardEmergencyAreaId = R.id.keyguard_emergency_area
    private val showEmergencyButtonOnKeyguard: Boolean = context.resources.getBoolean(
        com.android.settingslib.R.bool.config_showEmergencyButton)

    override fun addViews(constraintLayout: ConstraintLayout) {
        if (!showEmergencyButtonOnKeyguard) {
            return
        }
        val view =
            LayoutInflater.from(constraintLayout.context)
                .inflate(R.layout.keyguard_emergency_carrier_area, constraintLayout, false)
                    as EmergencyCarrierArea
        view.id = keyguardEmergencyAreaId
        constraintLayout.addView(view)
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        if (!showEmergencyButtonOnKeyguard) {
            return
        }

        val emergencyButton =
            constraintLayout.findViewById<EmergencyButton>(R.id.emergency_call_button) ?: return

        val controller = emergencyButtonControllerFactory.create(emergencyButton)
        controller.init()
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        if (!showEmergencyButtonOnKeyguard) {
            return
        }
        constraintSet.apply {
            constrainWidth(keyguardEmergencyAreaId, ViewGroup.LayoutParams.WRAP_CONTENT)
            constrainHeight(keyguardEmergencyAreaId, ViewGroup.LayoutParams.WRAP_CONTENT)
            connect(keyguardEmergencyAreaId,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM,
                context.resources.getDimensionPixelSize(
                    R.dimen.keyguard_emergency_button_bottom_margin))
            connect(keyguardEmergencyAreaId,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START)
            connect(keyguardEmergencyAreaId,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END)
        }
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        if (!showEmergencyButtonOnKeyguard) {
            return
        }
        constraintLayout.removeView(keyguardEmergencyAreaId)
    }
}