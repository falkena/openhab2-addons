#ifndef HIDEKI_H
#define HIDEKI_H

#include <inttypes.h>

#define DATA_BUFFER_LENGTH 15

void setTimeOut(int timeout);
void setLogFile(const char* name);

int startDecoder(int pin);
int stopDecoder(int pin);
int getDecodedData(uint8_t data[DATA_BUFFER_LENGTH]);

#endif
