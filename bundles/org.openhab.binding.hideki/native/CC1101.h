#pragma once

#include "Receiver.h"

#include <inttypes.h>
#include <string>

class CC1101 final : public Receiver {
  public:
    CC1101(const int& pin, std::string device, const int& interrupt);
    ~CC1101() override;

    State getState() const override;
    double getRSSIValue() const override;

    uint8_t getStateCode() const;

  private:
    static constexpr uint32_t READ_BYTE = 0x80;
    static constexpr uint32_t WRITE_BYTE = 0x00;
    static constexpr uint32_t READ_BURST = 0xC0;
    static constexpr uint32_t WRITE_BURST = 0x40;

    CC1101() = delete;
    CC1101(const CC1101& other) = delete;
    CC1101& operator=(const CC1101& other) = delete;

    void close();
    int transfer(uint8_t* data, const uint32_t& length) const;

    int mSpiDevice;
};
