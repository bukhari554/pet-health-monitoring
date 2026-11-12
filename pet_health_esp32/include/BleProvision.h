#pragma once

#include <Arduino.h>

/**
 * GlucoAgri-form BLE provision (as-is protocol):
 * phone links over BLE → kit scans WiFi → phone sends SSID+password → kit joins WiFi → MQTT/cloud.
 */
void bleProvisionBegin(const char *serial);
void bleProvisionLoop();
void bleProvisionSetLinkState(bool wifiUp, bool mqttUp, int wifiRssi);

bool bleCredsDirty();
void bleCredsAck();
/** True while BLE WiFi scan is running — skip STA reconnect. */
bool bleWifiPaused();
/** True while a phone GATT client is connected. */
bool blePhoneLinked();

String bleWifiSsid();
String bleWifiPass();
String bleMqttHost();
uint16_t bleMqttPort();
String bleApiBase();
