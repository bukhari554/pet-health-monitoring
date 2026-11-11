#include "BleProvision.h"

#include <BLE2902.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <Preferences.h>
#include <ArduinoJson.h>
#include <config.h>

// PetHealth BLE provisioning service (GlucoTouch-style local link)
static const char *SVC_UUID = "7a5f0001-4d3e-4b2a-9c1d-8e7f6a5b4c3d";
static const char *SSID_UUID = "7a5f0002-4d3e-4b2a-9c1d-8e7f6a5b4c3d";
static const char *PASS_UUID = "7a5f0003-4d3e-4b2a-9c1d-8e7f6a5b4c3d";
static const char *HOST_UUID = "7a5f0004-4d3e-4b2a-9c1d-8e7f6a5b4c3d";
static const char *APPLY_UUID = "7a5f0005-4d3e-4b2a-9c1d-8e7f6a5b4c3d";
static const char *STATUS_UUID = "7a5f0006-4d3e-4b2a-9c1d-8e7f6a5b4c3d";
static const char *INFO_UUID = "7a5f0007-4d3e-4b2a-9c1d-8e7f6a5b4c3d";

static Preferences prefs;
static BLEServer *server = nullptr;
static BLECharacteristic *statusChar = nullptr;
static BLECharacteristic *infoChar = nullptr;

static String gSerial = DEVICE_SERIAL;
static String gSsid = WIFI_SSID;
static String gPass = WIFI_PASSWORD;
static String gHost = MQTT_HOST;
static uint16_t gPort = MQTT_PORT;
static String gApiBase = API_BASE;

static bool credsDirty = false;
static bool deviceConnected = false;
static bool wifiState = false;
static bool mqttState = false;
static int rssiState = 0;
static unsigned long lastStatusPushMs = 0;

static void persistCreds() {
  prefs.putString("ssid", gSsid);
  prefs.putString("pass", gPass);
  prefs.putString("host", gHost);
  prefs.putUShort("port", gPort);
  prefs.putString("api", gApiBase);
}

static void loadCreds() {
  prefs.begin("petble", false);
  gSsid = prefs.getString("ssid", WIFI_SSID);
  gPass = prefs.getString("pass", WIFI_PASSWORD);
  gHost = prefs.getString("host", MQTT_HOST);
  gPort = prefs.getUShort("port", MQTT_PORT);
  gApiBase = prefs.getString("api", API_BASE);
}

static String statusJson() {
  StaticJsonDocument<256> doc;
  doc["serial"] = gSerial;
  doc["ble"] = deviceConnected;
  doc["wifi"] = wifiState;
  doc["mqtt"] = mqttState;
  doc["cloud"] = wifiState && mqttState;
  doc["rssi"] = rssiState;
  doc["ssid"] = gSsid;
  doc["host"] = gHost;
  doc["port"] = gPort;
  String out;
  serializeJson(doc, out);
  return out;
}

static void pushStatus(bool force = false) {
  if (!statusChar) return;
  unsigned long now = millis();
  if (!force && now - lastStatusPushMs < 1000) return;
  lastStatusPushMs = now;
  String payload = statusJson();
  statusChar->setValue(payload.c_str());
  if (deviceConnected) statusChar->notify();
}

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *s) override {
    deviceConnected = true;
    Serial.println("BLE: phone connected");
    pushStatus(true);
  }
  void onDisconnect(BLEServer *s) override {
    deviceConnected = false;
    Serial.println("BLE: phone disconnected — advertising");
    s->startAdvertising();
  }
};

class SsidCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *c) override {
    String v = c->getValue().c_str();
    v.trim();
    if (v.length() == 0 || v.length() > 32) return;
    gSsid = v;
    Serial.printf("BLE SSID=%s\n", gSsid.c_str());
  }
};

class PassCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *c) override {
    String v = c->getValue().c_str();
    if (v.length() > 63) return;
    gPass = v;
    Serial.println("BLE PASS received");
  }
};

class HostCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *c) override {
    String v = c->getValue().c_str();
    v.trim();
    if (v.length() == 0 || v.length() > 64) return;
    // Accept "192.168.1.14" or "192.168.1.14:1883" or full http URL
    if (v.startsWith("http://") || v.startsWith("https://")) {
      gApiBase = v;
      // extract host for MQTT if possible
      int start = v.indexOf("://") + 3;
      int slash = v.indexOf('/', start);
      String hostPort = slash > 0 ? v.substring(start, slash) : v.substring(start);
      int colon = hostPort.indexOf(':');
      gHost = colon > 0 ? hostPort.substring(0, colon) : hostPort;
    } else {
      int colon = v.indexOf(':');
      if (colon > 0) {
        gHost = v.substring(0, colon);
        gPort = (uint16_t)v.substring(colon + 1).toInt();
        if (gPort == 0) gPort = MQTT_PORT;
      } else {
        gHost = v;
      }
      gApiBase = String("http://") + gHost + ":3000";
    }
    Serial.printf("BLE HOST=%s port=%u api=%s\n", gHost.c_str(), gPort, gApiBase.c_str());
  }
};

class ApplyCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *c) override {
    String v = c->getValue().c_str();
    v.trim();
    if (v.length() == 0) return;
    persistCreds();
    credsDirty = true;
    Serial.println("BLE APPLY — reconnect WiFi/MQTT");
    pushStatus(true);
  }
};

void bleProvisionBegin(const char *serial) {
  gSerial = serial;
  loadCreds();

  String name = String("PetHealth-") + gSerial;
  BLEDevice::init(name.c_str());
  server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService *svc = server->createService(SVC_UUID);

  BLECharacteristic *ssidChar = svc->createCharacteristic(
      SSID_UUID, BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE);
  ssidChar->setCallbacks(new SsidCallbacks());
  ssidChar->setValue(gSsid.c_str());

  BLECharacteristic *passChar = svc->createCharacteristic(
      PASS_UUID, BLECharacteristic::PROPERTY_WRITE);
  passChar->setCallbacks(new PassCallbacks());

  BLECharacteristic *hostChar = svc->createCharacteristic(
      HOST_UUID, BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE);
  hostChar->setCallbacks(new HostCallbacks());
  hostChar->setValue(gHost.c_str());

  BLECharacteristic *applyChar = svc->createCharacteristic(
      APPLY_UUID, BLECharacteristic::PROPERTY_WRITE);
  applyChar->setCallbacks(new ApplyCallbacks());

  statusChar = svc->createCharacteristic(
      STATUS_UUID,
      BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  statusChar->addDescriptor(new BLE2902());
  statusChar->setValue(statusJson().c_str());

  infoChar = svc->createCharacteristic(INFO_UUID, BLECharacteristic::PROPERTY_READ);
  StaticJsonDocument<192> info;
  info["serial"] = gSerial;
  info["fw"] = "pet-health-esp32";
  info["svc"] = "ble-provision";
  String infoStr;
  serializeJson(info, infoStr);
  infoChar->setValue(infoStr.c_str());

  svc->start();

  BLEAdvertising *adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(SVC_UUID);
  adv->setScanResponse(true);
  adv->setMinPreferred(0x06);
  BLEDevice::startAdvertising();
  Serial.printf("BLE advertising as %s\n", name.c_str());
}

void bleProvisionLoop() {
  pushStatus(false);
}

void bleProvisionSetLinkState(bool wifiUp, bool mqttUp, int wifiRssi) {
  bool changed = (wifiState != wifiUp) || (mqttState != mqttUp) || (rssiState != wifiRssi);
  wifiState = wifiUp;
  mqttState = mqttUp;
  rssiState = wifiRssi;
  if (changed) pushStatus(true);
}

bool bleCredsDirty() { return credsDirty; }
void bleCredsAck() { credsDirty = false; }

String bleWifiSsid() { return gSsid; }
String bleWifiPass() { return gPass; }
String bleMqttHost() { return gHost; }
uint16_t bleMqttPort() { return gPort; }
String bleApiBase() { return gApiBase; }
