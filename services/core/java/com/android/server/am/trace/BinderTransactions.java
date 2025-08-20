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

import android.os.Process;
import android.util.Slog;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Locale;

public class BinderTransactions {
    private static final String TAG = "BinderTransactions";
    private final static String REGEX_PATTERN =
        "\\s+(outgoing|incoming|pending)\\s+transaction.*from"
        + "\\s+(\\d+):\\d+\\s+to\\s+(\\d+):\\d+\\s+.*";
    private final static String BINDER_TRANSATION_FILE = "/dev/binderfs/binder_logs/state";
    private static final int DUMP_MAX_COUNT = 10;
    // binder ref mapping to binder service
    private Map<Integer, HashSet<Integer>> mLocalToRemotesMap =
        new HashMap<Integer, HashSet<Integer>>();
    private int mCheckPid;
    //whether need recursive find the blocked target process
    private boolean mRecursiveMode;
    private Set<Integer> mRemotePids = new HashSet();
    /**
       * @param recursive recursive mode
    */
    public BinderTransactions(boolean recursive) {
        mRecursiveMode = recursive;
    }

    /**
     * find all the processes who is binder transaction to {pid}
     * @param pid  anr happen on PID
     * @return
     */
    public Set<Integer> getTargetPidsStuckInBinder(int pid){
        mCheckPid = pid;
        parseFromFile();
        if(!mRecursiveMode) {
            if(mLocalToRemotesMap.containsKey(pid)){
                for(int remotePid: mLocalToRemotesMap.get(pid)) {
                    mRemotePids.add(remotePid);
                }
            }
        }else {
            Set<Integer> keyPids = mLocalToRemotesMap.keySet();
            for(Integer p: keyPids) {
                if(p == mCheckPid) {
                    mRemotePids.add(p);
                    findRemotePid(p);
                }
            }
       }

       Set<Integer> ret = new HashSet<Integer>();
       mRemotePids.forEach(
           p->{if(p!= 0 && ret.size() <= SmartTraceUtils.DUMP_MAX_COUNT) ret.add(p);});
       return ret;
    }

    public void binderStateRead(File outputFile) {
       try {
            boolean binderfsNodePresent = false;
            BufferedReader in = null;
            Slog.i(TAG,"Collecting Binder Transaction Status Information");
            try {
                in = new BufferedReader(new FileReader(BINDER_TRANSATION_FILE));
                Slog.i(TAG, "Collecting Binder state file from binderfs");
                binderfsNodePresent = true;
            } catch(IOException e) {
                Slog.i(TAG, "Binderfs node not found, Trying to collect it from debugfs", e);
            }
            try {
                if (binderfsNodePresent == false) {
                    in = new BufferedReader(new FileReader("/sys/kernel/debug/binder/state"));
                    Slog.i(TAG, "Collecting Binder state file from debugfs");
                }
            } catch(IOException e) {
                Slog.i(TAG, "Debugfs node not found", e);
            }
            if (in == null) {
                return;
            }
            String format = "yyyy-MM-dd-HH-mm-ss";
            String now = new SimpleDateFormat(format, Locale.US).format(new Date());
            FileWriter out = new FileWriter(outputFile, true);
            String line = null;

            // Write line-by-line
            while ((line = in.readLine()) != null) {
                out.write(line);
                out.write('\n');
            }
            in.close();
            out.close();
        } catch (IOException e) {
            Slog.w(TAG, "Failed to collect state file", e);
        }
    }

    private void findRemotePid(int s) {
        if(mLocalToRemotesMap.containsKey(s)) {
            for(Integer p: mLocalToRemotesMap.get(s)) {
                if(!mRemotePids.contains(p)) {
                    mRemotePids.add(p);
                    //continue to search
                    findRemotePid(p);
                }
            }
        }
    }

   private void parseFromFile() {
        try {
            mLocalToRemotesMap.clear();
            mRemotePids.clear();
            BufferedReader in =
            new BufferedReader(new FileReader(BINDER_TRANSATION_FILE));
            Pattern outP=Pattern.compile(REGEX_PATTERN);
            Matcher m;
            String line = null;
            while ((line = in.readLine()) != null) {
                // match outgoing transaction
                m = outP.matcher(line);
                if(m.find()) {
                    addItem(Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)),
                        m.group(1).equals("outgoing"));
                }
            }
            in.close();
        } catch (FileNotFoundException e) {
            Slog.w(TAG, "Unexpected FileNotFoundException" , e);
        } catch (IOException e) {
            Slog.w(TAG, "Unexpected IOException" , e);
        } catch (NumberFormatException e) {
            Slog.w(TAG,"Unexpected NumberFormatException ", e);
        }
    }

    /**
      * @spid source pid
      * @stid source tid
      * @tpid target pid
      * @ttid target tid
    */
    private void addItem(int spid, int tpid, boolean outgoing) {
        if(outgoing) {
            if(mLocalToRemotesMap.containsKey(spid)) {
                mLocalToRemotesMap.get(spid).add(tpid);
            }else {
                HashSet<Integer> set = new HashSet<Integer>();
                set.add(tpid);
                mLocalToRemotesMap.put(spid, set);
            }
        }else {
            if(tpid == mCheckPid) {
                mRemotePids.add(spid);
            }
        }
    }
}
