/*
 * Module for receiving and decoding of wireless weather station
 * sensor data (433MHz). Protocol used by Cresta/Irox/Mebus/Nexus/
 * Honeywell/Hideki/TFA weather stations.
 * 
 * Protocol was reverse engineered and documented by Ruud v Gessel
 * in "Cresta weather sensor protocol", see
 * http://members.upc.nl/m.beukelaar/Crestaprotocol.pdf
 *
 * Future work was done by Rory O’Hare and documented in
 * "Blind Reverse Engineering a Wireless Protocol", see 
 * https://github.com/r-ohare/Amateur-SIGINT
 *
 * This module utilizes code of the atMETEO Project, see
 * https://github.com/fetzerch/atMETEO
 *
 * License: GPLv3. See license.txt
 */

#include "HidekiJniWrapper.h"
 
#include "Decoder.h"
#include "Receiver.h"
#include "CC1101.h"
#include "RXB.h"

#include <jni.h>

#include <array>
#include <cmath>
#include <map>

#include <syslog.h>

std::map<jint, Decoder*> DecoderMap;
std::map<jint, Receiver*> ReceiverMap;

void JNICALL Java_org_openhab_binding_hideki_internal_HidekiDecoder_create(JNIEnv* env, jobject object, jint receiver)
{
  env->MonitorEnter(object);
  jmethodID getId = env->GetMethodID(env->GetObjectClass(object), "getId", "()I");
  if (getId != nullptr) {
    jint id = env->CallIntMethod(object, getId);
//    syslog(LOG_INFO, "Create Decoder: %X, Id %d, with receiver %d\n", object, id, receiver);
    decltype(ReceiverMap)::iterator it = ReceiverMap.find(receiver);
    if ((DecoderMap.find(id) == DecoderMap.end()) && (it != ReceiverMap.end())) {
      Decoder* decoder = new Decoder(it->second);
      if (!DecoderMap.emplace(id, decoder).second) {
        delete decoder;
        decoder = nullptr;
      }
    }
  }
  env->MonitorExit(object);
}

void JNICALL Java_org_openhab_binding_hideki_internal_HidekiDecoder_destroy(JNIEnv* env, jobject object)
{
  env->MonitorEnter(object);
  jmethodID getId = env->GetMethodID(env->GetObjectClass(object), "getId", "()I");
  if (getId != nullptr) {
    jint id = env->CallIntMethod(object, getId);
//    syslog(LOG_INFO, "Destroy Decoder: %X, Id %d\n", object, id);
    decltype(DecoderMap)::iterator it = DecoderMap.find(id);
    if (it != DecoderMap.end()) {
      if(it->second != nullptr) {
        it->second->stop();
        delete it->second;
        it->second = nullptr;
      }
      DecoderMap.erase(it);
    }
  }
  env->MonitorExit(object);
}

jboolean JNICALL Java_org_openhab_binding_hideki_internal_HidekiDecoder_start(JNIEnv* env, jobject object)
{
  jboolean result = false;
  
  env->MonitorEnter(object);
  jmethodID getId = env->GetMethodID(env->GetObjectClass(object), "getId", "()I");
  if (getId != nullptr) {
    jint id = env->CallIntMethod(object, getId);
//    syslog(LOG_INFO, "Start Decoder: %X, Id %d\n", object, id);
    decltype(DecoderMap)::iterator it = DecoderMap.find(id);
    if ((it != DecoderMap.end()) && (it->second != nullptr)) {
      result = it->second->start();
    }
  }
  env->MonitorExit(object);

  return result;
}

jboolean JNICALL Java_org_openhab_binding_hideki_internal_HidekiDecoder_stop(JNIEnv* env, jobject object)
{
  jboolean result = false;
  
  env->MonitorEnter(object);
  jmethodID getId = env->GetMethodID(env->GetObjectClass(object), "getId", "()I");
  if (getId != nullptr) {
    jint id = env->CallIntMethod(object, getId);
//    syslog(LOG_INFO, "Stop Decoder: %X, Id %d\n", object, id);
    decltype(DecoderMap)::iterator it = DecoderMap.find(id);
    if ((it != DecoderMap.end()) && (it->second != nullptr)) {
      result = it->second->stop();
    }
  }
  env->MonitorExit(object);

  return result;
}

jintArray JNICALL Java_org_openhab_binding_hideki_internal_HidekiDecoder_getDecodedData(JNIEnv* env, jobject object)
{
  jintArray result = nullptr;

  env->MonitorEnter(object);
  jmethodID getId = env->GetMethodID(env->GetObjectClass(object), "getId", "()I");
  if (getId != nullptr) {
    jint id = env->CallIntMethod(object, getId);
//    syslog(LOG_INFO, "Get Decoder data: %X, Id %d\n", object, id);
    decltype(DecoderMap)::iterator it = DecoderMap.find(id);
    if ((it != DecoderMap.end()) && (it->second != nullptr)) {
      double rssi = 0.0;
      std::array<uint8_t, Decoder::DATA_BUFFER_LENGTH> data;
      jint length = it->second->getDecodedData(data, rssi);
      if (length > 0) {
        result = env->NewIntArray(length + 1);
        jint* buffer = env->GetIntArrayElements(result, nullptr);
        for (jint i = 0; i < length; i++) {
          buffer[i] = static_cast<jint>(data[i]);
        }
        buffer[length] = static_cast<jint>(std::round(rssi));
        env->ReleaseIntArrayElements(result, buffer, 0);
//        syslog(LOG_INFO, " Received: %d bytes\n", length);
      }
    }
  }
  env->MonitorExit(object);

  return result;
}

void JNICALL Java_org_openhab_binding_hideki_internal_HidekiReceiver_create(JNIEnv* env, jobject object, jint kind, jint pin, jstring device, jint interrupt)
{
  env->MonitorEnter(object);
  jmethodID getId = env->GetMethodID(env->GetObjectClass(object), "getId", "()I");
  if (getId != nullptr) {
    jint id = env->CallIntMethod(object, getId);
//    syslog(LOG_INFO, "Create Receiver: %X, Id %d\n", object, id);
    if (ReceiverMap.find(id) == ReceiverMap.end()) {
      switch(kind) {
        case 0: {
          RXB* receiver = new RXB(pin);
          if (!ReceiverMap.emplace(id, receiver).second) {
            delete receiver;
            receiver = nullptr;
          }
          break;
        }
        case 1: {
          const char *buffer = env->GetStringUTFChars(device, nullptr);
          CC1101* receiver = new CC1101(pin, std::string(buffer), interrupt);
          if (!ReceiverMap.emplace(id, receiver).second) {
            delete receiver;
            receiver = nullptr;
          }
          env->ReleaseStringUTFChars(device, buffer);
          break;
        }
        default: {
          break;
        }
      }
    }
  }
  env->MonitorExit(object);
}

void JNICALL Java_org_openhab_binding_hideki_internal_HidekiReceiver_destroy(JNIEnv* env, jobject object)
{
  env->MonitorEnter(object);
  jmethodID getId = env->GetMethodID(env->GetObjectClass(object), "getId", "()I");
  if (getId != nullptr) {
    jint id = env->CallIntMethod(object, getId);
//    syslog(LOG_INFO, "Destroy Receiver: %X, Id %d\n", object, id);
    decltype(ReceiverMap)::iterator it = ReceiverMap.find(id);
    if (it != ReceiverMap.end()) {
      if (it->second != nullptr) {
        delete it->second;
        it->second = nullptr;
      }
      ReceiverMap.erase(it);
    }
  }
  env->MonitorExit(object);
}

void JNICALL Java_org_openhab_binding_hideki_internal_HidekiReceiver_setTimeOut(JNIEnv* env, jobject object, jint timeout)
{
  env->MonitorEnter(object);
  jmethodID getId = env->GetMethodID(env->GetObjectClass(object), "getId", "()I");
  if (getId != nullptr) {
    jint id = env->CallIntMethod(object, getId);
//    syslog(LOG_INFO, "Set timeout: %X, Id %d\n", object, id);
    decltype(ReceiverMap)::iterator it = ReceiverMap.find(id);
    if ((it != ReceiverMap.end()) && (it->second != nullptr)) {
      it->second->setTimeout(timeout);
    }
  }
  env->MonitorExit(object);
}
