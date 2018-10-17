#include "CC1101.h"
#include "spdlog/spdlog.h"
#include "spdlog/sinks/daily_file_sink.h"

#include <signal.h>
#include <unistd.h>
#include <sys/stat.h>

#include <iostream>
#include <string>

volatile bool done = false;
void terminate(int signum)
{
  done = true;
}

int main(int argc, char *argv[])
{
  signal(SIGINT, terminate);

  std::string path(PATH_MAX, '\0');
  realpath("/proc/self/exe", &path[0]);
  path = path.substr(0, path.find_last_of("/\\"));

  struct stat state;
  if(stat(path.c_str(), &state) == 0) {
    std::cout << "Create log directory..." << std::endl;
    path = path + std::string("/logs");
    if((mkdir(path.c_str(), 0777) == 0) || (errno == EEXIST)) {
      std::cout << "Create logger..." << std::endl;
      auto logger = spdlog::daily_logger_mt("CC1101", path + "/pulses.txt", 0, 0);

      std::cout << "Start CC1101..." << std::endl;
      CC1101 receiver(21, "/dev/spidev0.0", 0);
      receiver.setTimeout(5000);
      receiver.start();

      while(true) {
        Receiver::PulseDurationType duration = 0;
        if(receiver.getNextPulse(duration)) {
          logger->info("{:5d}", duration);
        } else {
          sleep(1);
        }

        if(done) {
          break;
        }
      }

      std::cout << "Stop CC1101..." << std::endl;
      receiver.stop();
    }
  }

  return 0;
}