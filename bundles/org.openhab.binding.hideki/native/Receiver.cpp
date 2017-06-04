#include "Receiver.h"
#include "GPIO.h"

#include <fcntl.h>
#include <math.h>
#include <poll.h>
#include <unistd.h>

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

  pthread_mutex_lock(&receiver->mStartMutex);
  struct pollfd polldat = { .fd = -1, .events = POLLPRI | POLLERR, .revents = 0 };
  if (GPIO::enable(receiver->mPin, GPIO::Direction::IN, GPIO::Edge::BOTH) == 0) {
    static char file[FILENAME_MAX] = { '\0' }; // Received data
    snprintf(file, FILENAME_MAX, "/sys/class/gpio/gpio%d/value", receiver->mPin.load());
    polldat.fd = ::open(file, O_RDONLY | O_SYNC);
  }

  receiver->mReceiverThreadIsAlive = true;
  pthread_cond_signal(&receiver->mStartCondition);
  pthread_mutex_unlock(&receiver->mStartMutex);

  // Start receiver
  while (!receiver->mStopReceiverThread && (polldat.fd >= 0)) {
    PulseDurationType duration = 0;
    if (lseek(polldat.fd, 0, SEEK_SET) >= 0) { // Catch next edge time
      static struct timespec tOld;
      clock_gettime(CLOCK_REALTIME, &tOld);
      int pc = poll(&polldat, 1, receiver->mTimeout);
      static struct timespec tNew;
      clock_gettime(CLOCK_REALTIME, &tNew);

      if ((pc > 0) && (polldat.revents & POLLPRI)) {
        static uint8_t value = 0;
        if (read(polldat.fd, &value, 1) >= 0) {
          struct timespec diff = {
            .tv_sec = tNew.tv_sec - tOld.tv_sec,
            .tv_nsec = tNew.tv_nsec - tOld.tv_nsec
          };
          if (diff.tv_nsec < 0) {
            diff.tv_sec  -= 1;
            diff.tv_nsec += 1000000000;
          }
          // Calculate pulse length in microseconds
          duration = round(diff.tv_sec * 1000000.0 + diff.tv_nsec / 1000.0);
        }
      }
    }

    if (duration > 20) { // Filter pulses shorter than 20 microseconds
      receiver->mPulseData.enqueue(duration);
    }
  }

  if (polldat.fd >= 0) {
    close(polldat.fd);
    polldat.fd = -1;
  }
  GPIO::disable(receiver->mPin);
  receiver->mReceiverThreadIsAlive = false;

  return nullptr;
}
