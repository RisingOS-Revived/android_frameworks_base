/*
 * SPDX-FileCopyrightText: 2023-2024 the risingOS Android Project
 * SPDX-FileCopyrightText: 2024 TheParasiteProject
 * SPDX-FileCopyrightText: 2025 DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.island

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.IconDrawableFactory
import android.util.Log
import java.util.Locale

class IslandNotificationManager(private val context: Context) {
    companion object {
        private const val TAG = "IslandNotificationManager"
    }

    fun resolveNotificationContent(notification: Notification): Pair<String, String> {
        val titleText = notification.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: notification.extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: notification.extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
            ?: ""
        val contentText = notification.extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ""
        return titleText.toString() to contentText.toString()
    }

    fun getDrawableFromExtras(extras: Bundle, key: String, context: Context): Drawable? {
        val iconObject = extras.get(key) ?: return null
        return when (iconObject) {
            is Icon -> iconObject.loadDrawable(context)
            is Drawable -> iconObject
            else -> null
        }
    }

    fun getNotificationIcon(sbn: StatusBarNotification, notification: Notification): Drawable? {
        return try {
            if ("com.android.systemui" == sbn.packageName) {
                context.getDrawable(notification.icon)
            } else {
                val iconFactory: IconDrawableFactory = IconDrawableFactory.newInstance(context)
                iconFactory.getBadgedIcon(getApplicationInfo(sbn), sbn.user.identifier)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Failed to get notification icon", e)
            null
        }
    }

    fun getApplicationInfo(sbn: StatusBarNotification): ApplicationInfo {
        return context.packageManager.getApplicationInfoAsUser(
            sbn.packageName,
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            sbn.user.identifier)
    }

    fun getAppLabel(packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Failed to get app label", e)
            packageName
        }
    }

    fun buildSpannableText(
        title: SpannableString,
        content: String,
        subContent: String,
        singleLine: Boolean,
        subtitleColor: Int
    ): SpannableStringBuilder {
        return SpannableStringBuilder().apply {
            append(title as CharSequence)
            if (content.isNotBlank()) {
                appendSpannable(content, 0.9f, singleLine, subtitleColor)
            }
            if (subContent.isNotBlank()) {
                appendSpannable(subContent, 0.85f, singleLine, subtitleColor)
            }
        }
    }

    private fun SpannableStringBuilder.appendSpannable(
        spanText: String,
        size: Float,
        singleLine: Boolean,
        subtitleColor: Int
    ) {
        if (!spanText.isBlank()) {
            val spannableText = SpannableString(spanText).apply {
                setSpan(ForegroundColorSpan(subtitleColor), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(RelativeSizeSpan(size), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            append(if (!singleLine) "\n" else " ")
            append(spannableText)
        }
    }
} 
