TARGET=libhideki.so

DEBUG      = -O3 -fwhole-program -std=c++11
CC         = gcc
INCLUDE	   = -I/$(JAVA_HOME)/include                           \
             -I/$(JAVA_HOME)/include/linux                     \
             -I/usr/lib/jvm/jdk-8-oracle/include               \
             -I/usr/lib/jvm/jdk-8-oracle/include/linux         \
             -I/usr/lib/jvm/jdk-8-oracle-arm32-vfp-hflt/include       \
             -I/usr/lib/jvm/jdk-8-oracle-arm32-vfp-hflt/include/linux \
             -I/usr/lib/jvm/java-8-oracle/include              \
             -I/usr/lib/jvm/java-8-oracle/include/linux        \
             -I/usr/lib/jvm/java-8-openjdk-armhf/include       \
             -I/usr/lib/jvm/java-8-openjdk-armhf/include/linux \
             -I/usr/local/include -I/usr/local/include/linux

CFLAGS      := $(DEBUG) -pedantic -Wall $(INCLUDE) -Winline -pipe $(CARGS) -Wl,--add-stdcall-alias -fPIC
LIBS         =  -lm -lpthread -lstdc++

CLASS_PATH = ../target
vpath %.class $(CLASS_PATH)

# Should not alter anything below this line
###############################################################################
SRC	=   CC1101.cpp Decoder.cpp GPIO.cpp Receiver.cpp RXB.cpp HidekiJniWrapper.cpp

OBJ =	$(SRC:.c=.o)

all:	$(OBJ)
	@$(CC) $(OBJ) -shared -o $(TARGET) $(INCLUDE) $(LIBS)

.cpp.o:
	@echo [COMPILE] $<
	@$(CC) -c $(CFLAGS) $< -o $@

clean:
	rm -f $(OBJ) $(TARGET) *~ core tags Makefile.bak

tags:	$(SRC)
	@echo [ctags]
	@ctags $(SRC)

depend:
	makedepend -Y $(SRC)

install:	$(TARGET)
	@echo [install]
	install -m 0755 -d /usr/local/lib
	install -m 0755 -d /usr/local/include
	install -m 0644 $(TARGET)	/usr/local/lib

uninstall:
	@echo [uninstall]
	rm -f /usr/local/lib/$(TARGET)

CC1101.o: CC1101.h
Decoder.o: Decoder.h
GPIO.o: GPIO.h
Receiver.o: Receiver.h
RXB.o: RXB.h
HidekiJniWrapper.o: HidekiJniWrapper.h

#HidekiDecoder.h : HidekiDecoder.class
#	javah -classpath $(CLASS_PATH) $*

