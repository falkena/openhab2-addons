#include "Decoder.h"
#include "CC1101.h"

#include <signal.h>
#include <unistd.h>

#include <ctime>
#include <iomanip>
#include <iostream>
#include <map>

volatile bool done = false;
void terminate(int signum)
{
  done = true;
}

int main(int argc, char *argv[])
{
  signal(SIGINT, terminate);

  CC1101 receiver(21, "/dev/spidev0.0", 0);
  receiver.setTimeout(5000);

  Decoder decoder(&receiver);
  decoder.start();

  std::map<uint32_t, time_t> last;
  std::cout << "Start CC1101..." << std::endl;
  while(true) {
    double rssi = 0.0;
    std::array<uint8_t, Decoder::DATA_BUFFER_LENGTH> data;
    int length = decoder.getDecodedData(data, rssi);
    if(length > 0) {
      const time_t now = time(nullptr);
      const uint32_t type = static_cast<uint32_t>(data[3]) & 0x1F;

      double delta = 0.0;
      auto it = last.find(type);
      if(it == last.end()) {
        last.insert(std::pair<uint32_t, time_t>(type, now));
      } else {
        delta = difftime(now, it->second);
        it->second = now;
      }

      std::string buffer(asctime(localtime(&now)));
      std::cout << buffer.substr(0, buffer.size() - 1) << ": ";
      switch(type) {
        case 0x0C: {
          std::cout << "Anemometer";
          break;
        }
        case 0x0D: {
          std::cout << "UV - meter";
          break;
        }
        case 0x0E: {
          std::cout << "Rain level meter";
          break;
        }
        case 0x1E: {
          std::cout << "Thermo/hygro - meter";
          break;
        }
        default: {
          std::cout << "Unknown";
          break;
        }
      }
//      std::cout << "Got new sensor 0x" << std::setfill('0') << std::setw(2) << std::hex << std::uppercase;
//      std::cout << type << " with RSSI: " << rssi << ", dt = " << delta << " seconds" << std::endl;
      std::cout << " with RSSI: " << rssi << ", dt = " << delta << " seconds" << std::endl;
    }
    
//    std::cout << "State: 0x" << std::setfill('0') << std::setw(2) << std::uppercase << std::hex
//              << static_cast<uint32_t>(receiver.getState()) << std::dec << std::endl;
    if(done) {
      break;
    }
    sleep(1);
  }
  std::cout << "Stop CC1101..." << std::endl;
  return 0;
}