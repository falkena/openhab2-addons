#pragma once

#include <cstdint>

namespace GPIO
{
  enum class Direction : uint8_t {
    IN = 0,
    OUT = 1
  };

  enum class Edge : uint8_t {
    NONE = 0,
    RISING = 1,
    FALLING = 2,
    BOTH = 3
  };

  /* Activate GPIO-Pin
   * Write pin number to /sys/class/gpio/export
   * Result: 0 = O.K., -1 = Error
   */
  int enable(const int& pin, Direction direction, Edge edge = Edge::NONE);

  /* Deactivate GPIO-Pin
   * Write pin number to /sys/class/gpio/unexport
   * Result: 0 = O.K., -1 = Error
   */
  int disable(const int& pin);

  /* Read value from pin
   * Timeout timeout: maximal time to wait for interrupt
   * Duration duration: detected length of pulse. Is timeout, if failed
   * Result: Read value. Is 0xFF, if failed
   */
  uint8_t read(const int& pin, const int& timeout, unsigned int& duration);

  /* Write value to pin
   * Result: 0 = O.K., -1 = Error
   */
  int write(const int& pin, const uint8_t& value);
}
