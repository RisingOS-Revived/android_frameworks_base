/* Copyright (c) 2021 The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.server.am.trace;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;

import static android.text.format.DateUtils.DAY_IN_MILLIS;

import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Slog;

public class SmartTraceUtils {
    // true to enable smart trace function
    public static final String PROP_ENABLE_SMART_TRACE= "persist.sys.smtrace.enable";
    public static final String PROP_ENABLE_ON_BG_APP = "persist.sys.smtrace.bgapp.enable";
    public static final String PROP_ENABLE_PERFETTO_DUMP = "persist.sys.perfetto_dump.enable";
    public static final String PROP_ENABLE_PERFETTO_ON_BG_APP = "persist.sys.perfetto.bgapp.enable";
    //whether need to dump process defined in NATIVE_STACKS_OF_INTEREST Watchdog.java
    //default value false
    public static final String PROP_ENABLE_DUMP_PREDEFINED_PIDS =
                          "persist.sys.smtrace.dump.predefined_pids.enable";
    //defined extra cmdline to dump trace
    public static final String PROP_DUMP_CMDLINES= "persist.sys.smtrace.dump.cmdlines.extra";
    //whether need recursive find the blocked target process
    public static final String PROP_ENABLE_RECURSIVE_MODE =
                          "persist.sys.smtrace.recursivemode.enable";

    public static final int DUMP_MAX_COUNT = 10;
    private static final String PROP_PERFETTO_COMMAND = "sys.perfetto.cmd";
    //should not set by user
    private static final String PROP_DUMP_CMD = "sys.smtrace.cmd";
    //perfetto max file count
    private static final String PROP_PERFETTO_MAX_TRACE_COUNT=
                                   "persist.sys.perfetto.max_trace_count";
    private static final String TRACE_DIRECTORY = "/data/misc/perfetto-traces/";
    private static final String TAG = "SmartTraceUtils";

    public static boolean isSmartTraceEnabled() {
        return SystemProperties.getBoolean(PROP_ENABLE_SMART_TRACE, false);
    }

    public static boolean isSmartTraceEnabledOnBgApp() {
        return SystemProperties.getBoolean(PROP_ENABLE_ON_BG_APP, true);
    }

    public static boolean isDumpPredefinedPidsEnabled() {
        return SystemProperties.getBoolean(PROP_ENABLE_DUMP_PREDEFINED_PIDS, false);
    }

    public static boolean isPerfettoDumpEnabled() {
        return SystemProperties.getBoolean(PROP_ENABLE_PERFETTO_DUMP, false);
    }

    public static boolean isPerfettoDumpEnabledOnBgApp() {
        return SystemProperties.getBoolean(PROP_ENABLE_PERFETTO_ON_BG_APP, true);
    }

    public static boolean isRecursiveModeEnabled() {
        return SystemProperties.getBoolean(PROP_ENABLE_RECURSIVE_MODE, true);
    }

    //process ids store in "sys.smtrace.cmd" property shows IPC communicate with
    //system process, it requires /system/bin/binder_trace_dump.sh tool to dump their trace
    //and reset property value to "" after work finished.
    public static void dumpStackTraces(int pid, ArrayList<Integer> firstPids,
                ArrayList<Integer> nativePids, File outputFile) {
        if(!isSmartTraceEnabled()) {
            return;
        }
        if (isDumpingOn()){
            Slog.e(TAG,
               "Attempting to run smart trace dump but trace is already in progress, skip it");
            return;
        }
        Set<Integer> pidSet = getTargetPidsStuckInBinder(pid, firstPids, nativePids, outputFile);
        if(pidSet != null && pidSet.size() != 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(pid);
            pidSet.forEach(p->{sb.append(","); sb.append(p);});
            sb.append(":"+outputFile.getPath());
            SystemProperties.set(PROP_DUMP_CMD,sb.toString());
            Slog.i(TAG, "Start collect stack trace for "+sb.toString());
        }
    }

    public static boolean traceStart() {
        if (isTracingOn()){
            Slog.e(TAG,
               "Attempting to start perfetto trace but trace is already in progress, skip it");
            return false;
        }
        Slog.i(TAG,"Perfetto trace start..");
        SystemProperties.set(PROP_PERFETTO_COMMAND,"START");
        return true;
    }

    private static boolean isDumpingOn() {
        return !TextUtils.isEmpty(SystemProperties.get(PROP_DUMP_CMD,""));
    }

    private static boolean isTracingOn() {
        return !TextUtils.isEmpty(SystemProperties.get(PROP_PERFETTO_COMMAND,""));
    }

    private static Set<Integer> getTargetPidsStuckInBinder(int pid, ArrayList<Integer> firstPids,
                      ArrayList<Integer> nativePids, File outputFile) {
        BinderTransactions transactions = new BinderTransactions(isRecursiveModeEnabled());
        transactions.binderStateRead(outputFile);
        Set<Integer> pidSet = transactions.getTargetPidsStuckInBinder(pid);
        //remove duplicate process
        pidSet.removeAll(firstPids);
        if(nativePids != null){
            pidSet.removeAll(nativePids);
        }

        int[] extraPids = readExtraCmdlinesFromProperty();
        if(extraPids != null) {
            for(int p: extraPids){
                pidSet.add(p);
            }
        }
        return pidSet;
    }

    private static int[] readExtraCmdlinesFromProperty() {
        String cmdlines =  SystemProperties.get(PROP_DUMP_CMDLINES, "");
        if (TextUtils.isEmpty(cmdlines)) {
            return null;
        }

        try {
            return android.os.Process.getPidsForCommands(cmdlines.split(","));
        }catch(NullPointerException e) {
            Slog.e(TAG, "Exception get pid for commonds " + cmdlines, e);
        }catch(OutOfMemoryError e) {
            Slog.e(TAG, "Out of Memory when get pid for commonds " + cmdlines, e);
        }
        return null;
    }
}
