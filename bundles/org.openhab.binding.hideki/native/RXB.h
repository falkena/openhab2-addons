#pragma once

#include "Receiver.h"

class RXB final : public Receiver {
  public:
    RXB(const int& pin);
    ~RXB() override;

    State getState() const override;
    double getRSSIValue() const override;

  private:
    RXB() = delete;
    RXB(const RXB& other) = delete;
    RXB& operator=(const RXB& other) = delete;
};
