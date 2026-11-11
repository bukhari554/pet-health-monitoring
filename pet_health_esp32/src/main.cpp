#include <Wire.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <math.h>
#include <config.h>
#include "BleProvision.h"

Adafruit_MPU6050 mpu;
WiFiClient wifiClient;
PubSubClient mqtt(wifiClient);

unsigned long lastSampleTime = 0;
unsigned long lastStatusMs = 0;
unsigned long lastReadingMs = 0;
unsigned long lastMqttAttemptMs = 0;
unsigned long standingTime = 0, sleepingTime = 0, layingTime = 0, activeTime = 0;
int movementCount = 0;
String lastState = "unknown";
unsigned long lastStateChangeTime = 0;

bool wifiUp = false;
bool mqttUp = false;

const int SAMPLE_INTERVAL_MS = 20;
const int REFRACTORY_MS = 280;
const int IBI_BUFFER = 8;
const float EMA_ALPHA = 0.15f;

float ema = 0;
bool rising = false;
unsigned long lastPeakMs = 0;
unsigned long ibiMs[IBI_BUFFER];
int ibiCount = 0;
int ibiIndex = 0;
int currentBpm = 0;
float currentHrv = 0;
float signalQuality = 0;
bool artifactRejected = false;

float lastDynamicEnergy = 0.0f;
float dynamicEma = 0.0f;
String currentActivity = "unknown";
float ax = 0, ay = 0, az = 0, gx = 0, gy = 0, gz = 0;
float accelEnergy = 0, gyroEnergy = 0, jerk = 0, enclosureTempC = 0;
unsigned long dayAnchorMs = 0;
unsigned long lastMoveIncMs = 0;
const float GRAVITY_MS2 = 9.81f;

void pushIbi(unsigned long ibi) {
  if (ibi < 300 || ibi > 1500) return;
  ibiMs[ibiIndex] = ibi;
  ibiIndex = (ibiIndex + 1) % IBI_BUFFER;
  if (ibiCount < IBI_BUFFER) ibiCount++;
}

void recomputeVitals() {
  if (ibiCount < 3) {
    currentBpm = 0;
    currentHrv = 0;
    signalQuality = constrain(signalQuality * 0.9f, 0.0f, 0.4f);
    return;
  }
  // Walk circular buffer in chronological order (oldest → newest)
  unsigned long ordered[IBI_BUFFER];
  int start = (ibiIndex - ibiCount + IBI_BUFFER) % IBI_BUFFER;
  for (int i = 0; i < ibiCount; i++) {
    ordered[i] = ibiMs[(start + i) % IBI_BUFFER];
  }
  float sum = 0;
  for (int i = 0; i < ibiCount; i++) sum += ordered[i];
  float meanIbi = sum / ibiCount;
  currentBpm = (int)constrain(60000.0f / meanIbi, 40, 200);
  float sq = 0;
  int pairs = 0;
  for (int i = 1; i < ibiCount; i++) {
    long d = (long)ordered[i] - (long)ordered[i - 1];
    sq += d * d;
    pairs++;
  }
  currentHrv = pairs > 0 ? sqrt(sq / pairs) : 0;
  float var = 0;
  for (int i = 0; i < ibiCount; i++) {
    float d = ordered[i] - meanIbi;
    var += d * d;
  }
  var /= ibiCount;
  float cv = sqrt(var) / meanIbi;
  float fill = ibiCount / (float)IBI_BUFFER;
  signalQuality = constrain(fill * (1.0f - constrain(cv * 2.5f, 0.0f, 1.0f)), 0.0f, 1.0f);
}

void samplePulse(bool motionBusy) {
  int raw = analogRead(PULSE_SENSOR_PIN);
  if (ema <= 1) ema = raw;
  ema = EMA_ALPHA * raw + (1.0f - EMA_ALPHA) * ema;
  float threshold = ema + 40.0f;
  unsigned long now = millis();

  // Always expire stale BPM (even during motion) so UI never sticks on old HR.
  if (lastPeakMs > 0 && now - lastPeakMs > 3000) {
    currentBpm = 0;
    currentHrv = 0;
    signalQuality = max(0.05f, signalQuality * 0.8f);
    artifactRejected = true;
  }

  if (motionBusy) {
    artifactRejected = true;
    signalQuality = min(signalQuality, 0.25f);
    rising = false;
    return;
  }

  if (raw > threshold && !rising) {
    rising = true;
    if (lastPeakMs > 0 && (now - lastPeakMs) >= REFRACTORY_MS) {
      pushIbi(now - lastPeakMs);
      recomputeVitals();
      artifactRejected = false;
    }
    lastPeakMs = now;
  } else if (raw < ema) {
    rising = false;
  }
}

// dynamicEnergy = |‖a‖ − g| (m/s²). Gravity-inclusive ‖a‖ must NOT drive class labels.
String classifyActivity(float axV, float ayV, float azV, float dynamicEnergy, float gyroE) {
  if (dynamicEnergy > 6.0f || gyroE > 3.0f) return "playing";
  if (dynamicEnergy > 1.8f || gyroE > 1.2f) return "walking";

  float abx = fabs(axV), aby = fabs(ayV), abz = fabs(azV);
  if (aby >= abx && aby >= abz && aby > 7.0f) return "standing";
  if (abz >= abx && abz >= aby && abz > 7.0f) {
    if (dynamicEnergy < 0.35f && gyroE < 0.45f) return "sleeping";
    return "laying";
  }
  if (dynamicEnergy < 0.35f && gyroE < 0.4f) return "sleeping";
  return "unknown";
}

bool ensureWifi() {
  if (WiFi.status() == WL_CONNECTED) {
    wifiUp = true;
    return true;
  }
  wifiUp = false;
  mqttUp = false;
  String ssid = bleWifiSsid();
  String pass = bleWifiPass();
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid.c_str(), pass.c_str());
  Serial.printf("WiFi join %s\n", ssid.c_str());
  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 15000) {
    delay(250);
    Serial.print('.');
  }
  Serial.println();
  wifiUp = WiFi.status() == WL_CONNECTED;
  if (wifiUp) {
    Serial.print("WiFi OK ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("WiFi FAIL — use app BLE provisioning");
  }
  return wifiUp;
}

bool ensureMqtt() {
  if (!wifiUp) return false;
  if (mqtt.connected()) {
    mqttUp = true;
    return true;
  }
  unsigned long now = millis();
  if (now - lastMqttAttemptMs < MQTT_HEAL_MS && lastMqttAttemptMs != 0) {
    mqttUp = false;
    return false;
  }
  lastMqttAttemptMs = now;
  String host = bleMqttHost();
  uint16_t port = bleMqttPort();
  mqtt.setServer(host.c_str(), port);
  mqtt.setKeepAlive(MQTT_KEEPALIVE_SEC);
  mqtt.setBufferSize(1024);
  Serial.printf("MQTT connect %s:%u as %s...\n", host.c_str(), port, DEVICE_SERIAL);
  if (mqtt.connect(DEVICE_SERIAL, DEVICE_SERIAL, DEVICE_TOKEN)) {
    mqttUp = true;
    Serial.println("CLOUD up (MQTT)");
    return true;
  }
  mqttUp = false;
  Serial.printf("MQTT fail rc=%d — will heal ~%ds\n", mqtt.state(), MQTT_HEAL_MS / 1000);
  return false;
}

void httpHeartbeatFallback() {
  if (mqttUp || !wifiUp) return;
  HTTPClient http;
  http.begin(bleApiBase() + "/devices/" + DEVICE_SERIAL + "/heartbeat");
  http.addHeader("Content-Type", "application/json");
  http.addHeader("x-device-token", DEVICE_TOKEN);
  StaticJsonDocument<192> doc;
  doc["wifiRssi"] = WiFi.RSSI();
  doc["firmware"] = "pet-health-esp32";
  doc["mqttUp"] = false;
  String body;
  serializeJson(doc, body);
  int code = http.POST(body);
  Serial.printf("HTTP heartbeat %d\n", code);
  http.end();
}

void publishStatus() {
  if (!mqttUp) {
    httpHeartbeatFallback();
    return;
  }
  StaticJsonDocument<256> doc;
  doc["online"] = true;
  doc["wifiRssi"] = WiFi.RSSI();
  doc["firmware"] = "pet-health-esp32";
  doc["activity"] = currentActivity;
  doc["bpm"] = currentBpm;
  doc["quality"] = signalQuality;
  char buf[256];
  size_t n = serializeJson(doc, buf, sizeof(buf));
  String topic = String("pet/") + DEVICE_SERIAL + "/status";
  bool ok = mqtt.publish(topic.c_str(), (uint8_t *)buf, n, false);
  Serial.printf("status publish %s\n", ok ? "ok" : "fail");
}

void publishReading() {
  StaticJsonDocument<768> doc;
  doc["petId"] = PET_ID;
  doc["accelX"] = ax;
  doc["accelY"] = ay;
  doc["accelZ"] = az;
  doc["gyroX"] = gx;
  doc["gyroY"] = gy;
  doc["gyroZ"] = gz;
  doc["enclosureTempC"] = enclosureTempC;
  doc["accelEnergy"] = accelEnergy;
  doc["gyroEnergy"] = gyroEnergy;
  doc["jerk"] = jerk;
  doc["heartRateBpm"] = currentBpm;
  doc["hrvRmssdMs"] = currentHrv;
  doc["signalQuality"] = signalQuality;
  if ((currentActivity == "sleeping" || currentActivity == "laying") && currentBpm > 0) {
    doc["restingHeartRate"] = currentBpm;
  } else {
    doc["restingHeartRate"] = nullptr;
  }
  doc["artifactRejected"] = artifactRejected || currentBpm == 0;
  doc["activity"] = currentActivity;
  doc["movementCount"] = movementCount;
  doc["layingMinutes"] = (int)(layingTime / 60);
  doc["standingMinutes"] = (int)(standingTime / 60);
  doc["sleepingMinutes"] = (int)(sleepingTime / 60);
  doc["activeMinutes"] = (int)(activeTime / 60);

  if (mqttUp) {
    char buf[768];
    size_t n = serializeJson(doc, buf, sizeof(buf));
    String topic = String("pet/") + DEVICE_SERIAL + "/evt";
    bool ok = mqtt.publish(topic.c_str(), (uint8_t *)buf, n, false);
    Serial.printf("evt MQTT %s\n", ok ? "ok" : "fail");
    return;
  }

  if (!wifiUp) return;
  String body;
  serializeJson(doc, body);
  HTTPClient http;
  http.begin(bleApiBase() + "/ingest");
  http.addHeader("Content-Type", "application/json");
  http.addHeader("x-device-token", DEVICE_TOKEN);
  int code = http.POST(body);
  Serial.printf("evt HTTP %d\n", code);
  http.end();
}

void applyBleCredsIfNeeded() {
  if (!bleCredsDirty()) return;
  bleCredsAck();
  Serial.println("Applying BLE WiFi/MQTT credentials…");
  if (mqtt.connected()) mqtt.disconnect();
  mqttUp = false;
  WiFi.disconnect(true, false);
  wifiUp = false;
  delay(200);
  lastMqttAttemptMs = 0;
  ensureWifi();
  ensureMqtt();
  publishStatus();
}

void setup() {
  Serial.begin(115200);
  lastStateChangeTime = millis();
  analogReadResolution(12);
  Serial.println();
  Serial.println("PetHealth ESP32 boot (GlucoTouch BLE→WiFi→MQTT)");
  Serial.printf("Validation mode: %d\n", BPM_VALIDATION_MODE);

  if (!mpu.begin()) {
    Serial.println("MPU6050 not found!");
    while (1) delay(10);
  }
  mpu.setAccelerometerRange(MPU6050_RANGE_8_G);
  mpu.setGyroRange(MPU6050_RANGE_500_DEG);
  mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);
  Serial.println("MPU6050 OK");

  bleProvisionBegin(DEVICE_SERIAL);

  if (!BPM_VALIDATION_MODE) {
    ensureWifi();
    ensureMqtt();
    publishStatus();
  }
}

void loop() {
  unsigned long now = millis();
  bleProvisionLoop();
  applyBleCredsIfNeeded();

  // Soft "calendar day": reset session counters every 24h of uptime
  if (dayAnchorMs == 0) dayAnchorMs = now;
  if (now - dayAnchorMs >= 86400000UL) {
    standingTime = sleepingTime = layingTime = activeTime = 0;
    movementCount = 0;
    dayAnchorMs = now;
  }

  if (now - lastSampleTime >= SAMPLE_INTERVAL_MS) {
    lastSampleTime = now;
    sensors_event_t a, g, temp;
    mpu.getEvent(&a, &g, &temp);
    ax = a.acceleration.x;
    ay = a.acceleration.y;
    az = a.acceleration.z;
    gx = g.gyro.x;
    gy = g.gyro.y;
    gz = g.gyro.z;
    enclosureTempC = temp.temperature;

    float mag = sqrt(ax * ax + ay * ay + az * az);
    float dynamic = fabs(mag - GRAVITY_MS2);
    dynamicEma = 0.25f * dynamic + 0.75f * dynamicEma;
    // Publish motion energy (not gravity-inclusive ‖a‖) so activity/HR correlate.
    accelEnergy = dynamicEma;
    gyroEnergy = sqrt(gx * gx + gy * gy + gz * gz);
    jerk = fabs(dynamicEma - lastDynamicEnergy) / (SAMPLE_INTERVAL_MS / 1000.0f);
    lastDynamicEnergy = dynamicEma;

    currentActivity = classifyActivity(ax, ay, az, dynamicEma, gyroEnergy);
    bool motionBusy =
        (currentActivity == "walking" || currentActivity == "playing" || jerk > 15.0f);

    // ~1 Hz step proxy while moving (not every 20ms sample)
    if (motionBusy && now - lastMoveIncMs >= 1000) {
      movementCount++;
      lastMoveIncMs = now;
    }

    if (currentActivity != lastState) {
      unsigned long spent = (now - lastStateChangeTime) / 1000;
      if (lastState == "standing") standingTime += spent;
      else if (lastState == "sleeping") sleepingTime += spent;
      else if (lastState == "laying") layingTime += spent;
      else if (lastState == "walking" || lastState == "playing") activeTime += spent;
      lastStateChangeTime = now;
      lastState = currentActivity;
    }
    samplePulse(motionBusy);
  }

  if (BPM_VALIDATION_MODE) {
    bleProvisionSetLinkState(false, false, 0);
    return;
  }

  if (WiFi.status() != WL_CONNECTED) {
    wifiUp = false;
    mqttUp = false;
    if (now - lastMqttAttemptMs > MQTT_HEAL_MS) ensureWifi();
  } else {
    wifiUp = true;
    if (!mqtt.connected()) {
      mqttUp = false;
      ensureMqtt();
    } else {
      mqttUp = true;
      mqtt.loop();
    }
  }

  bleProvisionSetLinkState(wifiUp, mqttUp, wifiUp ? WiFi.RSSI() : 0);

  if (now - lastStatusMs >= STATUS_INTERVAL_MS) {
    lastStatusMs = now;
    Serial.printf("LINK ble-prov wifi=%d cloud(mqtt)=%d activity=%s bpm=%d\n",
                  wifiUp ? 1 : 0, mqttUp ? 1 : 0, currentActivity.c_str(), currentBpm);
    publishStatus();
  }

  if (now - lastReadingMs >= READING_INTERVAL_MS) {
    lastReadingMs = now;
    publishReading();
  }
}
