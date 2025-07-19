/*
 * Copyright (C) 2022-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server;

import static android.os.Process.THREAD_PRIORITY_DEFAULT;

import android.content.Context;
import android.os.Handler;
import android.os.IBinder;

import com.android.server.ServiceThread;
import com.android.server.SystemService;
import com.android.server.wm.DisplayResolutionController;

public class RisingSystemExService extends SystemService {

    private static final String TAG = "RisingSystemExService";

    private Handler mHandler;
    private ServiceThread mWorker;

    public RisingSystemExService(Context context) {
        super(context);
    }

    @Override
    public void onBootPhase(int phase) {
    }

    @Override
    public void onStart() {
        mWorker = new ServiceThread(TAG, THREAD_PRIORITY_DEFAULT, false);
        mWorker.start();
        mHandler = new Handler(mWorker.getLooper());

        DisplayResolutionController.getInstance().initSystemExService(this);
    }

    @Override
    public void onUserSwitching(TargetUser from, TargetUser to) {
    }

    /**
     * Public wrapper method to expose publishBinderService to controllers
     * This is needed because publishBinderService is protected and can only be called
     * from within the SystemService subclass.
     *
     * @param name the service name
     * @param service the binder service
     */
    public void publishService(String name, IBinder service) {
        publishBinderService(name, service);
    }
}
