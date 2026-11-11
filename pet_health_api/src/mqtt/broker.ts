import { createServer } from 'net';
import { createServer as createHttpServer } from 'http';
import { Aedes, type Client } from 'aedes';
// websocket-stream has incomplete typings for createServer
// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-ignore
import websocketStream from 'websocket-stream';
import { authenticateDevice, setMqttDown, touchDevice } from '../services/deviceService.js';
import { ingestReading } from '../services/sensorService.js';
import { config } from '../config.js';

export async function startMqttBroker(log: {
  info: (o: unknown, msg?: string) => void;
  error: (o: unknown, msg?: string) => void;
}) {
  const broker = await Aedes.createBroker({
    authenticate(client, username, password, done) {
      const serial = String(username ?? '');
      const token = password ? password.toString() : '';
      authenticateDevice(serial, token)
        .then((device) => {
          if (!device) {
            const err = Object.assign(new Error('Auth failed'), { returnCode: 4 });
            done(err, false);
            return;
          }
          (client as Client & { petSerial?: string }).petSerial = serial;
          done(null, true);
        })
        .catch((err) => done(err, false));
    },
  });

  broker.on('clientReady', async (client) => {
    const serial = (client as Client & { petSerial?: string }).petSerial;
    if (!serial) return;
    await touchDevice(serial, { mqttUp: true });
    log.info({ serial, id: client.id }, 'MQTT client ready');
  });

  broker.on('clientDisconnect', async (client) => {
    const serial = (client as Client & { petSerial?: string }).petSerial;
    if (!serial) return;
    await setMqttDown(serial);
    log.info({ serial, id: client.id }, 'MQTT client disconnected');
  });

  broker.on('publish', async (packet, client) => {
    if (!client) return;
    const topic = packet.topic;
    const serial = (client as Client & { petSerial?: string }).petSerial;
    if (!serial) return;

    let payload: Record<string, unknown> = {};
    try {
      payload = JSON.parse(packet.payload.toString());
    } catch {
      return;
    }

    if (topic === `pet/${serial}/status`) {
      await touchDevice(serial, {
        mqttUp: true,
        wifiRssi: typeof payload.wifiRssi === 'number' ? payload.wifiRssi : undefined,
        firmware: typeof payload.firmware === 'string' ? payload.firmware : undefined,
      });
      return;
    }

    if (topic === `pet/${serial}/evt`) {
      await touchDevice(serial, { mqttUp: true });
      const petId = typeof payload.petId === 'string' ? payload.petId : undefined;
      if (!petId) return;
      try {
        await ingestReading({
          petId,
          accelX: Number(payload.accelX ?? 0),
          accelY: Number(payload.accelY ?? 0),
          accelZ: Number(payload.accelZ ?? 0),
          gyroX: Number(payload.gyroX ?? 0),
          gyroY: Number(payload.gyroY ?? 0),
          gyroZ: Number(payload.gyroZ ?? 0),
          enclosureTempC:
            payload.enclosureTempC == null ? undefined : Number(payload.enclosureTempC),
          accelEnergy:
            payload.accelEnergy == null ? undefined : Number(payload.accelEnergy),
          gyroEnergy: payload.gyroEnergy == null ? undefined : Number(payload.gyroEnergy),
          jerk: payload.jerk == null ? undefined : Number(payload.jerk),
          heartRateBpm: Number(payload.heartRateBpm ?? 0),
          hrvRmssdMs: payload.hrvRmssdMs == null ? undefined : Number(payload.hrvRmssdMs),
          signalQuality:
            payload.signalQuality == null ? undefined : Number(payload.signalQuality),
          restingHeartRate:
            payload.restingHeartRate == null ? null : Number(payload.restingHeartRate),
          artifactRejected: Boolean(payload.artifactRejected ?? false),
          activity: payload.activity as
            | 'sleeping'
            | 'laying'
            | 'standing'
            | 'walking'
            | 'playing'
            | 'unknown'
            | undefined,
          movementCount:
            payload.movementCount == null ? undefined : Number(payload.movementCount),
          layingMinutes:
            payload.layingMinutes == null ? undefined : Number(payload.layingMinutes),
          standingMinutes:
            payload.standingMinutes == null ? undefined : Number(payload.standingMinutes),
          sleepingMinutes:
            payload.sleepingMinutes == null ? undefined : Number(payload.sleepingMinutes),
          activeMinutes:
            payload.activeMinutes == null ? undefined : Number(payload.activeMinutes),
        });
      } catch (err) {
        log.error(err, 'MQTT evt ingest failed');
      }
    }
  });

  const tcp = createServer(broker.handle);
  tcp.listen(config.mqttPort, '0.0.0.0', () => {
    log.info({ port: config.mqttPort }, 'MQTT TCP listening');
  });

  const httpServer = createHttpServer();
  // Aedes handle is compatible at runtime; websocket-stream typings are wrong for the 2nd arg.
  const createWsServer = (
    websocketStream as unknown as {
      createServer: (opts: object, handle: typeof broker.handle) => void;
    }
  ).createServer;
  createWsServer({ server: httpServer, path: '/mqtt' }, broker.handle);
  httpServer.listen(config.mqttWsPort, '0.0.0.0', () => {
    log.info({ port: config.mqttWsPort, path: '/mqtt' }, 'MQTT WebSocket listening');
  });

  return { broker, tcp, httpServer };
}
