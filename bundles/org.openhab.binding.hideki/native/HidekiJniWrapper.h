#ifndef HIDEKI_JNI_WRAPPER_H
#define HIDEKI_JNI_WRAPPER_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     org_openhab_binding_hideki_internal_HidekiDecoder
 * Method:    create
 * Signature: (Lorg/openhab/binding/hideki/internal/HidekiDecoder;I)V
 */
JNIEXPORT void JNICALL Java_org_openhab_binding_hideki_internal_HidekiDecoder_create(JNIEnv*, jobject, jint);

/*
 * Class:     org_openhab_binding_hideki_internal_HidekiDecoder
 * Method:    destroy
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_org_openhab_binding_hideki_internal_HidekiDecoder_destroy(JNIEnv*, jobject);

/*
 * Class:     org_openhab_binding_hideki_internal_HidekiDecoder
 * Method:    start
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_org_openhab_binding_hideki_internal_HidekiDecoder_start(JNIEnv*, jobject);

/*
 * Class:     org_openhab_binding_hideki_internal_HidekiDecoder
 * Method:    stop
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_org_openhab_binding_hideki_internal_HidekiDecoder_stop(JNIEnv*, jobject);

/*
 * Class:     org_openhab_binding_hideki_internal_HidekiDecoder
 * Method:    getDecodedData
 * Signature: ()[I
 */
JNIEXPORT jintArray JNICALL Java_org_openhab_binding_hideki_internal_HidekiDecoder_getDecodedData(JNIEnv*, jobject);

/*
 * Class:     org_openhab_binding_hideki_internal_HidekiReceiver
 * Method:    create
 * Signature: (Lorg/openhab/binding/hideki/internal/HidekiReceiver/Kind;II)V
 */
JNIEXPORT void JNICALL Java_org_openhab_binding_hideki_internal_HidekiReceiver_create(JNIEnv*, jobject, jint, jint, jstring, jint);

/*
 * Class:     org_openhab_binding_hideki_internal_HidekiReceiver
 * Method:    destroy
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_org_openhab_binding_hideki_internal_HidekiReceiver_destroy(JNIEnv*, jobject);

/*
 * Class:     org_openhab_binding_hideki_internal_HidekiReceiver
 * Method:    setTimeOut
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_org_openhab_binding_hideki_internal_HidekiReceiver_setTimeOut(JNIEnv*, jobject, jint);

#ifdef __cplusplus
}
#endif

#endif
