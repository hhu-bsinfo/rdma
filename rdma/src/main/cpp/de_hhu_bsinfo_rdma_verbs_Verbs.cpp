#include <de_hhu_bsinfo_rdma_verbs_Verbs.h>
#include <infiniband/verbs.h>
#include <stddef.h>
#include <vector>
#include <Util.hpp>

JNIEXPORT jint JNICALL Java_de_hhu_bsinfo_rdma_verbs_Verbs_getNumDevices (JNIEnv *env, jclass clazz) {
    int numDevices = 0;
    ibv_device **devices = ibv_get_device_list(&numDevices);
    if (devices != nullptr) {
        ibv_free_device_list(devices);
    }
    return numDevices;
}

JNIEXPORT jstring JNICALL Java_de_hhu_bsinfo_rdma_verbs_Verbs_getDeviceName (JNIEnv *env, jclass clazz, jlong contextHandle) {
    auto context = castHandle<ibv_context>(contextHandle);

    const char *ret = ibv_get_device_name(context->device);
    if(ret == nullptr) {
        return env->NewStringUTF("");
    } else {
        return env->NewStringUTF(ret);
    }
}

JNIEXPORT void JNICALL Java_de_hhu_bsinfo_rdma_verbs_Verbs_openDevice (JNIEnv *env, jclass clazz, jint index, jlong resultHandle) {
    auto result = castHandle<Result>(resultHandle);
    int numDevices = 0;
    ibv_device **devices = ibv_get_device_list(&numDevices);
    if (devices == nullptr) {
        result->status = 1;
        return;
    }

    if (index >= numDevices) {
        ibv_free_device_list(devices);
        result->status = 1;
        return;
    }

    result->handle = reinterpret_cast<long>(ibv_open_device(devices[index]));
    result->status = 0;

    ibv_free_device_list(devices);
}

JNIEXPORT void JNICALL Java_de_hhu_bsinfo_rdma_verbs_Verbs_closeDevice (JNIEnv *env, jclass clazz, jlong contextHandle, jlong resultHandle) {
    auto context = castHandle<ibv_context>(contextHandle);
    auto result = castHandle<Result>(resultHandle);

    result->status = ibv_close_device(context);
    result->handle = 0;
}

JNIEXPORT void JNICALL Java_de_hhu_bsinfo_rdma_verbs_Verbs_queryDevice (JNIEnv *env, jclass clazz, jlong contextHandle, jlong deviceHandle, jlong resultHandle) {
    auto context = castHandle<ibv_context>(contextHandle);
    auto device = castHandle<ibv_device_attr>(deviceHandle);
    auto result = castHandle<Result>(resultHandle);

    result->status = ibv_query_device(context, device);
    result->handle = 0;
}

JNIEXPORT void JNICALL Java_de_hhu_bsinfo_rdma_verbs_Verbs_queryPort (JNIEnv *env, jclass clazz, jlong contextHandle, jlong portHandle, jint portNumber, jlong resultHandle) {
    auto context = castHandle<ibv_context>(contextHandle);
    auto port = castHandle<ibv_port_attr>(portHandle);
    auto result = castHandle<Result>(resultHandle);

    result->status = ibv_query_port(context, portNumber, port);
    result->handle = 0;
}

JNIEXPORT void JNICALL Java_de_hhu_bsinfo_rdma_verbs_Verbs_allocateProtectionDomain (JNIEnv *env, jclass clazz, jlong contextHandle, jlong resultHandle) {
    auto context = castHandle<ibv_context>(contextHandle);
    auto result = castHandle<Result>(resultHandle);

    result->handle = reinterpret_cast<long>(ibv_alloc_pd(context));
    result->status = result->handle == 0 ? 1 : 0;
}

JNIEXPORT void JNICALL Java_de_hhu_bsinfo_rdma_verbs_Verbs_deallocateProtectionDomain (JNIEnv *env, jclass clazz, jlong protectionDomainHandle, jlong resultHandle) {
    auto protectionDomain = castHandle<ibv_pd>(protectionDomainHandle);
    auto result = castHandle<Result>(resultHandle);

    result->status = ibv_dealloc_pd(protectionDomain);
    result->handle = 0;
}