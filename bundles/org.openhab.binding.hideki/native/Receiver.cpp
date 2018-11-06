#include "Receiver.h"

#include <gpiod.h>
#include <linux/gpio.h>
#include <sys/ioctl.h>
#include <cmath>

Receiver::Receiver(const int& pin)
  :mPin(pin),
   mTimeout(-1),
   mStopReceiverThread(false),
   mReceiverThreadIsAlive(false)
{
  mStartCondition = PTHREAD_COND_INITIALIZER;
  mStartMutex = PTHREAD_MUTEX_INITIALIZER;
}

Receiver::~Receiver()
{
  stop();
  pthread_mutex_destroy(&mStartMutex);
  pthread_cond_destroy(&mStartCondition);
  mPin = -1;
}

bool Receiver::start()
{
  if ((0 < mPin) && (mPin < 41) && !mReceiverThreadIsAlive) {
    mStopReceiverThread = false;
    if(pthread_create(&mReceiverThread, nullptr, receive, this) == 0) {
      pthread_mutex_lock(&mStartMutex);
      while(!mReceiverThreadIsAlive) {
        if(pthread_cond_wait(&mStartCondition, &mStartMutex) != 0) {
          mReceiverThreadIsAlive = false;
          break;
        }
      }
      pthread_mutex_unlock(&mStartMutex);
    }
  }
  return mReceiverThreadIsAlive;
}

bool Receiver::stop()
{
  if (mReceiverThreadIsAlive) {
    mStopReceiverThread = true;
    pthread_join(mReceiverThread, nullptr);
  }
  return !mReceiverThreadIsAlive;
}

void Receiver::setTimeout(const int& timeout)
{
  if (!mReceiverThreadIsAlive) {
    mTimeout = timeout;
  }
}

bool Receiver::getNextPulse(PulseDurationType& duration) const
{
  return mPulseData.try_dequeue(duration);
}

void* Receiver::receive(void* parameter)
{
  Receiver* receiver = reinterpret_cast<Receiver*>(parameter);

  static char buffer[FILENAME_MAX] = { '\0' };
  pthread_mutex_lock(&receiver->mStartMutex);

  struct gpiod_line *line = nullptr;
  struct gpiod_chip *chip = gpiod_chip_open("/dev/gpiochip0");
  if (chip != nullptr) {
    line  = gpiod_chip_get_line(chip, receiver->mPin);
  } else {
    snprintf(buffer, sizeof(buffer), "Unable to open gpio device for pin %d", receiver->mPin.load());
    perror(buffer);
  }

  if (line != nullptr) {
    if (gpiod_line_request_both_edges_events(line, "hideki") != 0) {
      line = nullptr;
      snprintf(buffer, sizeof(buffer), "Unable to request event handling for pin %d", receiver->mPin.load());
      perror(buffer);
    }
  } else {
    snprintf(buffer, sizeof(buffer), "Unable to open gpio line for pin %d", receiver->mPin.load());
    perror(buffer);
  }

  if ((chip != nullptr) && (line == nullptr)) {
    gpiod_chip_close(chip);
    chip = nullptr;
  }

  receiver->mReceiverThreadIsAlive = true;
  pthread_cond_signal(&receiver->mStartCondition);
  pthread_mutex_unlock(&receiver->mStartMutex);

  // Start receiver
  while (!receiver->mStopReceiverThread && (chip != nullptr)) {
    PulseDurationType duration = 0;

    static struct timespec tOld;
    clock_gettime(CLOCK_REALTIME, &tOld);

    static struct timespec timeout = {};
    timeout.tv_sec = receiver->mTimeout.load() / 1000,
    timeout.tv_nsec = (receiver->mTimeout.load() - timeout.tv_sec * 1000) * 1000000;

    int result = 0;
    do {
      result = gpiod_line_event_wait(line, receiver->mTimeout.load() < 0 ? nullptr : &timeout);
    } while (result <=0);
    
    struct gpiod_line_event event;
    result = gpiod_line_event_read(line, &event);
    
    static struct timespec tNew;
    clock_gettime(CLOCK_REALTIME, &tNew);

    struct timespec diff = {
      .tv_sec = tNew.tv_sec - tOld.tv_sec,
      .tv_nsec = tNew.tv_nsec - tOld.tv_nsec
    };
    if (diff.tv_nsec < 0) {
      diff.tv_sec  -= 1;
      diff.tv_nsec += 1000000000;
    }
    // Calculate pulse length in microseconds
    duration = std::round(diff.tv_sec * 1000000.0 + diff.tv_nsec / 1000.0);

    if (duration > 20) { // Filter pulses shorter than 20 microseconds
      receiver->mPulseData.enqueue(duration);
    }
  }

  if (chip != nullptr) {
    gpiod_chip_close(chip);
    chip = nullptr;
  }
  receiver->mReceiverThreadIsAlive = false;

  return nullptr;
}
