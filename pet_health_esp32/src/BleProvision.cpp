#include "BleProvision.h"

#include <BLE2902.h>
#include <BLEDevice.h>
#include <BLESecurity.h>
#include <BLEServer.h>
#include <Preferences.h>
#include <WiFi.h>
#include <algorithm>
#include <cstring>
#include <vector>
#include <config.h>

// GlucoAgri BLE UUIDs — keep protocol identical so Android BleProvision works as-is.
static const char *SERVICE_UUID = "4f9a0001-7c2e-4b1a-9d33-51a6c8e04b10";
static const char *CMD_UUID = "4f9a0002-7c2e-4b1a-9d33-51a6c8e04b10";
static const char *STATUS_UUID = "4f9a0003-7c2e-4b1a-9d33-51a6c8e04b10";
static const char *SCAN_UUID = "4f9a0004-7c2e-4b1a-9d33-51a6c8e04b10";

static Preferences prefs;
static BLECharacteristic *statusChar = nullptr;
static BLECharacteristic *scanChar = nullptr;
static bool clientOn = false;

static String gSerial = DEVICE_SERIAL;
static String gSsid = WIFI_SSID;
static String gPass = WIFI_PASSWORD;
static String gHost = MQTT_HOST;
static uint16_t gPort = MQTT_PORT;
static String gApiBase = API_BASE;

static bool credsDirty = false;
static bool scanning = false;
static bool expectLink = false;
static bool wifiPaused = false;
static uint32_t lastScan = 0;
static uint32_t connectAt = 0;
static bool wifiState = false;
static bool mqttState = false;
static int rssiState = 0;

static char qSsid[33];
static char qPass[65];
static volatile uint8_t qScan = 0;
static volatile uint8_t qCred = 0;

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

class ProvSecurity : public BLESecurityCallbacks {
  uint32_t onPassKeyRequest() override { return 0; }
  void onPassKeyNotify(uint32_t) override {}
  bool onConfirmPIN(uint32_t) override { return true; }
  bool onSecurityRequest() override { return false; }
  void onAuthenticationComplete(esp_ble_auth_cmpl_t) override {}
};

class ProvServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *) override {
    clientOn = true;
    qScan = 1;  // auto WiFi scan on phone connect (Gluco behavior)
    Serial.println("BLE: phone connected — WiFi scan queued");
  }
  void onDisconnect(BLEServer *) override {
    clientOn = false;
    Serial.println("BLE: phone disconnected — advertising");
    BLEDevice::startAdvertising();
  }
};

class ProvCmdCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *c) override {
    std::string v = c->getValue();
    if (v.empty()) return;
    if ((uint8_t)v[0] == 1) {
      qScan = 1;
      return;
    }
    if ((uint8_t)v[0] == 2 && v.size() >= 3) {
      uint8_t sl = (uint8_t)v[1];
      if (sl > 32 || (size_t)(2 + sl) >= v.size()) return;
      uint8_t pl = (uint8_t)v[2 + sl];
      if (pl > 64 || v.size() < (size_t)(3 + sl + pl)) return;
      memset(qSsid, 0, sizeof(qSsid));
      memset(qPass, 0, sizeof(qPass));
      memcpy(qSsid, v.data() + 2, sl);
      memcpy(qPass, v.data() + 3 + sl, pl);
      qCred = 1;
      return;
    }
    // cmd 3 (mode/profile) ignored for Pet Health collar
  }
};

static void notifyStatus(const char *text) {
  if (!statusChar || !clientOn) return;
  statusChar->setValue((uint8_t *)text, strlen(text));
  statusChar->notify();
}

static void notifyHit(const char *ssid, int8_t rssi, bool secure) {
  if (!scanChar || !clientOn) return;
  uint8_t buf[36];
  size_t n = strlen(ssid);
  if (n > 32) n = 32;
  buf[0] = 0x11;
  buf[1] = (uint8_t)rssi;
  buf[2] = secure ? 1 : 0;
  memcpy(buf + 3, ssid, n);
  scanChar->setValue(buf, 3 + n);
  scanChar->notify();
  delay(25);
}

static void notifyEnd() {
  if (!scanChar || !clientOn) return;
  uint8_t end = 0x10;
  scanChar->setValue(&end, 1);
  scanChar->notify();
}

static void startScan() {
  if (scanning) return;
  scanning = true;
  wifiPaused = true;
  WiFi.scanDelete();
  WiFi.scanNetworks(true);
  notifyStatus("scanning");
  Serial.println("BLE: WiFi scan started");
}

static void finishScan(int count) {
  scanning = false;
  wifiPaused = false;
  if (count < 0) count = 0;
  struct Hit {
    String ssid;
    int8_t rssi;
    bool secure;
  };
  std::vector<Hit> best;
  for (int i = 0; i < count; i++) {
    String ssid = WiFi.SSID(i);
    if (!ssid.length()) continue;
    int8_t rssi = (int8_t)WiFi.RSSI(i);
    bool secure = WiFi.encryptionType(i) != WIFI_AUTH_OPEN;
    bool found = false;
    for (auto &h : best) {
      if (h.ssid == ssid) {
        if (rssi > h.rssi) h.rssi = rssi;
        found = true;
        break;
      }
    }
    if (!found) best.push_back({ssid, rssi, secure});
  }
  std::sort(best.begin(), best.end(), [](const Hit &a, const Hit &b) { return a.rssi > b.rssi; });
  if (best.size() > 12) best.resize(12);
  WiFi.scanDelete();
  for (auto &h : best) notifyHit(h.ssid.c_str(), h.rssi, h.secure);
  notifyEnd();
  notifyStatus("idle");
  lastScan = millis();
  Serial.printf("BLE: WiFi scan done (%d nets)\n", (int)best.size());
}

void bleProvisionBegin(const char *serial) {
  gSerial = serial;
  loadCreds();

  // Short fixed name (Gluco style) — longer names get truncated / dropped from ads.
  BLEDevice::init("PetHealth");
  BLEDevice::setPower(ESP_PWR_LVL_P9);
  BLEDevice::setMTU(185);
  BLEDevice::setSecurityCallbacks(new ProvSecurity());
  BLEServer *server = BLEDevice::createServer();
  server->setCallbacks(new ProvServerCallbacks());
  BLEService *service = server->createService(SERVICE_UUID);
  BLECharacteristic *cmd = service->createCharacteristic(CMD_UUID, BLECharacteristic::PROPERTY_WRITE);
  cmd->setCallbacks(new ProvCmdCallbacks());
  statusChar = service->createCharacteristic(
      STATUS_UUID, BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  statusChar->addDescriptor(new BLE2902());
  scanChar = service->createCharacteristic(
      SCAN_UUID, BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  scanChar->addDescriptor(new BLE2902());
  service->start();

  BLEAdvertising *advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  advertising->start();
  lastScan = millis();
  Serial.printf("BLE advertising as PetHealth serial=%s (Gluco protocol)\n", gSerial.c_str());
}

void bleProvisionLoop() {
  if (qCred) {
    char ssid[33];
    char pass[65];
    memcpy(ssid, qSsid, sizeof(ssid));
    memcpy(pass, qPass, sizeof(pass));
    qCred = 0;
    if (ssid[0]) {
      gSsid = ssid;
      gPass = pass;
      persistCreds();
      credsDirty = true;
      expectLink = true;
      connectAt = millis();
      notifyStatus("saved");
      Serial.printf("BLE: WiFi saved SSID=%s — connecting\n", ssid);
    }
  }
  if (qScan) {
    qScan = 0;
    startScan();
  }
  if (scanning) {
    int n = WiFi.scanComplete();
    if (n >= 0 || n == WIFI_SCAN_FAILED) finishScan(n);
  } else if (!wifiState && clientOn && millis() - lastScan > 20000) {
    startScan();
  }
  if (expectLink && wifiState) {
    expectLink = false;
    notifyStatus("connected");
    Serial.println("BLE: WiFi connected status → phone");
  } else if (expectLink && millis() - connectAt > 15000) {
    expectLink = false;
    notifyStatus("failed");
    Serial.println("BLE: WiFi connect timeout");
  }

  static unsigned long lastAdvKickMs = 0;
  unsigned long now = millis();
  if (!clientOn && now - lastAdvKickMs > 12000) {
    lastAdvKickMs = now;
    BLEDevice::startAdvertising();
  }
}

void bleProvisionSetLinkState(bool wifiUp, bool mqttUp, int wifiRssi) {
  wifiState = wifiUp;
  mqttState = mqttUp;
  rssiState = wifiRssi;
}

bool bleCredsDirty() { return credsDirty; }
void bleCredsAck() { credsDirty = false; }
bool bleWifiPaused() { return wifiPaused || scanning; }
bool blePhoneLinked() { return clientOn; }

String bleWifiSsid() { return gSsid; }
String bleWifiPass() { return gPass; }
String bleMqttHost() { return gHost; }
uint16_t bleMqttPort() { return gPort; }
String bleApiBase() { return gApiBase; }
