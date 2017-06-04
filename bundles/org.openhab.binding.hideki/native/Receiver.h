#pragma once

#include "concurrentqueue.h"

#include <inttypes.h>
#include <pthread.h>

#include <atomic>

class Receiver {
  public:
    using PulseDurationType = decltype(timespec::tv_sec);
    enum class State : uint8_t {
      ERROR,
      INITIALIZED
    };

    Receiver(const int& pin);
    virtual ~Receiver();

    virtual bool start();
    virtual bool stop();

    virtual State getState() const = 0;
    virtual void setTimeout(const int& timeout);

    virtual double getRSSIValue() const = 0;
    bool getNextPulse(PulseDurationType& duration) const;

  private:
    Receiver() = delete;
    Receiver(const Receiver& other) = delete;
    Receiver& operator=(const Receiver& other) = delete;

    std::atomic<int> mPin;
    std::atomic<int> mTimeout;

    pthread_t mReceiverThread;
    pthread_cond_t mStartCondition;
    pthread_mutex_t mStartMutex;

    std::atomic<bool> mStopReceiverThread;
    std::atomic<bool> mReceiverThreadIsAlive;
    mutable moodycamel::ConcurrentQueue<PulseDurationType> mPulseData;

    static void* receive(void* parameter);
};
