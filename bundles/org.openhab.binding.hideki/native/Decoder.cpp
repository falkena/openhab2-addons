#include "Decoder.h"
#include "Receiver.h"

#include <unistd.h>

#include <cstring>
#include <iostream>

Decoder::Decoder(const Receiver* receiver)
  :mReceiver(const_cast<Receiver*>(receiver)),
   mFetchNewDataLock(PTHREAD_RWLOCK_INITIALIZER),
   mStartCondition(PTHREAD_COND_INITIALIZER),
   mStartMutex(PTHREAD_MUTEX_INITIALIZER),
   mReceivedNewData(false),
   mStopDecoderThread(false),
   mDecoderThreadIsAlive(false)
{
}


Decoder::~Decoder()
{
  stop();
  pthread_mutex_destroy(&mStartMutex);
  pthread_cond_destroy(&mStartCondition);
  pthread_rwlock_destroy(&mFetchNewDataLock);

  mReceiver = nullptr;
}

/* Start decoder on pin
* Result: 0 = O.K., -1 = Error
*/
bool Decoder::start()
{
  std::memset(&mReceivedData, 0, sizeof(decltype(mReceivedData)));

  if (!mDecoderThreadIsAlive && mReceiver->start()) {
    if(pthread_create(&mDecoderThread, nullptr, decode, this) == 0) {
      pthread_mutex_lock(&mStartMutex);
      while(!mDecoderThreadIsAlive) {
        if(pthread_cond_wait(&mStartCondition, &mStartMutex) != 0) {
          mDecoderThreadIsAlive = false;
          break;
        }
      }
      pthread_mutex_unlock(&mStartMutex);
    }
  }

  return mDecoderThreadIsAlive;
}

/* Stop decoder on pin
* Result: 0 = O.K., -1 = Error
*/
bool Decoder::stop()
{
  if (mDecoderThreadIsAlive && mReceiver->stop()) {
    mStopDecoderThread = true;
    pthread_join(mDecoderThread, nullptr);
  }

  std::memset(&mReceivedData, 0, sizeof(decltype(mReceivedData)));
  return !mDecoderThreadIsAlive;
}

int Decoder::getDecodedData(std::array<uint8_t, DATA_BUFFER_LENGTH>& data, double& rssi)
{
  int length = -1;

  pthread_rwlock_rdlock(&mFetchNewDataLock);
  if (mReceivedNewData) {
    data = mReceivedData.data;
    length = (data[2] >> 1) & 0x1F;
    rssi = mReceivedData.rssi.value;
    if(mReceivedData.rssi.count > 0) {
      rssi /= mReceivedData.rssi.count;
    }
    pthread_rwlock_unlock(&mFetchNewDataLock);

    pthread_rwlock_wrlock(&mFetchNewDataLock);
    mReceivedNewData = false;
    std::memset(&mReceivedData, 0, sizeof(decltype(mReceivedData)));
  }
  pthread_rwlock_unlock(&mFetchNewDataLock);

  return length + 1;
}

// Set limits according to
// http://jeelabs.org/2010/04/16/cresta-sensor/index.html
// http://jeelabs.org/2010/04/17/improved-ook-scope/index.html
static constexpr Receiver::PulseDurationType LOW_TIME = 183; //200;    // Minimal short pulse length in microseconds
static constexpr Receiver::PulseDurationType MID_TIME = 726; //750;    // Maximal short / Minimal long pulse length in microseconds
static constexpr Receiver::PulseDurationType HIGH_TIME = 1464; //1300; // Maximal long pulse length in microseconds
void* Decoder::decode(void* parameter)
{
  Decoder* decoder = reinterpret_cast<Decoder*>(parameter);

  pthread_mutex_lock(&decoder->mStartMutex);
  static int count = 0; // Current bit count
  static uint32_t value = 0; // Received byte + parity value

  decoder->mDecoderThreadIsAlive = true;
  pthread_cond_signal(&decoder->mStartCondition);
  pthread_mutex_unlock(&decoder->mStartMutex);

  // Start decoder
  while (!decoder->mStopDecoderThread) {
    auto duration = std::numeric_limits<Receiver::PulseDurationType>::max();
    if(!decoder->mReceiver->getNextPulse(duration)) {
      usleep(1000); // Sleep for 1 millisecond
      continue;
    }

    bool reset = true;
    static int halfBit = 0; // Indicator for received half bit
    if ((MID_TIME <= duration) && (duration < HIGH_TIME)) { // The pulse was long --> Got 1
      value = value + 1;
      value = value << 1;
      count = count + 1;
      reset = false;
      halfBit = 0;
    } else if ((LOW_TIME <= duration) && (duration < MID_TIME)) { // The pulse was short --> Got 0?
      if (halfBit == 1) { // Two short pulses one after the other --> Got 0
        value = value + 0;
        value = value << 1;
        count = count + 1;
      }
      reset = false;
      halfBit = (halfBit + 1) % 2;
    }

    static uint32_t byte = 0;
    static struct ReceivedData buffer = { .rssi = { 0.0, 0 }, .data = { 0 } };
    std::size_t length = buffer.data.size() + 1;
    if ((byte > 2) && !reset) {
      length = (buffer.data[2] >> 1) & 0x1F;
      if (length > buffer.data.size() - 1) {
        reset = true;
      }
    }

    // Last byte has 8 bits only. No parity will be read
    // Fake parity bit to pass next step
    if ((byte == length + 2) && (count == 8) && !reset)
    {
      count = count + 1;
      value = __builtin_parity(value) + (value << 1);
    }

    if ((count == 9) && !reset) {
      value = value >> 1; // We made one shift more than need. Shift back.
      if (__builtin_parity(value >> 1) == value % 2) {
        buffer.data[byte] = static_cast<decltype(buffer.data)::value_type>((value >> 1) & 0xFF);
        buffer.data[byte] = ((buffer.data[byte] & 0xAA) >> 1) | ((buffer.data[byte] & 0x55) << 1);
        buffer.data[byte] = ((buffer.data[byte] & 0xCC) >> 2) | ((buffer.data[byte] & 0x33) << 2);
        buffer.data[byte] = ((buffer.data[byte] & 0xF0) >> 4) | ((buffer.data[byte] & 0x0F) << 4);

        if (buffer.data[0] == 0x9F) {
          byte = byte + 1;
          buffer.rssi.count += 1;
          buffer.rssi.value += decoder->mReceiver->getRSSIValue();
        } else {
          reset = true;
        }

        if ((byte > 2) && !reset) {
          length = (buffer.data[2] >> 1) & 0x1F;
          if (length > buffer.data.size() - 1) {
            reset = true;
          }
        }

        if ((byte > length + 1) && !reset) {
          uint32_t crc1 = 0;
          for (uint8_t i = 1; i < length + 1; ++i) {
            crc1 = crc1 ^ buffer.data[i];
          }
          if (crc1 != buffer.data[length + 1]) {
            reset = true;
          }
        }

        if ((byte > length + 2) && !reset) {
          uint32_t crc2 = 0;
          for (uint8_t i = 1; i < length + 2; ++i) {
            crc2 = crc2 ^ buffer.data[i];
            for (uint8_t j = 0; j < 8; ++j) {
              if ((crc2 & 0x01) != 0) {
                crc2 = (crc2 >> 1) ^ 0xE0;
              } else {
                crc2 = (crc2 >> 1);
              }
            }
          }

          if (crc2 == buffer.data[length + 2]) {
            pthread_rwlock_wrlock(&decoder->mFetchNewDataLock);
            decoder->mReceivedNewData = true;
            std::memcpy(&decoder->mReceivedData, &buffer, sizeof(decltype(buffer)));
            pthread_rwlock_unlock(&decoder->mFetchNewDataLock);
          }
          reset = true;
        }
      }
      count = 0;
      value = 0;
      halfBit = 0;
    }

    if (reset) { // Reset if failed or got valid data
      byte = 0;
      count = 0;
      value = 0;
      halfBit = 0;
      std::memset(&buffer, 0, sizeof(decltype(buffer)));
    }
  }
  decoder->mDecoderThreadIsAlive = false;

  return nullptr;
}
