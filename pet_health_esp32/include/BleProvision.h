#pragma once

#include <Arduino.h>

// GlucoTouch-style: phone pairs over BLE, pushes WiFi + broker, then device goes CLOUD via MQTT.
void bleProvisionBegin(const char *serial);
void bleProvisionLoop();
void bleProvisionSetLinkState(bool wifiUp, bool mqttUp, int wifiRssi);

bool bleCredsDirty();
void bleCredsAck();

String bleWifiSsid();
String bleWifiPass();
String bleMqttHost();
uint16_t bleMqttPort();
String bleApiBase();
