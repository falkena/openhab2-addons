#pragma once

#include <inttypes.h>
#include <pthread.h>

#include <array>
#include <atomic>

class Receiver;

class Decoder
{
  public:
    static constexpr std::size_t DATA_BUFFER_LENGTH = 15;

    Decoder(const Receiver* receiver);
    virtual ~Decoder();

    virtual bool start();
    virtual bool stop();

    int getDecodedData(std::array<uint8_t, DATA_BUFFER_LENGTH>& data, double& rssi);

  private:
    Decoder() = delete;
    Decoder(const Decoder& other) = delete;
    Decoder& operator=(const Decoder& other) = delete;

    Receiver* mReceiver;

    pthread_rwlock_t mFetchNewDataLock;
    volatile bool mReceivedNewData;
    struct ReceivedData {
      struct RSSI {
        double value;
        uint32_t count;
      } rssi;
      std::array<uint8_t, DATA_BUFFER_LENGTH> data;
    } mReceivedData;
    static void* decode(void* parameter);

    pthread_t mDecoderThread;
    pthread_cond_t mStartCondition;
    pthread_mutex_t mStartMutex;

    std::atomic<bool> mStopDecoderThread;
    std::atomic<bool> mDecoderThreadIsAlive;
};
