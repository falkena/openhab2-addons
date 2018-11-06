#include "CC1101.h"

#include <fcntl.h>
#include <stdio.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <linux/spi/spidev.h>

#include <algorithm>
#include <array>
#include <cstring>
#include <iostream>
#include <limits>

CC1101::CC1101(const int& pin, std::string device, const int& interrupt)
  :Receiver(pin),
   mSpiDevice(-1)
{
  device.erase(device.begin(), std::find_if(device.begin(), device.end(), [](int ch) {
    return !std::isspace(ch);
  }));
  device.erase(std::find_if(device.rbegin(), device.rend(), [](int ch) {
    return !std::isspace(ch);
  }).base(), device.end());
  if (!device.empty() && ((interrupt == 0) || (interrupt == 2))) {
    mSpiDevice = ::open(device.c_str(), O_RDWR);
  }

  if (mSpiDevice >= 0) {
    static constexpr int mode = SPI_MODE_0;
    if (ioctl(mSpiDevice, SPI_IOC_WR_MODE, &mode) < 0) {
      perror("Unable to set SPI mode");
      close();
    }
  } else {
    perror("Unable to open SPI device");
  }

  if (mSpiDevice >= 0) {
    static constexpr int bits = 8;
    if (ioctl(mSpiDevice, SPI_IOC_WR_BITS_PER_WORD, &bits) < 0) {
      perror("Unable to set SPI bit count");
      close();
    }
  }

  if (mSpiDevice >= 0) {
    static constexpr int speed = 500000;
    if (ioctl(mSpiDevice, SPI_IOC_WR_MAX_SPEED_HZ, &speed) < 0) {
      perror("Unable to set SPI speed");
      close();
    }
  }

  // Reset CC1101
  if (mSpiDevice >= 0) {
    std::array<uint8_t, 1> buffer = { 0x30 | WRITE_BYTE}; // Reset chip
    if (transfer(buffer.data(), buffer.size()) < 0) {
      perror("Unable to reset transmitter");
      close();
    }
    sleep(1);
  }

  if (mSpiDevice >= 0) {
    // http://www.ti.com/lit/ds/symlink/cc1101.pdf
    // http://www.ti.com/lit/an/swra215e/swra215e.pdf
    // Settings: 433.92 MHz base frequency 6.00052 kBaud data rate
    //           325kHz bandwidth, 350kHz channel spacing, 152.34kHz IF frequency
    std::array<uint8_t, 48> buffer = { 0x00 | WRITE_BURST,
      0x2E,  // IOCFG2        High-Impedance - GDO2 is not connected
      0x2E,  // IOCFG1        High-Impedance - GDO1 is also used as the SPI output from the CC1101
      0x0D,  // IOCFG0        GDO0 Output Pin Configuration
      0x47,  // FIFOTHR       RX FIFO and TX FIFO Thresholds
      0xD3,  // SYNC1         Sync Word, High Byte
      0x91,  // SYNC0         Sync Word, Low Byte
      0xFF,  // PKTLEN        Packet Length
      0x04,  // PKTCTRL1      Packet Automation Control
      0x32,  // PKTCTRL0      Packet Automation Control
      0x00,  // ADDR          Device Address
      0x00,  // CHANNR        Channel Number
      0x06,  // FSCTRL1       Frequency Synthesizer Control
      0x00,  // FSCTRL0       Frequency Synthesizer Control
      0x10,  // FREQ2         Frequency Control Word, High Byte
      0xB0,  // FREQ1         Frequency Control Word, Middle Byte
      0x72,  // FREQ0         Frequency Control Word, Low Byte
      0x57,  // MDMCFG4       Modem Configuration
      0xE4,  // MDMCFG3       Modem Configuration
      0x30,  // MDMCFG2       Modem Configuration
      0x23,  // MDMCFG1       Modem Configuration
      0xB9,  // MDMCFG0       Modem Configuration
      0x15,  // DEVIATN       Modem Deviation Setting
      0x07,  // MCSM2         Main Radio Control State Machine Configuration
      0x3C,  // MCSM1         Main Radio Control State Machine Configuration
      0x18,  // MCSM0         Main Radio Control State Machine Configuration
      0x16,  // FOCCFG        Frequency Offset Compensation Configuration
      0x6C,  // BSCFG         Bit Synchronization Configuration
      0x07,  // AGCCTRL2      AGC Control
      0x00,  // AGCCTRL1      AGC Control
      0x92,  // AGCCTRL0      AGC Control
      0x87,  // WOREVT1       High Byte Event0 Timeout
      0x6B,  // WOREVT0       Low Byte Event0 Timeout
      0xFB,  // WORCTRL       Wake On Radio Control
      0xB6,  // FREND1        Front End RX Configuration
      0x11,  // FREND0        Front End TX Configuration
      0xE9,  // FSCAL3        Frequency Synthesizer Calibration
      0x2A,  // FSCAL2        Frequency Synthesizer Calibration
      0x00,  // FSCAL1        Frequency Synthesizer Calibration
      0x1F,  // FSCAL0        Frequency Synthesizer Calibration
      0x41,  // RCCTRL1       RC Oscillator Configuration
      0x00,  // RCCTRL0       RC Oscillator Configuration
      0x59,  // FSTEST        Frequency Synthesizer Calibration Control
      0x7F,  // PTEST         Production Test
      0x3F,  // AGCTEST       AGC Test
      0x81,  // TEST2         Various Test Settings
      0x35,  // TEST1         Various Test Settings
      0x09   // TEST0         Various Test Settings
    };

    if (interrupt == 2) {
      buffer[1] = 0x0D;  // IOCFG2        GDO2 Output Pin Configuration
      buffer[3] = 0x2E;  // High-Impedance - GDO0 is not connected
    }

    if (transfer(buffer.data(), buffer.size()) < 0) {
      perror("Unable to flash transmitter settings");
      close();
    }
  }

  if (mSpiDevice >= 0) {
    std::array<uint8_t, 9> buffer = { 0x3E | WRITE_BURST, // Power control read/write
      0x00, 0x60, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };
    if (transfer(buffer.data(), buffer.size()) < 0) {
      perror("Unable to flash power settings");
      close();
    }
  }

  if (mSpiDevice >= 0) {
    std::array<uint8_t, 1> buffer = { 0x34 | WRITE_BYTE}; // Set to receive mode
    if (transfer(buffer.data(), buffer.size()) >= 0) {
      uint8_t state = 0xFF;
      do {
        std::array<uint8_t, 2> buffer = { 0x35 | READ_BURST, 0 }; // Read chip state
        if (transfer(buffer.data(), buffer.size()) >= 0) {
          state = buffer[1] & 0x1F;
        }
      } while (state != 0x0D); // 0x0D = receive
    } else {
      perror("Unable to set receive mode");
      close();
    }
  }
}

CC1101::~CC1101()
{
  close();
}

CC1101::State CC1101::getState() const
{
  return mSpiDevice >= 0 ? State::INITIALIZED : State::ERROR;
}

double CC1101::getRSSIValue() const
{
  double result = std::numeric_limits<double>::max();
  std::array<uint8_t, 2> buffer = { 0x34 | READ_BURST, 0 }; // Read RSSI
  if (transfer(buffer.data(), buffer.size()) >= 0) {
    result = static_cast<double>(buffer[1]); // Cast unsigned to signed first
    if (result >= 128.0) {
      result -= 256.0;
    }
    result = 0.5 * result - 74.0;
  }
  return result;
}

uint8_t CC1101::getStateCode() const
{
  uint8_t result = 0xFF;
  std::array<uint8_t, 2> buffer = { 0x35 | READ_BURST, 0 }; // Read chip state
  if (transfer(buffer.data(), buffer.size()) >= 0) {
    result = buffer[1] & 0x1F;
  }
  return result;
}

void CC1101::close()
{
  if (mSpiDevice >= 0) {
    ::close(mSpiDevice);
    mSpiDevice = -1;
  }
}

int CC1101::transfer(uint8_t* data, const uint32_t& length) const
{
  int result = -1;
  if (mSpiDevice >= 0) {
    static struct spi_ioc_transfer spi = { 0 };
    std::memset(&spi, 0, sizeof(struct spi_ioc_transfer));

    result = ioctl(mSpiDevice, SPI_IOC_RD_BITS_PER_WORD, &spi.bits_per_word);
    if (result >= 0) {
      result = ioctl(mSpiDevice, SPI_IOC_RD_MAX_SPEED_HZ, &spi.speed_hz);
      if (result >= 0) {
        spi.tx_buf = (__u64)data;
        spi.rx_buf = (__u64)data;
        spi.len = length;
        spi.delay_usecs = 0;
        spi.cs_change = 0;
        result = ioctl(mSpiDevice, SPI_IOC_MESSAGE(1), &spi);
      }
    }
  }
  return result;
}
