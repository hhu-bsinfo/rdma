#include <de_hhu_bsinfo_neutrino_util_FileDescriptor.h>
#include <unistd.h>
#include <fcntl.h>

JNIEXPORT jint JNICALL Java_de_hhu_bsinfo_neutrino_util_FileDescriptor_close0 (JNIEnv *env, jclass clazz, jint fd) {
    return close(fd);
}

JNIEXPORT jint JNICALL Java_de_hhu_bsinfo_neutrino_util_FileDescriptor_setFlags0 (JNIEnv *env, jclass clazz, jint fd, jint mode) {
    return fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) | mode);
}

JNIEXPORT jint JNICALL Java_de_hhu_bsinfo_neutrino_util_FileDescriptor_getFlags0 (JNIEnv *env, jclass clazz, jint fd) {
    return fcntl(fd, F_GETFL);
}