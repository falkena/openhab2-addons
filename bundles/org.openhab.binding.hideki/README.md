# Hideki Binding

This binding provides native support for Hideki based weather stations, like Cresta, TFA-Dostmann and many others.
Two different wireless receivers are implemented now:
  * Superheterodyne (RXB6, RXB8 and similar)
  * Based on CC101 chip

## Supported Things

Receiver, Thermometer, Anemometer, Pluviometer and UV-meter

## Discovery

Discovery is not available.

## Binding Configuration

_If your binding requires or supports general configuration settings, please create a folder ```cfg``` and place the configuration file ```<bindingId>.cfg``` inside it. In this section, you should link to this file and provide some information about the options. The file could e.g. look like:_

```
# Configuration for the Philips Hue Binding
#
# Default secret key for the pairing of the Philips Hue Bridge.
# It has to be between 10-40 (alphanumeric) characters 
# This may be changed by the user for security reasons.
secret=EclipseSmartHome
```

_Note that it is planned to generate some part of this based on the information that is available within ```ESH-INF/binding``` of your binding._

_If your binding does not offer any generic configurations, you can remove this section completely._

## Thing Configuration

_Describe what is needed to manually configure a thing, either through the (Paper) UI or via a thing-file. This should be mainly about its mandatory and optional configuration parameters. A short example entry for a thing file can help!_

_Note that it is planned to generate some part of this based on the XML files within ```ESH-INF/thing``` of your binding._

## Channels

_Here you should provide information about available channel types, what their meaning is and how they can be used._

_Note that it is planned to generate some part of this based on the XML files within ```ESH-INF/thing``` of your binding._

## Examples

hideki.things:

```
Bridge hideki:receiver:TFA [ receiver="CC1101", pin=21, device="/dev/spidev0.0", interrupt=0, refresh=1 ]
{
  Thing anemometer Anemometer "TFA Anemometer" @ "Weather station"
  Thing thermometer Thermometer "TFA Thermometer" @ "Weather station"
  Thing pluviometer Pluviometer "TFA Pluviometer" @ "Weather station"
  Thing uvmeter UVmeter "TFA UVmeter" @ "Weather station"
}
```

hideki.items:

```
DateTime             HidekiThermometerUpdated     { channel="hideki:thermometer:TFA:Thermometer:updated" }
Number:Temperature   HidekiThermometerTemperature { channel="hideki:thermometer:TFA:Thermometer:temperature" }
Number:Dimensionless HidekiThermometerHumidity    { channel="hideki:thermometer:TFA:Thermometer:humidity" }
Switch               HidekiThermometerBattery     { channel="hideki:thermometer:TFA:Thermometer:battery" }
Number               HidekiThermometerId          { channel="hideki:thermometer:TFA:Thermometer:sensor" }
Number               HidekiThermometerChannel     { channel="hideki:thermometer:TFA:Thermometer:channel" }
Number               HidekiThermometerMessage     { channel="hideki:thermometer:TFA:Thermometer:message" }
Number               HidekiThermometerRSSI        { channel="hideki:thermometer:TFA:Thermometer:rssi" }

DateTime           HidekiAnemometerUpdated     { channel="hideki:anemometer:TFA:Anemometer:updated" }
Number:Temperature HidekiAnemometerTemperature { channel="hideki:anemometer:TFA:Anemometer:temperature" }
Number:Temperature HidekiAnemometerWindChill   { channel="hideki:anemometer:TFA:Anemometer:chill" }
Number:Speed       HidekiAnemometerWindGust    { channel="hideki:anemometer:TFA:Anemometer:gust" }
Number:Speed       HidekiAnemometerWindSpeed   { channel="hideki:anemometer:TFA:Anemometer:speed" }
Number:Angle       HidekiAnemometerDirection   { channel="hideki:anemometer:TFA:Anemometer:direction" }
Switch             HidekiAnemometerBattery     { channel="hideki:anemometer:TFA:Anemometer:battery" }
Number             HidekiAnemometerId          { channel="hideki:anemometer:TFA:Anemometer:sensor" }
Number             HidekiAnemometerMessage     { channel="hideki:anemometer:TFA:Anemometer:message" }
Number             HidekiAnemometerRSSI        { channel="hideki:anemometer:TFA:Anemometer:rssi" }

DateTime HidekiPluviometerUpdated   { channel="hideki:pluviometer:TFA:Pluviometer:updated" }
Number   HidekiPluviometerRainLevel { channel="hideki:pluviometer:TFA:Pluviometer:rain" }
Switch   HidekiPluviometerBattery   { channel="hideki:pluviometer:TFA:Pluviometer:battery" }
Number   HidekiPluviometerId        { channel="hideki:pluviometer:TFA:Pluviometer:sensor" }
Number   HidekiPluviometerMessage   { channel="hideki:pluviometer:TFA:Pluviometer:message" }
Number   HidekiPluviometerRSSI      { channel="hideki:pluviometer:TFA:Pluviometer:rssi" }

DateTime           HidekiUVmeterUpdated     { channel="hideki:uvmeter:TFA:UVmeter:updated" }
Number:Temperature HidekiUVmeterTemperature { channel="hideki:uvmeter:TFA:UVmeter:temperature" }
Number             HidekiUVmeterUVIndex     { channel="hideki:uvmeter:TFA:UVmeter:uv" }
Number             HidekiUVmeterMED         { channel="hideki:uvmeter:TFA:UVmeter:med" }
Switch             HidekiUVmeterBattery     { channel="hideki:uvmeter:TFA:UVmeter:battery" }
Number             HidekiUVmeterId          { channel="hideki:uvmeter:TFA:UVmeter:sensor" }
Number             HidekiUVmeterMessage     { channel="hideki:uvmeter:TFA:UVmeter:message" }
Number             HidekiUVmeterRSSI        { channel="hideki:uvmeter:TFA:UVmeter:rssi" }
```

## Any custom content here!

_Feel free to add additional sections for whatever you think should also be mentioned about your binding!_
