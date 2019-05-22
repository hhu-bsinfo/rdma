/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class de_hhu_bsinfo_neutrino_verbs_Verbs */

#ifndef _Included_de_hhu_bsinfo_neutrino_verbs_Verbs
#define _Included_de_hhu_bsinfo_neutrino_verbs_Verbs
#ifdef __cplusplus
extern "C" {
#endif
#undef de_hhu_bsinfo_neutrino_verbs_Verbs_DEFAULT_POOL_SIZE
#define de_hhu_bsinfo_neutrino_verbs_Verbs_DEFAULT_POOL_SIZE 1024L
/*
 * Class:     de_hhu_bsinfo_neutrino_verbs_Verbs
 * Method:    getNumDevices
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_de_hhu_bsinfo_neutrino_verbs_Verbs_getNumDevices
  (JNIEnv *, jclass);

/*
 * Class:     de_hhu_bsinfo_neutrino_verbs_Verbs
 * Method:    getDeviceName
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_de_hhu_bsinfo_neutrino_verbs_Verbs_getDeviceName
  (JNIEnv *, jclass, jlong);

/*
 * Class:     de_hhu_bsinfo_neutrino_verbs_Verbs
 * Method:    openDevice
 * Signature: (IJ)V
 */
JNIEXPORT void JNICALL Java_de_hhu_bsinfo_neutrino_verbs_Verbs_openDevice
  (JNIEnv *, jclass, jint, jlong);

/*
 * Class:     de_hhu_bsinfo_neutrino_verbs_Verbs
 * Method:    closeDevice
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_de_hhu_bsinfo_neutrino_verbs_Verbs_closeDevice
  (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     de_hhu_bsinfo_neutrino_verbs_Verbs
 * Method:    queryDevice
 * Signature: (JJJ)V
 */
JNIEXPORT void JNICALL Java_de_hhu_bsinfo_neutrino_verbs_Verbs_queryDevice
  (JNIEnv *, jclass, jlong, jlong, jlong);

/*
 * Class:     de_hhu_bsinfo_neutrino_verbs_Verbs
 * Method:    queryPort
 * Signature: (JJIJ)V
 */
JNIEXPORT void JNICALL Java_de_hhu_bsinfo_neutrino_verbs_Verbs_queryPort
  (JNIEnv *, jclass, jlong, jlong, jint, jlong);

/*
 * Class:     de_hhu_bsinfo_neutrino_verbs_Verbs
 * Method:    allocateProtectionDomain
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_de_hhu_bsinfo_neutrino_verbs_Verbs_allocateProtectionDomain
  (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     de_hhu_bsinfo_neutrino_verbs_Verbs
 * Method:    deallocateProtectionDomain
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_de_hhu_bsinfo_neutrino_verbs_Verbs_deallocateProtectionDomain
  (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     de_hhu_bsinfo_neutrino_verbs_Verbs
 * Method:    registerMemoryRegion
 * Signature: (JJJIJ)V
 */
JNIEXPORT void JNICALL Java_de_hhu_bsinfo_neutrino_verbs_Verbs_registerMemoryRegion
  (JNIEnv *, jclass, jlong, jlong, jlong, jint, jlong);

/*
 * Class:     de_hhu_bsinfo_neutrino_verbs_Verbs
 * Method:    deregisterMemoryRegion
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_de_hhu_bsinfo_neutrino_verbs_Verbs_deregisterMemoryRegion
  (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     de_hhu_bsinfo_neutrino_verbs_Verbs
 * Method:    createCompletionQueue
 * Signature: (JIJJIJ)V
 */
JNIEXPORT void JNICALL Java_de_hhu_bsinfo_neutrino_verbs_Verbs_createCompletionQueue
  (JNIEnv *, jclass, jlong, jint, jlong, jlong, jint, jlong);

/*
 * Class:     de_hhu_bsinfo_neutrino_verbs_Verbs
 * Method:    destroyCompletionQueue
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_de_hhu_bsinfo_neutrino_verbs_Verbs_destroyCompletionQueue
  (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     de_hhu_bsinfo_neutrino_verbs_Verbs
 * Method:    postSendWorkRequest
 * Signature: (JJJ)V
 */
JNIEXPORT void JNICALL Java_de_hhu_bsinfo_neutrino_verbs_Verbs_postSendWorkRequest
  (JNIEnv *, jclass, jlong, jlong, jlong);

/*
 * Class:     de_hhu_bsinfo_neutrino_verbs_Verbs
 * Method:    postReceiveWorkRequest
 * Signature: (JJJ)V
 */
JNIEXPORT void JNICALL Java_de_hhu_bsinfo_neutrino_verbs_Verbs_postReceiveWorkRequest
  (JNIEnv *, jclass, jlong, jlong, jlong);

/*
 * Class:     de_hhu_bsinfo_neutrino_verbs_Verbs
 * Method:    createSharedReceiveQueue
 * Signature: (JJJ)V
 */
JNIEXPORT void JNICALL Java_de_hhu_bsinfo_neutrino_verbs_Verbs_createSharedReceiveQueue
  (JNIEnv *, jclass, jlong, jlong, jlong);

/*
 * Class:     de_hhu_bsinfo_neutrino_verbs_Verbs
 * Method:    createQueuePair
 * Signature: (JJJ)V
 */
JNIEXPORT void JNICALL Java_de_hhu_bsinfo_neutrino_verbs_Verbs_createQueuePair
  (JNIEnv *, jclass, jlong, jlong, jlong);

#ifdef __cplusplus
}
#endif
#endif
