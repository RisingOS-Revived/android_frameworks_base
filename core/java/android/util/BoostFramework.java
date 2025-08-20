/*
 * Copyright (c) 2017-2018, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *    * Neither the name of The Linux Foundation nor the names of its
 *      contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
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

package android.util;

import android.app.ActivityThread;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.BLASTBufferQueue;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;

import dalvik.system.PathClassLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/** @hide */
public class BoostFramework {

    private static final String TAG = "BoostFramework";
    private static final String PERFORMANCE_JAR = "/system/framework/QPerformance.jar";
    private static final String PERFORMANCE_CLASS = "com.qualcomm.qti.Performance";

    private static final String UXPERFORMANCE_JAR = "/system/framework/UxPerformance.jar";
    private static final String UXPERFORMANCE_CLASS = "com.qualcomm.qti.UxPerformance";
    public  static final float PERF_HAL_V22 = 2.2f;
    public  static final float PERF_HAL_V23 = 2.3f;
    public static final int VENDOR_T_API_LEVEL = 33;
    public static final int VENDOR_V_API_LEVEL = 202404;
    public final int board_first_api_lvl = SystemProperties.getInt("ro.board.first_api_level", 0);
    public final int board_api_lvl = SystemProperties.getInt("ro.board.api_level", 0);
    //key in privider settings global
    public static final String KEY_LEGACY_UI_PERF_PKGS = "LEGACY_UI_PERF_PROCS";
    public static final String KEY_GPU_PREFER = "UI_PERF_GPU_PREFER";
    public static final String KEY_CPU_PREFER = "UI_PERF_CPU_PREFER";
    public static final String KEY_CPU_AGGRESSIVE = "UI_PERF_CPU_AGGRESSIVE";
    public static final String KEY_CPU_GPU = "UI_PERF_CPU_GPU";
    public static final String KEY_PKGS = "UI_PERF_PKGS";
    public static final String KEY_IGNORE_PKGS = "UI_PERF_IGNORE_PKGS";
    //ui perf mode controller in android space
    public static final String UI_PERF_PROP = "debug.ui.perfmode.enable";
    public static final String UI_LEGACY_PERF_PROP = "sys.ui.legacy_perfmode.enable";
    //Deprecated, will be removed soon.
    public static final String UI_PERF_PROC_PROP = "debug.ui.perfmode.process";

/** @hide */
    private static boolean sIsLoaded = false;
    private static Class<?> sPerfClass = null;
    private static Method sAcquireFunc = null;
    private static Method sPerfHintFunc = null;
    private static Method sReleaseFunc = null;
    private static Method sPerfHintRelFunc = null;
    private static Method sReleaseHandlerFunc = null;
    private static Method sFeedbackFunc = null;
    private static Method sFeedbackFuncExtn = null;
    private static Method sPerfGetPropFunc = null;
    private static Method sAcqAndReleaseFunc = null;
    private static Method sperfHintAcqRelFunc = null;
    private static Method sperfHintRenewFunc = null;
    private static Method sPerfEventFunc = null;
    private static Method sPerfGetPerfHalVerFunc = null;
    private static Method sPerfSyncRequest = null;

    private static Method sIOPStart = null;
    private static Method sIOPStop  = null;
    private static Method sUXEngineEvents  = null;
    private static Method sUXEngineTrigger  = null;

    private static boolean sUxIsLoaded = false;
    private static Class<?> sUxPerfClass = null;
    private static Method sUxIOPStart = null;

/** @hide */
    private Object mPerf = null;
    private Object mUxPerf = null;

    //perf hints
    public static final int VENDOR_HINT_SCROLL_BOOST = 0x00001080;
    public static final int VENDOR_HINT_FIRST_LAUNCH_BOOST = 0x00001081;
    public static final int VENDOR_HINT_SUBSEQ_LAUNCH_BOOST = 0x00001082;
    public static final int VENDOR_HINT_ANIM_BOOST = 0x00001083;
    public static final int VENDOR_HINT_ACTIVITY_BOOST = 0x00001084;
    public static final int VENDOR_HINT_TOUCH_BOOST = 0x00001085;
    public static final int VENDOR_HINT_MTP_BOOST = 0x00001086;
    public static final int VENDOR_HINT_DRAG_BOOST = 0x00001087;
    public static final int VENDOR_HINT_PACKAGE_INSTALL_BOOST = 0x00001088;
    public static final int VENDOR_HINT_ROTATION_LATENCY_BOOST = 0x00001089;
    public static final int VENDOR_HINT_ROTATION_ANIM_BOOST = 0x00001090;
    public static final int VENDOR_HINT_PERFORMANCE_MODE = 0x00001091;
    public static final int VENDOR_HINT_APP_UPDATE = 0x00001092;
    public static final int VENDOR_HINT_KILL = 0x00001093;
    public static final int VENDOR_HINT_BOOST_RENDERTHREAD = 0x00001096;
    public static final int VENDOR_HINT_PASS_PID = 0x0000109C;
    public static final int VENDOR_HINT_SCENARIO_GPU = 0x000010AA;
    public static final int VENDOR_HINT_SCENARIO_CPU = 0x000010AB;
    public static final int VENDOR_HINT_SCENARIO_CPU_GPU = 0x000010AC;
    public static final int VENDOR_HINT_SCENARIO_CPU_AGGRESSIVE = 0x000010AD;
    //perf events
    public static final int VENDOR_HINT_FIRST_DRAW = 0x00001042;
    public static final int VENDOR_HINT_TAP_EVENT = 0x00001043;
    public static final int VENDOR_HINT_DRAG_START = 0x00001051;
    public static final int VENDOR_HINT_DRAG_END = 0x00001052;
    //Ime Launch Boost Hint
    public static final int VENDOR_HINT_IME_LAUNCH_EVENT = 0x0000109F;
    //App exit animation boost
    public static final int VENDOR_HINT_EXIT_ANIM_BOOST = 0x000010A9;

    //feedback hints
    public static final int VENDOR_FEEDBACK_WORKLOAD_TYPE = 0x00001601;
    public static final int VENDOR_FEEDBACK_LAUNCH_END_POINT = 0x00001602;
    public static final int VENDOR_FEEDBACK_PA_FW = 0x00001604;

    //UXE Events and Triggers
    public static final int UXE_TRIGGER = 1;
    public static final int UXE_EVENT_BINDAPP = 2;
    public static final int UXE_EVENT_DISPLAYED_ACT = 3;
    public static final int UXE_EVENT_KILL = 4;
    public static final int UXE_EVENT_GAME  = 5;
    public static final int UXE_EVENT_SUB_LAUNCH = 6;
    public static final int UXE_EVENT_PKG_UNINSTALL = 7;
    public static final int UXE_EVENT_PKG_INSTALL = 8;

    //New Hints while porting IOP to Perf Hal.
    public static final int VENDOR_HINT_BINDAPP = 0x000010A0;
    public static final int VENDOR_HINT_WARM_LAUNCH = 0x000010A1; //SUB_LAUNCH
    // 0x000010A2 is added in UXPerformance.java for SPEED Hints
    public static final int VENDOR_HINT_PKG_INSTALL = 0x000010A3;
    public static final int VENDOR_HINT_PKG_UNINSTALL = 0x000010A4;

    //perf opcodes
    public static final int MPCTLV3_GPU_IS_APP_FG = 0X42820000;
    public static final int MPCTLV3_GPU_IS_APP_BG = 0X42824000;

    public class Scroll {
        public static final int VERTICAL = 1;
        public static final int HORIZONTAL = 2;
        public static final int PANEL_VIEW = 3;
        public static final int PREFILING = 4;
    };

    public class Launch {
        public static final int BOOST_V1 = 1;
        public static final int BOOST_V2 = 2;
        public static final int BOOST_V3 = 3;
        public static final int BOOST_GAME = 4;
        public static final int RESERVED_1 = 5;
        public static final int RESERVED_2 = 6;
        public static final int RESERVED_3 = 7;
        public static final int RESERVED_4 = 8;
        public static final int RESERVED_5 = 9;
        public static final int ACTIVITY_LAUNCH_BOOST = 10;
        public static final int TYPE_SERVICE_START = 100;
        public static final int TYPE_START_PROC = 101;
        public static final int TYPE_START_APP_FROM_BG = 102;
        public static final int TYPE_ATTACH_APPLICATION = 103;
    };

    public class PassPid {
        public static final int APP_PID = 4;
        public static final int RENDER_TID = 5;
    }

    public class Draw {
        public static final int EVENT_TYPE_V1 = 1;
    };

    public class WorkloadType {
        public static final int NOT_KNOWN = 0;
        public static final int APP = 1;
        public static final int GAME = 2;
        public static final int BROWSER = 3;
        public static final int PREPROAPP = 4;
        public static final int VIDEO = 5;
        public static final int APP_OF_INTEREST = 6;
    };

/** @hide */
    public BoostFramework() {
        initFunctions();

        try {
            if (sPerfClass != null) {
                mPerf = sPerfClass.newInstance();
            }
            if (sUxPerfClass != null) {
                mUxPerf = sUxPerfClass.newInstance();
            }
        }
        catch(Exception e) {
            Log.e(TAG,"BoostFramework() : Exception_2 = " + e);
        }
    }

/** @hide */
    public BoostFramework(Context context) {
        this(context, false);
    }

/** @hide */
    public BoostFramework(Context context, boolean isTrusted) {
        initFunctions();

        try {
            if (sPerfClass != null) {
                Constructor cons = sPerfClass.getConstructor(Context.class);
                if (cons != null)
                    mPerf = cons.newInstance(context);
            }
            if (sUxPerfClass != null) {
                if (isTrusted) {
                    Constructor cons = sUxPerfClass.getConstructor(Context.class);
                    if (cons != null)
                        mUxPerf = cons.newInstance(context);
                } else {
                    mUxPerf = sUxPerfClass.newInstance();
                }
            }
        }
        catch(Exception e) {
            Log.e(TAG,"BoostFramework() : Exception_3 = " + e);
        }
    }

/** @hide */
    public BoostFramework(boolean isUntrustedDomain) {
        initFunctions();

        try {
            if (sPerfClass != null) {
                Constructor cons = sPerfClass.getConstructor(boolean.class);
                if (cons != null)
                    mPerf = cons.newInstance(isUntrustedDomain);
            }
            if (sUxPerfClass != null) {
                mUxPerf = sUxPerfClass.newInstance();
            }
        }
        catch(Exception e) {
            Log.e(TAG,"BoostFramework() : Exception_5 = " + e);
        }
    }

    private void initFunctions () {
        synchronized(BoostFramework.class) {
            if (sIsLoaded == false) {
                try {
                    sPerfClass = Class.forName(PERFORMANCE_CLASS);

                    Class[] argClasses = new Class[] {int.class, int[].class};
                    sAcquireFunc = sPerfClass.getMethod("perfLockAcquire", argClasses);

                    argClasses = new Class[] {int.class, String.class, int.class, int.class};
                    sPerfHintFunc = sPerfClass.getMethod("perfHint", argClasses);

                    argClasses = new Class[] {};
                    sReleaseFunc = sPerfClass.getMethod("perfLockRelease", argClasses);

                    argClasses = new Class[] {};
                    sPerfHintRelFunc = sPerfClass.getMethod("perfHintRelease", argClasses);

                    argClasses = new Class[] {int.class};
                    sReleaseHandlerFunc = sPerfClass.getDeclaredMethod("perfLockReleaseHandler", argClasses);

                    argClasses = new Class[] {int.class, String.class};
                    sFeedbackFunc = sPerfClass.getMethod("perfGetFeedback", argClasses);

                    argClasses = new Class[] {int.class, String.class, int.class, int[].class};
                    sFeedbackFuncExtn = sPerfClass.getMethod("perfGetFeedbackExtn", argClasses);

                    argClasses = new Class[] {int.class, String.class, String.class};
                    sIOPStart =   sPerfClass.getDeclaredMethod("perfIOPrefetchStart", argClasses);

                    argClasses = new Class[] {};
                    sIOPStop =  sPerfClass.getDeclaredMethod("perfIOPrefetchStop", argClasses);

                    argClasses = new Class[] {String.class, String.class};
                    sPerfGetPropFunc = sPerfClass.getMethod("perfGetProp", argClasses);

                    argClasses = new Class[] {int.class, int.class, int.class, int.class, int[].class};
                    sAcqAndReleaseFunc = sPerfClass.getMethod("perfLockAcqAndRelease", argClasses);

                    argClasses = new Class[] {int.class, String.class, int.class, int[].class};
                    sPerfEventFunc = sPerfClass.getMethod("perfEvent", argClasses);

                    argClasses = new Class[] {int.class};
                    sPerfSyncRequest = sPerfClass.getMethod("perfSyncRequest", argClasses);

                    argClasses = new Class[] {int.class, int.class, String.class, int.class,
                                              int.class, int.class, int[].class};
                    sperfHintAcqRelFunc = sPerfClass.getMethod("perfHintAcqRel", argClasses);

                    argClasses = new Class[] {int.class, int.class, String.class, int.class,
                                              int.class, int.class, int[].class};
                    sperfHintRenewFunc = sPerfClass.getMethod("perfHintRenew", argClasses);

                    try {
                        argClasses = new Class[] {};
                        sPerfGetPerfHalVerFunc = sPerfClass.getMethod("perfGetHalVer", argClasses);

                    } catch (Exception e) {
                        Log.i(TAG, "BoostFramework() : Exception_1 = perfGetHalVer not supported");
                        sPerfGetPerfHalVerFunc = null;
                    }

                    try {
                        argClasses = new Class[] {int.class, int.class, String.class, int.class, String.class};
                        sUXEngineEvents =  sPerfClass.getDeclaredMethod("perfUXEngine_events",
                                                                          argClasses);

                        argClasses = new Class[] {int.class};
                        sUXEngineTrigger =  sPerfClass.getDeclaredMethod("perfUXEngine_trigger",
                                                                           argClasses);
                    } catch (Exception e) {
                        Log.i(TAG, "BoostFramework() : Exception_4 = PreferredApps not supported");
                    }

                    sIsLoaded = true;
                }
                catch(Exception e) {
                    Log.e(TAG,"BoostFramework() : Exception_1 = " + e);
                }
                // Load UXE Class now Adding new try/catch block to avoid
                // any interference with Qperformance
                try {
                    sUxPerfClass = Class.forName(UXPERFORMANCE_CLASS);

                    Class[] argUxClasses = new Class[] {int.class, String.class, String.class};
                    sUxIOPStart = sUxPerfClass.getDeclaredMethod("perfIOPrefetchStart", argUxClasses);

                    sUxIsLoaded = true;
                }
                catch(Exception e) {
                    Log.e(TAG,"BoostFramework() Ux Perf: Exception = " + e);
                }
            }
        }
    }

/** @hide */
    public int perfLockAcquire(int duration, int... list) {
        int ret = -1;
        try {
            if (sAcquireFunc != null) {
                Object retVal = sAcquireFunc.invoke(mPerf, duration, list);
                ret = (int)retVal;
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

/** @hide */
    public int perfLockRelease() {
        int ret = -1;
        try {
            if (sReleaseFunc != null) {
                Object retVal = sReleaseFunc.invoke(mPerf);
                ret = (int)retVal;
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

/** @hide */
    public int perfHintRelease() {
        int ret = -1;
        try {
            if (sPerfHintRelFunc != null) {
                Object retVal = sPerfHintRelFunc.invoke(mPerf);
                ret = (int)retVal;
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

/** @hide */
    public int perfLockReleaseHandler(int handle) {
        int ret = -1;
        try {
            if (sReleaseHandlerFunc != null) {
                Object retVal = sReleaseHandlerFunc.invoke(mPerf, handle);
                ret = (int)retVal;
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

/** @hide */
    public int perfHint(int hint, String userDataStr) {
        return perfHint(hint, userDataStr, -1, -1);
    }

/** @hide */
    public int perfHint(int hint, String userDataStr, int userData) {
        return perfHint(hint, userDataStr, userData, -1);
    }

/** @hide */
    public int perfHint(int hint, String userDataStr, int userData1, int userData2) {
        int ret = -1;
        try {
            if (sPerfHintFunc != null) {
                Object retVal = sPerfHintFunc.invoke(mPerf, hint, userDataStr, userData1, userData2);
                ret = (int)retVal;
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

/** @hide */
    public double getPerfHalVersion() {
        double retVal = PERF_HAL_V22;
        try {
            if (sPerfGetPerfHalVerFunc != null) {
                Object ret = sPerfGetPerfHalVerFunc.invoke(mPerf);
                retVal = (double)ret;
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return retVal;
    }

/** @hide */
    public int perfGetFeedback(int req, String pkg_name) {
        int ret = -1;
        try {
            if (sFeedbackFunc != null) {
                Object retVal = sFeedbackFunc.invoke(mPerf, req, pkg_name);
                ret = (int)retVal;
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

/** @hide */
    public int perfGetFeedbackExtn(int req, String pkg_name, int numArgs, int... list) {
        int ret = -1;
        try {
            if (sFeedbackFuncExtn != null) {
                Object retVal = sFeedbackFuncExtn.invoke(mPerf, req, pkg_name, numArgs, list);
                ret = (int)retVal;
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

/** @hide */
    public int perfIOPrefetchStart(int pid, String pkgName, String codePath) {
        int ret = -1;
        try {
            Object retVal = sIOPStart.invoke(mPerf, pid, pkgName, codePath);
            ret = (int) retVal;
        } catch (Exception e) {
            Log.e(TAG, "Exception " + e);
        }
        try {
             Object retVal = sUxIOPStart.invoke(mUxPerf, pid, pkgName, codePath);
             ret = (int) retVal;
         } catch (Exception e) {
             Log.e(TAG, "Ux Perf Exception " + e);
         }

        return ret;
    }

/** @hide */
    public int perfIOPrefetchStop() {
        int ret = -1;
        try {
            Object retVal = sIOPStop.invoke(mPerf);
            ret = (int) retVal;
        } catch (Exception e) {
            Log.e(TAG, "Exception " + e);
        }
        return ret;
    }

/** @hide */
    public int perfUXEngine_events(int opcode, int pid, String pkgName, int lat) {
        return perfUXEngine_events(opcode, pid, pkgName, lat, null);
     }

/** @hide */
    public int perfUXEngine_events(int opcode, int pid, String pkgName, int lat, String codePath) {
        int ret = -1;
        try {
            if (sUXEngineEvents == null) {
                return ret;
            }

            Object retVal = sUXEngineEvents.invoke(mPerf, opcode, pid, pkgName, lat,codePath);
            ret = (int) retVal;
        } catch (Exception e) {
            Log.e(TAG, "Exception " + e);
        }
        return ret;
    }


/** @hide */
    public String perfUXEngine_trigger(int opcode) {
        String ret = null;
        try {
            if (sUXEngineTrigger == null) {
                return ret;
            }
            Object retVal = sUXEngineTrigger.invoke(mPerf, opcode);
            ret = (String) retVal;
        } catch (Exception e) {
            Log.e(TAG, "Exception " + e);
        }
        return ret;
    }

/** @hide */
    public String perfSyncRequest(int opcode) {
        String ret = null;
        try {
            if (sPerfSyncRequest == null) {
                return ret;
            }
            Object retVal = sPerfSyncRequest.invoke(mPerf, opcode);
            ret = (String) retVal;
        } catch (Exception e) {
            Log.e(TAG, "Exception " + e);
        }
        return ret;
    }

/** @hide */
    public String perfGetProp(String prop_name, String def_val) {
        String ret = "";
        try {
            if (sPerfGetPropFunc != null) {
                Object retVal = sPerfGetPropFunc.invoke(mPerf, prop_name, def_val);
                ret = (String)retVal;
            }else {
                ret = def_val;
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

/** @hide */
    public int perfLockAcqAndRelease(int handle, int duration, int numArgs,int reserveNumArgs, int... list) {
        int ret = -1;
        try {
            if (sAcqAndReleaseFunc != null) {
                Object retVal = sAcqAndReleaseFunc.invoke(mPerf, handle, duration, numArgs, reserveNumArgs, list);
                ret = (int)retVal;
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

/** @hide */
    public void perfEvent(int eventId, String pkg_name) {
        perfEvent(eventId, pkg_name, 0);
    }

/** @hide */
    public void perfEvent(int eventId, String pkg_name, int numArgs, int... list) {
        try {
            if (sPerfEventFunc != null) {
                sPerfEventFunc.invoke(mPerf, eventId, pkg_name, numArgs, list);
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
    }

/** @hide */
    public int perfHintAcqRel(int handle, int hint, String pkg_name) {
        return perfHintAcqRel(handle, hint, pkg_name, -1, -1, 0);
    }

/** @hide */
    public int perfHintAcqRel(int handle, int hint, String pkg_name, int duration) {
        return perfHintAcqRel(handle, hint, pkg_name, duration, -1, 0);
    }

/** @hide */
    public int perfHintAcqRel(int handle, int hint, String pkg_name, int duration, int hintType) {
        return perfHintAcqRel(handle, hint, pkg_name, duration, hintType, 0);
    }

/** @hide */
    public int perfHintAcqRel(int handle, int hint, String pkg_name, int duration,
                              int hintType, int numArgs, int... list) {
        int ret = -1;
        try {
            if (sperfHintAcqRelFunc != null) {
                Object retVal = sperfHintAcqRelFunc.invoke(mPerf,handle, hint, pkg_name,
                                                           duration, hintType, numArgs, list);
                ret = (int)retVal;
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

/** @hide */
    public int perfHintRenew(int handle, int hint, String pkg_name) {
        return perfHintRenew(handle, hint, pkg_name, -1, -1, 0);
    }

/** @hide */
    public int perfHintRenew(int handle, int hint, String pkg_name, int duration) {
        return perfHintRenew(handle, hint, pkg_name, duration, -1, 0);
    }

/** @hide */
    public int perfHintRenew(int handle, int hint, String pkg_name, int duration, int hintType) {
        return perfHintRenew(handle, hint, pkg_name, duration, hintType, 0);
    }

/** @hide */
    public int perfHintRenew(int handle, int hint, String pkg_name, int duration,
                             int hintType, int numArgs, int... list) {
        int ret = -1;
        try {
            if (sperfHintRenewFunc != null) {
                Object retVal = sperfHintRenewFunc.invoke(mPerf,handle, hint, pkg_name,
                                                          duration, hintType, numArgs, list);
                ret = (int)retVal;
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

    /** @hide */
    public static class ScrollOptimizer {
        /** @hide */
        public static final int FLING_START = 1;
        /** @hide */
        public static final int FLING_END = 0;
        private static final String SCROLL_OPT_PROP = "ro.vendor.perf.scroll_opt";
        private static final String QXPERFORMANCE_JAR =
                "/system/framework/QXPerformance.jar";
        private static final String SCROLL_OPT_CLASS =
                "com.qualcomm.qti.QXPerformance.ScrollOptimizer";
        private static boolean sScrollOptProp = false;
        private static boolean sScrollOptEnable = false;
        private static boolean sQXIsLoaded = false;
        private static Class<?> sQXPerfClass = null;
        private static Method sSetFrameInterval = null;
        private static Method sDisableOptimizer = null;
        private static Method sSetBLASTBufferQueue = null;
        private static Method sSetMotionType = null;
        private static Method sSetVsyncTime = null;
        private static Method sSetUITaskStatus = null;
        private static Method sSetFlingFlag = null;
        private static Method sShouldUseVsync = null;
        private static Method sGetFrameDelay = null;
        private static Method sGetAdjustedAnimationClock = null;

        private static void initQXPerfFuncs() {
            if (sQXIsLoaded) return;

            try {
                sScrollOptProp = SystemProperties.getBoolean(SCROLL_OPT_PROP, false);
                if (!sScrollOptProp) {
                    sScrollOptEnable = false;
                    sQXIsLoaded = true;
                    return;
                }

                PathClassLoader qXPerfClassLoader = new PathClassLoader(
                        QXPERFORMANCE_JAR, ClassLoader.getSystemClassLoader());
                sQXPerfClass = qXPerfClassLoader.loadClass(SCROLL_OPT_CLASS);
                Class[] argClasses = new Class[]{long.class};
                sSetFrameInterval = sQXPerfClass.getMethod(
                        "setFrameInterval", argClasses);

                argClasses = new Class[]{boolean.class};
                sDisableOptimizer = sQXPerfClass.getMethod("disableOptimizer", argClasses);

                argClasses = new Class[]{BLASTBufferQueue.class};
                sSetBLASTBufferQueue = sQXPerfClass.getMethod("setBLASTBufferQueue", argClasses);

                argClasses = new Class[]{int.class};
                sSetMotionType = sQXPerfClass.getMethod("setMotionType", argClasses);

                argClasses = new Class[]{long.class};
                sSetVsyncTime = sQXPerfClass.getMethod("setVsyncTime", argClasses);

                argClasses = new Class[]{boolean.class};
                sSetUITaskStatus = sQXPerfClass.getMethod("setUITaskStatus", argClasses);

                argClasses = new Class[]{int.class};
                sSetFlingFlag = sQXPerfClass.getMethod("setFlingFlag", argClasses);

                sShouldUseVsync = sQXPerfClass.getMethod("shouldUseVsync");

                argClasses = new Class[]{long.class};
                sGetFrameDelay = sQXPerfClass.getMethod("getFrameDelay", argClasses);

                argClasses = new Class[]{long.class};
                sGetAdjustedAnimationClock = sQXPerfClass.getMethod(
                        "getAdjustedAnimationClock", argClasses);
            } catch (Exception e) {
                Log.e(TAG, "initQXPerfFuncs failed");
                e.printStackTrace();
            } finally {
                // If frameworks and perf changes don't match(may not built together)
                // or other exception, need to set sQXIsLoaded as true to avoid retry.
                sQXIsLoaded = true;
            }
        }

        /** @hide */
        public static void setFrameInterval(long frameIntervalNanos) {
            if (sQXIsLoaded) {
                if (sScrollOptEnable && sSetFrameInterval != null) {
                    try {
                        sSetFrameInterval.invoke(null, frameIntervalNanos);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return;
            }
            Thread initThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    synchronized(ScrollOptimizer.class) {
                        try {
                            initQXPerfFuncs();
                            if (sScrollOptProp && sSetFrameInterval != null) {
                                sSetFrameInterval.invoke(null, frameIntervalNanos);
                                sScrollOptEnable = true;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to run initThread.");
                            e.printStackTrace();
                        }
                    }
                }
            });
            initThread.start();
        }

        /** @hide */
        public static void disableOptimizer(boolean disabled) {
            if (sScrollOptEnable && sDisableOptimizer != null) {
                try {
                    sDisableOptimizer.invoke(null, disabled);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        /** @hide */
        public static void setBLASTBufferQueue(BLASTBufferQueue blastBufferQueue) {
            if (sScrollOptEnable && sSetBLASTBufferQueue != null) {
                try {
                    sSetBLASTBufferQueue.invoke(null, blastBufferQueue);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        /** @hide */
        public static void setMotionType(int eventType) {
            if (sScrollOptEnable && sSetMotionType != null) {
                try {
                    sSetMotionType.invoke(null, eventType);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        /** @hide */
        public static void setVsyncTime(long vsyncTimeNanos) {
            if (sScrollOptEnable && sSetVsyncTime != null) {
                try {
                    sSetVsyncTime.invoke(null, vsyncTimeNanos);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        /** @hide */
        public static void setUITaskStatus(boolean running) {
            if (sScrollOptEnable && sSetUITaskStatus != null) {
                try {
                    sSetUITaskStatus.invoke(null, running);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        /** @hide */
        public static void setFlingFlag(int flag) {
            if (sScrollOptEnable && sSetFlingFlag != null) {
                try {
                    sSetFlingFlag.invoke(null, flag);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        /** @hide */
        public static boolean shouldUseVsync(boolean defaultVsyncFlag) {
            boolean useVsync = defaultVsyncFlag;
            if (sScrollOptEnable && sShouldUseVsync != null) {
                try {
                    Object retVal = sShouldUseVsync.invoke(null);
                    useVsync = (boolean)retVal;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return useVsync;
        }

        /** @hide */
        public static long getFrameDelay(long defaultDelay, long lastFrameTimeNanos) {
            long frameDelay = defaultDelay;
            if (sScrollOptEnable && sGetFrameDelay != null) {
                try {
                    Object retVal = sGetFrameDelay.invoke(null, lastFrameTimeNanos);
                    frameDelay = (long)retVal;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return frameDelay;
        }

        /** @hide */
        public static long getAdjustedAnimationClock(long frameTimeNanos) {
            long newFrameTimeNanos = frameTimeNanos;
            if (sScrollOptEnable && sGetAdjustedAnimationClock != null) {
                try {
                    Object retVal = sGetAdjustedAnimationClock.invoke(null,
                            frameTimeNanos);
                    newFrameTimeNanos = (long)retVal;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return newFrameTimeNanos;
        }
    }

    //UI PERF START
    private static class LRUMap<K,V> extends LinkedHashMap<K,V> {
        private final int maxSize;

        LRUMap(int maxSize) {
            super(100, 0.75f, true);
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
            return size() > maxSize;
        }
    }

    private static class UIPerfMode {
        private static UIPerfMode instance = null;
        private static String UI_PERF_ENABLE = "sys.ui.perfmode.enable";
        private static String UI_PERF_ENHANCEMENT = "ro.vendor.ui.perfmode_enhance";
        private Context mContext;
        private String[] mUIPerfProcs = null;
        private String[] mLegacyUIPerfProcs = null;
        private String[] mIgnoreProcs = null;
        private String[] mUIPerfGpuActivities = null;
        private String[] mUIPerfCpuActivities = null;
        private String[] mUIPerfCpuGpuActivities = null;
        private String[] mUIPerfCpuAggressive = null;
        private boolean mUiPerfInited = false;
        private UiPerfProcsObserver observer = null;
        private boolean mUIPerfEnhance = false;
        private BoostFramework mPerf = new BoostFramework();
        private LRUMap<String,Boolean> mLRUMap = new LRUMap(30);

        private class UiPerfProcsObserver extends ContentObserver {
            private Context mContext = null;
            UiPerfProcsObserver(Context context) {
                super(null);
                mContext = context;
            }

            public void onChange(boolean selfChange, Uri uri) {
                try {
                    long callingId = Binder.clearCallingIdentity();
                    if (Settings.Global.getUriFor(KEY_PKGS).equals(uri)) {
                        synchronized (UIPerfMode.this) {
                            update();
                        }
                    }
                    Binder.restoreCallingIdentity(callingId);
                } catch (Exception e) {}
            }
        }

        /** @hide */
        public static UIPerfMode getInstance(Context context) {
            synchronized(UIPerfMode.class) {
                if (!ActivityThread.isSystem() ||
                        !SystemProperties.getBoolean(UI_PERF_ENABLE, false)) {
                    return null;
                }
                if (context != null) {
                    if (instance == null) {
                        instance = new UIPerfMode(context);
                        if (!instance.isInit()) {
                            instance.clean();
                            instance = null;
                        }
                    }
                    return instance;
                }
                return null;
            }
        }

        private UIPerfMode(Context context) {
            if (context != null) {
                mContext = context.getApplicationContext();
            }
            try {
                if (mContext != null) {
                    long callingId = Binder.clearCallingIdentity();
                    update();
                    observer = new UiPerfProcsObserver(mContext);
                    mContext.getContentResolver()
                            .registerContentObserver(Settings.Global.getUriFor(KEY_PKGS),
                                                     false, observer);
                    mUiPerfInited = true;
                    Binder.restoreCallingIdentity(callingId);

                    if (mPerf != null) {
                        mUIPerfEnhance =
                            Boolean.parseBoolean(mPerf.perfGetProp(UI_PERF_ENHANCEMENT,
                                                                   "false"));
                    }
                }
            } catch (Exception e) {
                mUiPerfInited = false;
            }
        }

        private void update() {
            try {
                if (mContext != null) {
                    String str = Settings.Global.getString(mContext.getContentResolver(),
                                     KEY_PKGS);
                    if (str != null && !str.isEmpty()) {
                        mUIPerfProcs = str.split(";");
                    }
                    str = Settings.Global.getString(mContext.getContentResolver(),
                                     KEY_GPU_PREFER);
                    if (str != null && !str.isEmpty()) {
                        mUIPerfGpuActivities = str.split(";");
                    }
                    str = Settings.Global.getString(mContext.getContentResolver(),
                                     KEY_CPU_PREFER);
                    if (str != null && !str.isEmpty()) {
                        mUIPerfCpuActivities = str.split(";");
                    }
                    str = Settings.Global.getString(mContext.getContentResolver(),
                                     KEY_CPU_GPU);
                    if (str != null && !str.isEmpty()) {
                        mUIPerfCpuGpuActivities = str.split(";");
                    }
                    str = Settings.Global.getString(mContext.getContentResolver(),
                                     KEY_CPU_AGGRESSIVE);
                    if (str != null && !str.isEmpty()) {
                        mUIPerfCpuAggressive = str.split(";");
                    }
                    str = Settings.Global.getString(mContext.getContentResolver(),
                                     KEY_LEGACY_UI_PERF_PKGS);
                    if (str != null && !str.isEmpty()) {
                        mLegacyUIPerfProcs = str.split(";");
                    }
                    str = Settings.Global.getString(mContext.getContentResolver(),
                                     KEY_IGNORE_PKGS);
                    if (str != null && !str.isEmpty()) {
                        mIgnoreProcs = str.split(";");
                    }
                }
            } catch (Exception e) {
            }
        }

        /** @hide */
        public void clean() {
            if (mContext != null && observer != null) {
                try {
                    mContext.getContentResolver().unregisterContentObserver(observer);
                } catch (Exception e) {}
            }
        }

        /** @hide */
        public boolean isInit() {
            return mUiPerfInited;
        }

        /** @hide */
        public boolean shouldUseUiPerf(String pkgName) {
            if (pkgName == null || pkgName.isEmpty()) {
                return false;
            }
            synchronized (this) {
                if (mUIPerfProcs != null) {
                    for (int i = 0; i < mUIPerfProcs.length; i++) {
                        if (pkgName.equals(mUIPerfProcs[i])) {
                            return true;
                        }
                    }
                }
            }
            return enhance(pkgName);
        }

        private boolean enhance(String pkgName) {
            if (mUIPerfEnhance) {
                if (mContext != null) {
                    PackageManager pm = mContext.getPackageManager();
                    if (pm != null) {
                        try {
                            ApplicationInfo aInfo = pm.getApplicationInfo(pkgName, 0);
                            if (aInfo == null) {
                                return false;
                            }
                            if (aInfo.isOdm() || aInfo.isOem()
                                              || aInfo.isProduct()
                                              || aInfo.isSystemApp()
                                              || aInfo.isSystemExt()
                                              || aInfo.isUpdatedSystemApp()
                                              || aInfo.isVendor()
                                              || aInfo.isPrivilegedApp()
                                              || aInfo.isSignedWithPlatformKey()) {
                                return false;
                            }
                        } catch (PackageManager.NameNotFoundException e) {
                            return false;
                        }
                    }
                }
                synchronized (this) {
                    if (mIgnoreProcs != null) {
                        for (int i = 0; i < mIgnoreProcs.length; i++) {
                            if (pkgName.equals(mIgnoreProcs[i])) {
                                return false;
                            }
                        }
                    }
                }
                if (mLRUMap.containsKey(pkgName)) {
                    return mLRUMap.get(pkgName);
                }
                if (mPerf != null) {
                    int type = mPerf.perfGetFeedback(VENDOR_FEEDBACK_WORKLOAD_TYPE, pkgName);
                    if (type == WorkloadType.APP_OF_INTEREST) {
                       mLRUMap.put(pkgName, true);
                       return true;
                    }
                }
                mLRUMap.put(pkgName, false);
            }
            return false;
        }

        /** @hide */
        public void updateUiPerfState(String pkgName, int pid) {
            if (shouldUseUiPerf(pkgName)) {
                SystemProperties.set(UI_PERF_PROP, Integer.toString(pid));
            } else {
                SystemProperties.set(UI_PERF_PROP, "0");
            }
        }

        /** @hide */
        public int getLegacyUiPerfHint(String pkgName) {
            int hint = -1;
            if (pkgName == null || pkgName.isEmpty()) {
                return hint;
            }
            synchronized (this) {
                if (SystemProperties.getBoolean(UI_LEGACY_PERF_PROP, false) &&
                        mLegacyUIPerfProcs != null) {
                    for (int i = 0; i < mLegacyUIPerfProcs.length; i++) {
                        if (pkgName.equals(mLegacyUIPerfProcs[i])) {
                            hint = VENDOR_HINT_PERFORMANCE_MODE;
                            return hint;
                        }
                    }
                }
            }
            return hint;
        }

        /** @hide */
        public int getUiPerfHint(String activityName) {
            int hint = -1;
            if (activityName == null || activityName.isEmpty()) {
                return hint;
            }
            synchronized (this) {
                if (mUIPerfGpuActivities != null) {
                    for (int i = 0; i < mUIPerfGpuActivities.length; i++) {
                        if (activityName.equals(mUIPerfGpuActivities[i])) {
                            hint = VENDOR_HINT_SCENARIO_GPU;
                            return hint;
                        }
                    }
                }
                if (mUIPerfCpuActivities != null) {
                    for (int i = 0; i < mUIPerfCpuActivities.length; i++) {
                        if (activityName.equals(mUIPerfCpuActivities[i])) {
                            hint = VENDOR_HINT_SCENARIO_CPU;
                            return hint;
                        }
                    }
                }
                if (mUIPerfCpuGpuActivities != null) {
                    for (int i = 0; i < mUIPerfCpuGpuActivities.length; i++) {
                        if (activityName.equals(mUIPerfCpuGpuActivities[i])) {
                            hint = VENDOR_HINT_SCENARIO_CPU_GPU;
                            return hint;
                        }
                    }
                }
                if (mUIPerfCpuAggressive != null) {
                    for (int i = 0; i < mUIPerfCpuAggressive.length; i++) {
                        if (activityName.equals(mUIPerfCpuAggressive[i])) {
                            hint = VENDOR_HINT_SCENARIO_CPU_AGGRESSIVE;
                            return hint;
                        }
                    }
                }
            }
            return hint;
        }
    }

    /** @hide */
    public boolean shouldUseUiPerf(Context context, String pkgName) {
        UIPerfMode uiPerf = UIPerfMode.getInstance(context);
        if (uiPerf != null) {
            return uiPerf.shouldUseUiPerf(pkgName);
        }
        return false;
    }

    /** @hide */
    public void updateUiPerfState(Context context, String pkgName, int pid) {
        UIPerfMode uiPerf = UIPerfMode.getInstance(context);
        if (uiPerf != null) {
            uiPerf.updateUiPerfState(pkgName, pid);
        }
    }

    /** @hide */
    public int getLegacyUiPerfHint(Context context, String pkgName) {
        int hint = -1;
        UIPerfMode uiPerf = UIPerfMode.getInstance(context);
        if (uiPerf != null) {
            hint = uiPerf.getLegacyUiPerfHint(pkgName);
        }
        return hint;
    }

    /** @hide */
    public int getUiPerfHint(Context context, String activityName) {
        int hint = -1;
        UIPerfMode uiPerf = UIPerfMode.getInstance(context);
        if (uiPerf != null) {
            hint = uiPerf.getUiPerfHint(activityName);
        }
        return hint;
    }

    /** @hide */
    public static boolean shouldUseUiPerf() {
        if (SystemProperties.getInt(UI_PERF_PROP, 0) == Process.myPid()) {
            return true;
        }
        return false;
    }
    //UI PERF END
};
