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
#undef ANDROID_UTILS_REF_BASE_DISABLE_IMPLICIT_CONSTRUCTION // TODO:remove this and fix code

#define LOG_TAG "CompositionSamplingListener"

#include <android/gui/BnRegionSamplingListener.h>
#include <android_runtime/AndroidRuntime.h>
#include <android_runtime/Log.h>
#include <binder/IServiceManager.h>
#include <gui/ISurfaceComposer.h>
#include <gui/SurfaceComposerClient.h>
#include <nativehelper/JNIHelp.h>
#include <ui/Rect.h>
#include <utils/Log.h>
#include <utils/RefBase.h>

#include "android_util_Binder.h"
#include "core_jni_helpers.h"

namespace android {

namespace {

struct {
    jclass mClass;
    jmethodID mDispatchOnSampleCollected;
} gListenerClassInfo;

struct CompositionSamplingListener : public gui::BnRegionSamplingListener {
    CompositionSamplingListener(JNIEnv* env, jobject listener)
            : mListener(env->NewWeakGlobalRef(listener)) {}

    binder::Status onSampleCollected(float medianLuma) override {
        JNIEnv* env = AndroidRuntime::getJNIEnv();
        if (env == nullptr) {
            ALOGE("Unable to retrieve JNIEnv in onSampleCollected.");
            return binder::Status::ok();
        }

        jobject listener = env->NewLocalRef(mListener);
        if (listener == NULL) {
            // Weak reference went out of scope
            return binder::Status::ok();
        }

        env->CallStaticVoidMethod(gListenerClassInfo.mClass,
                gListenerClassInfo.mDispatchOnSampleCollected, listener,
                static_cast<jfloat>(medianLuma));

        env->DeleteLocalRef(listener);

        if (env->ExceptionCheck()) {
            ALOGE("CompositionSamplingListener.onSampleCollected() failed.");
            LOGE_EX(env);
            env->ExceptionClear();
        }

        return binder::Status::ok();
    }

protected:
    virtual ~CompositionSamplingListener() {
        JNIEnv* env = AndroidRuntime::getJNIEnv();
        if (env != nullptr) {
            env->DeleteWeakGlobalRef(mListener);
        }
    }

private:
    jweak mListener;
};

jlong nativeCreate(JNIEnv* env, jclass clazz, jobject obj) {
    if (obj == nullptr) {
        ALOGE("nativeCreate: null listener object");
        return 0;
    }

    CompositionSamplingListener* listener = new CompositionSamplingListener(env, obj);
    if (listener == nullptr) {
        ALOGE("nativeCreate: failed to create native listener");
        return 0;
    }

    listener->incStrong((void*)nativeCreate);
    return reinterpret_cast<jlong>(listener);
}

void nativeDestroy(JNIEnv* env, jclass clazz, jlong ptr) {
    if (ptr == 0) {
        ALOGW("nativeDestroy: null pointer");
        return;
    }

    CompositionSamplingListener* listener = reinterpret_cast<CompositionSamplingListener*>(ptr);
    listener->decStrong((void*)nativeCreate);
}

void nativeRegister(JNIEnv* env, jclass clazz, jlong ptr, jlong stopLayerObj,
        jint left, jint top, jint right, jint bottom) {
    if (ptr == 0) {
        ALOGE("nativeRegister: null listener pointer");
        jniThrowRuntimeException(env, "Invalid listener pointer");
        return;
    }

    sp<CompositionSamplingListener> listener = reinterpret_cast<CompositionSamplingListener*>(ptr);
    auto stopLayer = reinterpret_cast<SurfaceControl*>(stopLayerObj);
    sp<IBinder> stopLayerHandle = stopLayer != nullptr ? stopLayer->getHandle() : nullptr;

    if (left >= right || top >= bottom) {
        ALOGW("nativeRegister: invalid sampling area (%d,%d,%d,%d)", left, top, right, bottom);
        return;
    }

    status_t result = SurfaceComposerClient::addRegionSamplingListener(
            Rect(left, top, right, bottom), stopLayerHandle, listener);

    if (result != OK) {
        ALOGE("addRegionSamplingListener failed with status: %d", result);

        if (result != INVALID_OPERATION && result != UNKNOWN_ERROR) {
            constexpr auto error_msg = "Couldn't addRegionSamplingListener";
            jniThrowRuntimeException(env, error_msg);
        } else {
            ALOGW("Region sampling not supported on this device (status: %d)", result);
        }
    }
}

void nativeUnregister(JNIEnv* env, jclass clazz, jlong ptr) {
    if (ptr == 0) {
        ALOGW("nativeUnregister: null pointer");
        return;
    }

    sp<CompositionSamplingListener> listener = reinterpret_cast<CompositionSamplingListener*>(ptr);

    status_t result = SurfaceComposerClient::removeRegionSamplingListener(listener);
    if (result != OK) {
        ALOGE("removeRegionSamplingListener failed with status: %d", result);

        if (result != INVALID_OPERATION && result != UNKNOWN_ERROR) {
            constexpr auto error_msg = "Couldn't removeRegionSamplingListener";
            jniThrowRuntimeException(env, error_msg);
        } else {
            ALOGW("Region sampling removal not supported on this device (status: %d)", result);
        }
    }
}

const JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "nativeCreate", "(Landroid/view/CompositionSamplingListener;)J",
            (void*)nativeCreate },
    { "nativeDestroy", "(J)V",
            (void*)nativeDestroy },
    { "nativeRegister", "(JJIIII)V",
            (void*)nativeRegister },
    { "nativeUnregister", "(J)V",
            (void*)nativeUnregister }
};

} // namespace

int register_android_view_CompositionSamplingListener(JNIEnv* env) {
    int res = jniRegisterNativeMethods(env, "android/view/CompositionSamplingListener",
            gMethods, NELEM(gMethods));
    if (res < 0) {
        ALOGE("Unable to register native methods for CompositionSamplingListener");
        return res;
    }

    jclass clazz = env->FindClass("android/view/CompositionSamplingListener");
    if (clazz == nullptr) {
        ALOGE("Unable to find CompositionSamplingListener class");
        return -1;
    }

    gListenerClassInfo.mClass = MakeGlobalRefOrDie(env, clazz);
    gListenerClassInfo.mDispatchOnSampleCollected = env->GetStaticMethodID(
            clazz, "dispatchOnSampleCollected", "(Landroid/view/CompositionSamplingListener;F)V");

    if (gListenerClassInfo.mDispatchOnSampleCollected == nullptr) {
        ALOGE("Unable to find dispatchOnSampleCollected method");
        return -1;
    }

    return 0;
}

} // namespace android
