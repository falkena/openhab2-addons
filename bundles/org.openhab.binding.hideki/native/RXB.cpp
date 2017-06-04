#include "RXB.h"

RXB::RXB(const int& pin)
  :Receiver(pin)
{
}

RXB::~RXB()
{
}

RXB::State RXB::getState() const
{
  return State::INITIALIZED;
}

double RXB::getRSSIValue() const
{
  return 0.0;
}
