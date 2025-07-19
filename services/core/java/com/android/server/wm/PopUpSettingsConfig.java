/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import static android.provider.Settings.System.POP_UP_DOUBLE_TAP_ACTION;
import static android.provider.Settings.System.POP_UP_KEEP_MUTE_IN_MINI;
import static android.provider.Settings.System.POP_UP_NOTIFICATION_BLACKLIST;
import static android.provider.Settings.System.POP_UP_SINGLE_TAP_ACTION;
import static org.rising.view.PopUpViewManager.TAP_ACTION_EXIT;
import static org.rising.view.PopUpViewManager.TAP_ACTION_NOTHING;
import static org.rising.view.PopUpViewManager.TAP_ACTION_PIN_WINDOW;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.util.android.PopUpSettingsHelper;

import org.rising.view.PopUpViewManager;

class PopUpSettingsConfig {

    private static final String TAG = "PopUpSettingsConfig";

    private static class InstanceHolder {
        private static final PopUpSettingsConfig INSTANCE = new PopUpSettingsConfig();
    }

    static PopUpSettingsConfig getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private final ArraySet<String> mUserNotificationBlacklist = new ArraySet<>();

    private Context mContext;
    private Handler mHandler;
    private SettingsObserver mObserver;

    private boolean mKeepMuteInMini = true;
    private int mSingleTapAction = TAP_ACTION_PIN_WINDOW;
    private int mDoubleTapAction = TAP_ACTION_EXIT;

    void init(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
        mObserver = new SettingsObserver(handler);
        mObserver.observe();
        updateAll();
    }

    private void updatePopUpKeepMuteInMini() {
        if (mContext == null) {
            Log.w(TAG, "Context is null, cannot update keep mute in mini setting");
            return;
        }
        mKeepMuteInMini = PopUpSettingsHelper.isKeepMuteInMiniEnabled(mContext);
    }

    boolean shouldMuteInMiniWindow() {
        return mKeepMuteInMini;
    }

    private void updatePopUpSingleTapAction() {
        if (mContext == null) {
            Log.w(TAG, "Context is null, cannot update single tap action");
            return;
        }
        mSingleTapAction = PopUpSettingsHelper.getSingleTapAction(mContext);
        DimmerWindow.getInstance().setSingleTapOnly(
                mDoubleTapAction == TAP_ACTION_NOTHING || mSingleTapAction == mDoubleTapAction);
    }

    int getSingleTapAction() {
        return mSingleTapAction;
    }

    private void updatePopUpDoubleTapAction() {
        if (mContext == null) {
            Log.w(TAG, "Context is null, cannot update double tap action");
            return;
        }
        mDoubleTapAction = PopUpSettingsHelper.getDoubleTapAction(mContext);
        DimmerWindow.getInstance().setSingleTapOnly(
                mDoubleTapAction == TAP_ACTION_NOTHING || mSingleTapAction == mDoubleTapAction);
    }

    int getDoubleTapAction() {
        return mDoubleTapAction;
    }

    private void updateNotificationBlacklist() {
        mUserNotificationBlacklist.clear();
        if (mContext == null) {
            Log.w(TAG, "Context is null, cannot update notification blacklist");
            return;
        }
        final String blacklist = PopUpSettingsHelper.getNotificationJumpBlacklist(mContext);
        if (TextUtils.isEmpty(blacklist)) {
            return;
        }
        final String[] apps = blacklist.split(";");
        for (String app : apps) {
            mUserNotificationBlacklist.add(app);
        }
    }

    boolean inNotificationBlacklist(String packageName) {
        return PopUpViewManager.inSystemNotificationBlacklist(packageName) ||
                mUserNotificationBlacklist.contains(packageName);
    }

    void updateAll() {
        if (mHandler != null) {
            mHandler.post(() -> {
                updatePopUpKeepMuteInMini();
                updatePopUpSingleTapAction();
                updatePopUpDoubleTapAction();
                updateNotificationBlacklist();
            });
        } else {
            Log.w(TAG, "Handler is null, updating settings synchronously");
            updatePopUpKeepMuteInMini();
            updatePopUpSingleTapAction();
            updatePopUpDoubleTapAction();
            updateNotificationBlacklist();
        }
    }

    private final class SettingsObserver extends ContentObserver {

        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            if (mContext == null) {
                Log.e(TAG, "Context is null, cannot register content observer");
                return;
            }
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(POP_UP_KEEP_MUTE_IN_MINI),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(POP_UP_SINGLE_TAP_ACTION),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(POP_UP_DOUBLE_TAP_ACTION),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(POP_UP_NOTIFICATION_BLACKLIST),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            switch (uri.getLastPathSegment()) {
                case POP_UP_KEEP_MUTE_IN_MINI:
                    updatePopUpKeepMuteInMini();
                    break;
                case POP_UP_SINGLE_TAP_ACTION:
                    updatePopUpSingleTapAction();
                    break;
                case POP_UP_DOUBLE_TAP_ACTION:
                    updatePopUpDoubleTapAction();
                    break;
                case POP_UP_NOTIFICATION_BLACKLIST:
                    updateNotificationBlacklist();
                    break;
            }
        }
    }
}
