#include "GPIO.h"

#include <ctype.h>
#include <fcntl.h>
#include <math.h>
#include <poll.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <sys/stat.h>

#include <type_traits>
#include <string>

static constexpr useconds_t TIME_FOR_WAIT = 1000; // 1 millisecond

int GPIO::enable(const int& pin, Direction direction, Edge edge)
{
  int result = 0;
  static struct stat state;
  static char buffer[FILENAME_MAX] = { '\0' };

  snprintf(buffer, sizeof(buffer), "/sys/class/gpio/gpio%d", pin);
  if (stat(buffer, &state) != 0) {
    int fd = open("/sys/class/gpio/export", O_WRONLY | O_SYNC);
    if (fd >= 0) {
      int bytes = snprintf(buffer, sizeof(buffer), "%d", pin);
      if (::write(fd, buffer, bytes) < 0) {
        snprintf(buffer, sizeof(buffer), "Can not activate pin %d", pin);
        result = -1;
      }
      close(fd);
    } else {
      snprintf(buffer, sizeof(buffer), "Can not export pin %d", pin);
      result = -1;
    }
  }

  if (result == 0) {
    snprintf(buffer, sizeof(buffer), "/sys/class/gpio/gpio%d/direction", pin);
    while ((stat(buffer, &state) != 0) || (access(buffer, W_OK) != 0)) {
      usleep(TIME_FOR_WAIT);
    }

    int fd = open(buffer, O_WRONLY | O_SYNC);
    if (fd >= 0) {
      static const char* DIRECTIONS[] = { "in", "out" };
      auto index = static_cast<std::underlying_type<decltype(direction)>::type>(direction);
      if (::write(fd, DIRECTIONS[index], strlen(DIRECTIONS[index])) < 0) {
        snprintf(buffer, sizeof(buffer), "Can not set direction of pin %d", pin);
        result = -1;
      }
      close(fd);
    } else {
      snprintf(buffer, sizeof(buffer), "Can not open direction file for pin %d", pin);
      result = -1;
    }
  }

  if (result == 0) {
    snprintf(buffer, sizeof(buffer), "/sys/class/gpio/gpio%d/edge", pin);
    while ((stat(buffer, &state) != 0) || (access(buffer, W_OK) != 0)) {
      usleep(TIME_FOR_WAIT);
    }

    int fd = open(buffer, O_WRONLY | O_SYNC);
    if (fd >= 0) {
      static const char* EDGES[] = { "none", "rising", "falling", "both" };
      auto index = static_cast<std::underlying_type<decltype(edge)>::type>(edge);
      if (::write(fd, EDGES[index], strlen(EDGES[index])) < 0) {
        snprintf(buffer, sizeof(buffer), "Can not set edge of pin %d", pin);
        result = -1;
      }
      close(fd);
    } else {
      snprintf(buffer, sizeof(buffer), "Can not open edge file for pin %d", pin);
      result = -1;
    }
  }

  if (result == 0) {
    const bool read = (direction == GPIO::Direction::IN);
    snprintf(buffer, sizeof(buffer), "/sys/class/gpio/gpio%d/value", pin);
    do {
      usleep(TIME_FOR_WAIT);
    } while ((stat(buffer, &state) != 0) || (access(buffer, read ? R_OK : W_OK) != 0));
  }

  if (result < 0) {
    perror(buffer);
  }

  return result;
}

int GPIO::disable(const int& pin)
{
  int result = 0;
  static struct stat state;
  static char buffer[FILENAME_MAX] = { '\0' };

  snprintf(buffer, sizeof(buffer), "/sys/class/gpio/gpio%d", pin);
  if (stat(buffer, &state) == 0) {
    if (S_ISDIR(state.st_mode) || S_ISLNK(state.st_mode)) {
      int fd = open("/sys/class/gpio/unexport", O_WRONLY | O_SYNC);
      if (fd >= 0) {
        int bytes = snprintf(buffer, sizeof(buffer), "%d", pin);
        if (::write(fd, buffer, bytes) < 0) {
          snprintf(buffer, sizeof(buffer), "Can not deactivate pin %d", pin);
          result = -1;
        }
        close(fd);
        if(result == 0) {
          snprintf(buffer, sizeof(buffer), "/sys/class/gpio/gpio%d", pin);
          do {
            usleep(TIME_FOR_WAIT);
          } while ((stat(buffer, &state) == 0) || (access(buffer, F_OK) == 0));
        }
      } else {
        snprintf(buffer, sizeof(buffer), "Can not unexport pin %d", pin);
        result = -1;
      }
    }
  }

  if (result < 0) {
    perror(buffer);
  }

  return result;
}

uint8_t GPIO::read(const int& pin, const int& timeout, unsigned int& duration)
{
  uint8_t result = 0xFF;  // Assume, we get error...
  static char buffer[FILENAME_MAX] = { '\0' };

  // Prepare interrupt struct
  snprintf(buffer, FILENAME_MAX, "/sys/class/gpio/gpio%d/value", pin);
  struct pollfd polldat = { 0 };
  polldat.fd = open(buffer, O_RDONLY | O_SYNC);
  polldat.events = POLLPRI | POLLERR;
  duration = timeout;

  if (polldat.fd >= 0) {
    memset(buffer, 0, sizeof(buffer));
    ::read(polldat.fd, buffer, sizeof(buffer));

    static struct timespec tOld;
    clock_gettime(CLOCK_REALTIME, &tOld);
    int pc = poll(&polldat, 1, timeout);
    static struct timespec tNew;
    clock_gettime(CLOCK_REALTIME, &tNew);

    if (pc > 0) {
      if (polldat.revents & POLLPRI) {
        memset(buffer, 0, sizeof(buffer));
        lseek(polldat.fd, 0, SEEK_SET);
        if (::read(polldat.fd, buffer, sizeof(buffer)) >= 0) {
          result = static_cast<uint8_t>(atoi(buffer));
          duration = round((tNew.tv_nsec - tOld.tv_nsec) / 1000.0); // Pulse lenght in microseconds
        } else {  // read() failed
          snprintf(buffer, sizeof(buffer), "Can not read from pin %d", pin);
        }
      }
    } else { // poll() failed or timeout!
      snprintf(buffer, sizeof(buffer), "Call of poll on pin %d failed", pin);
    }
    close(polldat.fd);
  } else {
    snprintf(buffer, sizeof(buffer), "Can not open pin %d for read", pin);
  }

  if (result == 0xFF) {
    perror(buffer);
  }

  return result;
}

int GPIO::write(const int& pin, const uint8_t& value)
{
  int result = 0;
  static char buffer[FILENAME_MAX] = { '\0' };

  snprintf(buffer, FILENAME_MAX, "/sys/class/gpio/gpio%d/value", pin);
  int fd = open(buffer, O_WRONLY | O_SYNC);
  if (fd >= 0) {
    int bytes = snprintf(buffer, sizeof(buffer), "%d", value);
    if (::write(fd, buffer, bytes) < 0) {
      snprintf(buffer, sizeof(buffer), "Can not write to pin %d", pin);
      result = -1;
    }
    close(fd);
  } else {
    snprintf(buffer, sizeof(buffer), "Can not open pin %d for write", pin);
    result = -1;
  }

  if (result < 0) {
    perror(buffer);
  }

  return result;
}
