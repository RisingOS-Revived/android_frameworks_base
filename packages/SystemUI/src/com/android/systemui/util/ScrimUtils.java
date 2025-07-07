/*
 * Copyright (C) 2023-2024 The risingOS Android Project
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
package com.android.systemui.util;

import static com.android.systemui.statusbar.StatusBarState.KEYGUARD;

import android.content.Context;
import android.os.Handler;

import com.android.keyguard.NowBarController;
import com.android.systemui.Dependency;
import com.android.systemui.notifications.ui.PeekDisplayViewController;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.phone.ScrimController;
import com.android.systemui.statusbar.policy.KeyguardStateController;

import java.util.function.Consumer;

public class ScrimUtils {

    public interface ScrimEventListener {
        default void onKeyguardShowingChanged(boolean showing) {}
        default void onKeyguardFadingAwayChanged(boolean fadingAway) {}
        default void onKeyguardGoingAwayChanged(boolean goingAway) {}
        default void onPrimaryBouncerShowingChanged(boolean showing) {}
        default void onDozingChanged() {}
        default void onExpandedFractionChanged(float expandedFraction) {}
        default void onBarStateChanged(int state) {}
        default void onQsVisibilityChanged(boolean visible) {}
    }

    public enum ExpansionState {
        QS_NOT_EXPANDED,
        QS_FULLY_EXPANDED
    }

    private static ScrimUtils instance;
    private final Context mContext;
    private final Handler mHandler;
    private final ScrimController mScrimController;
    private final StatusBarStateController mStatusBarStateController;
    private final WallpaperDepthUtils mWallpaperDepthUtils;
    private final MediaArtUtils mMediaArtUtils;
    private final PeekDisplayViewController mPeekDisplayViewController;
    private final NowBarController mNowBarController;

    private KeyguardStateController mKeyguardStateController;

    private ExpansionState mExpansionState = ExpansionState.QS_NOT_EXPANDED;

    private final WeakListenerManager<ScrimEventListener> listeners = new WeakListenerManager<>();

    private final KeyguardStateController.Callback mKeyguardStateCallback =
            new KeyguardStateController.Callback() {
                @Override
                public void onKeyguardFadingAwayChanged() {
                    onKgFadingAwayChanged();
                    notifyListeners(listener -> listener.onKeyguardFadingAwayChanged(
                        mKeyguardStateController.isKeyguardFadingAway()));
                }

                @Override
                public void onKeyguardGoingAwayChanged() {
                    onKgGoingAwayChanged();
                    notifyListeners(listener -> listener.onKeyguardGoingAwayChanged(
                        mKeyguardStateController.isKeyguardGoingAway()));
                }
            };

    private final StatusBarStateController.StateListener mStatusBarStateListener =
            new StatusBarStateController.StateListener() {
                @Override
                public void onStateChanged(int newState) {
                    notifyListeners(listener -> listener.onBarStateChanged(newState));
                    notifyListeners(listener -> listener.onKeyguardShowingChanged(newState == KEYGUARD));
                }
                @Override
                public void onDozingChanged(boolean dozing) {
                    onDozeChanged(dozing);
                    notifyListeners(ScrimEventListener::onDozingChanged);
                }
            };

    private ScrimUtils(Context context) {
        mContext = context.getApplicationContext();
        mHandler = new Handler();
        mScrimController = Dependency.get(ScrimController.class);
        mStatusBarStateController = Dependency.get(StatusBarStateController.class);
        mWallpaperDepthUtils = WallpaperDepthUtils.getInstance(mContext);
        mMediaArtUtils = MediaArtUtils.getInstance(mContext);
        mPeekDisplayViewController = PeekDisplayViewController.Companion.getInstance();
        mNowBarController = NowBarController.getInstance(mContext);
        mStatusBarStateController.addCallback(mStatusBarStateListener);
        mStatusBarStateListener.onDozingChanged(mStatusBarStateController.isDozing());

        try {
            mKeyguardStateController = Dependency.get(KeyguardStateController.class);
            if (mKeyguardStateController != null) {
                mKeyguardStateController.addCallback(mKeyguardStateCallback);
            }
        } catch (IllegalArgumentException e) {
            mKeyguardStateController = null;
        }
    }

    public static ScrimUtils getInstance(Context context) {
        if (instance == null) {
            instance = new ScrimUtils(context);
        }
        return instance;
    }

    public static ScrimUtils get() {
        return instance;
    }

    public void addListener(ScrimEventListener listener) {
        listeners.addListener(listener);
    }

    public void removeListener(ScrimEventListener listener) {
        listeners.removeListener(listener);
    }

    private void notifyListeners(Consumer<ScrimEventListener> callback) {
        listeners.notifyConsumer(callback);
    }

    public void setViewAlpha(float subjectAlpha) {
        mWallpaperDepthUtils.setSubjectAlpha(subjectAlpha);
        mMediaArtUtils.setSubjectAlpha(subjectAlpha);
        mPeekDisplayViewController.setAlpha(subjectAlpha);
        mNowBarController.setAlpha(subjectAlpha);
    }

    public void setQsExpansion(float expansion) {
        ExpansionState state = expansion < 1
                ? ExpansionState.QS_NOT_EXPANDED
                : ExpansionState.QS_FULLY_EXPANDED;
        if (state == mExpansionState) {
            return;
        }
        mExpansionState = state;

        notifyListeners(listener -> listener.onQsVisibilityChanged(state == ExpansionState.QS_FULLY_EXPANDED));

        if (mExpansionState == ExpansionState.QS_NOT_EXPANDED) {
            mWallpaperDepthUtils.updateDepthWallpaper();
            mMediaArtUtils.updateMediaArtVisibility();
            mPeekDisplayViewController.showPeekDisplayView();
            mNowBarController.show();
        } else if (mExpansionState == ExpansionState.QS_FULLY_EXPANDED) {
            mMediaArtUtils.hideMediaArt();
            mWallpaperDepthUtils.hideDepthWallpaper();
            mPeekDisplayViewController.hidePeekDisplayView();
            mNowBarController.hide();
        }
    }

    public void onScreenStateChange() {
        updateNotifContainerElements();
        mHandler.postDelayed(() -> {
            updateNotifContainerElements();
        }, 250);
        mPeekDisplayViewController.resetShelves();
    }

    private void updateNotifContainerElements() {
        mMediaArtUtils.updateMediaArtVisibility();
        mWallpaperDepthUtils.updateDepthWallpaperVisibility();
    }

    public void onScrimDispatched() {
        mMediaArtUtils.updateMediaArtVisibility();
        mWallpaperDepthUtils.updateDepthWallpaper();
    }

    private void onDozeChanged(boolean dozing) {
        mMediaArtUtils.onDozingChanged(dozing);
        mWallpaperDepthUtils.onDozingChanged(dozing);
    }

    private void onKgFadingAwayChanged() {
        mMediaArtUtils.hideMediaArt();
        mWallpaperDepthUtils.hideDepthWallpaper();
    }

    private void onKgGoingAwayChanged() {
        mMediaArtUtils.hideMediaArt();
        mWallpaperDepthUtils.hideDepthWallpaper();
    }

    public float getScrimBehindAlphaKeyguard() {
        return mScrimController.getScrimBehindAlpha();
    }

    private boolean isKeyguardStateControllerAvailable() {
        return mKeyguardStateController != null;
    }

    public boolean isKeyguardShowing() {
        return mStatusBarStateController.getState() == KEYGUARD;
    }
}
