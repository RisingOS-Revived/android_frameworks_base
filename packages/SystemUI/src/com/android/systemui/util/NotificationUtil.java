/*
 * Copyright (C) 2023 the risingOS Android Project
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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Collections;
import java.lang.reflect.Method;

import com.android.systemui.res.R;

public class NotificationUtil {

    private static final String CHANNEL_ID = "np_playback_service";
    private static final int NOTIFICATION_ID = 7383646;
    private static final long NOTIFICATION_CANCEL_DELAY = 5000; // 5 seconds

    private Context context;
    private NotificationManager notificationManager;

    private Handler handler = new Handler(Looper.getMainLooper());

    public NotificationUtil(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Now Playing",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setBlockable(true);
        channel.setSound(null, null);
        channel.setVibrationPattern(new long[]{0});
        notificationManager.createNotificationChannel(channel);
    }

    private String getActiveVolumeApp() {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        List appVolumes = getAppVolumes(audioManager);
        for (Object av : appVolumes) {
            try {
                Method isActiveMethod = av.getClass().getMethod("isActive");
                Boolean isActive = (Boolean) isActiveMethod.invoke(av);
                if (isActive) {
                    Field packageNameField = av.getClass().getField("packageName");
                    return (String) packageNameField.get(av);
                }
            } catch (Exception e) {
                // Log removed as per request
            }
        }
        return "";
    }

    private List getAppVolumes(AudioManager audioManager) {
        try {
            Method method = AudioManager.class.getDeclaredMethod("listAppVolumes");
            method.setAccessible(true);
            return (List) method.invoke(audioManager);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Runnable cancelNotificationTask = new Runnable() {
        @Override
        public void run() {
            cancelNowPlayingNotification();
        }
    };

    public void showNowPlayingNotification(MediaMetadata metadata) {
        Bitmap albumArtwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
	Bitmap mediaArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        Bitmap albumArt = (albumArtwork != null) ? albumArtwork : mediaArt;
        String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (title == null || artist == null) return;
        String packageName = getActiveVolumeApp();
        if (packageName.equals("")) return;
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent == null) return;
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PackageManager packageManager = context.getPackageManager();
        String appLabel;
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            appLabel = (String) packageManager.getApplicationLabel(appInfo);
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }
        String nowPlayingTitle = context.getString(R.string.now_playing_on, appLabel);
        String contentText = context.getString(R.string.by_artist, title, artist);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note)
                .setLargeIcon(albumArt)
                .setContentTitle(nowPlayingTitle)
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .build();

        notificationManager.notify(NOTIFICATION_ID, notification);

        // Schedule the task to cancel the notification after 5 seconds
        handler.postDelayed(cancelNotificationTask, NOTIFICATION_CANCEL_DELAY);
    }

    public void cancelNowPlayingNotification() {
        notificationManager.cancel(NOTIFICATION_ID);

        // Remove any pending callbacks (in case the notification is cancelled manually)
        handler.removeCallbacks(cancelNotificationTask);
    }
}
