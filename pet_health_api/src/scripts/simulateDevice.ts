import { config } from '../config.js';

const PET_ID = process.env.PET_ID ?? '11111111-1111-1111-1111-111111111111';
const BASE = process.env.API_URL ?? `http://127.0.0.1:${config.port}`;
const TOKEN = process.env.DEVICE_INGEST_TOKEN ?? config.deviceIngestToken;

let tick = 0;
let movement = 40;
let laying = 80;
let standing = 45;
let sleeping = 320;
let active = 55;

function nextPayload() {
  tick++;
  const phase = tick % 40;
  const activity =
    phase < 12
      ? 'sleeping'
      : phase < 18
        ? 'laying'
        : phase < 24
          ? 'standing'
          : phase < 32
            ? 'walking'
            : 'playing';

  const forceHighResting = tick % 55 === 20;
  const forceFall = tick % 70 === 35;

  let accelX = 0.2 + Math.random() * 0.4;
  let accelY = 0.1 + Math.random() * 0.3;
  let accelZ = 9.6 + (Math.random() - 0.5) * 0.4;
  let accelEnergy = 0.8 + Math.random();
  let jerk = 0.5 + Math.random();
  let gyroX = (Math.random() - 0.5) * 0.3;
  // Peak-detect style vitals: BPM from IBI mean, HRV as RMSSD-like jitter
  const baseIbi = (bpm: number) => 60000 / bpm;
  let targetBpm = 80;
  let hrvRmssdMs = 30;
  let signalQuality = 0.88 + Math.random() * 0.08;
  let artifactRejected = false;

  switch (activity) {
    case 'standing':
      accelY = 9.6;
      standing++;
      targetBpm = 85 + Math.floor(Math.random() * 15);
      hrvRmssdMs = 24 + Math.random() * 12;
      break;
    case 'laying':
      accelZ = 9.5;
      laying++;
      targetBpm = forceHighResting ? 140 + Math.floor(Math.random() * 15) : 75 + Math.floor(Math.random() * 20);
      hrvRmssdMs = forceHighResting ? 12 + Math.random() * 4 : 28 + Math.random() * 20;
      break;
    case 'sleeping':
      accelEnergy = 0.4;
      sleeping++;
      targetBpm = 70 + Math.floor(Math.random() * 15);
      hrvRmssdMs = 35 + Math.random() * 20;
      break;
    case 'walking':
      accelX = 2 + Math.random() * 2;
      accelEnergy = 5 + Math.random() * 3;
      jerk = 2 + Math.random() * 2;
      movement += 2;
      active++;
      targetBpm = 105 + Math.floor(Math.random() * 20);
      hrvRmssdMs = 18 + Math.random() * 10;
      if (Math.random() < 0.15) {
        artifactRejected = true;
        signalQuality = 0.28;
      }
      break;
    case 'playing':
      accelX = 4 + Math.random() * 4;
      accelEnergy = 14 + Math.random() * 8;
      jerk = 6 + Math.random() * 5;
      movement += 5;
      active += 2;
      targetBpm = 130 + Math.floor(Math.random() * 35);
      hrvRmssdMs = 14 + Math.random() * 10;
      if (Math.random() < 0.25) {
        artifactRejected = true;
        signalQuality = 0.22;
      }
      break;
  }

  const ibi = baseIbi(targetBpm) + (Math.random() - 0.5) * hrvRmssdMs;
  let heartRateBpm = artifactRejected ? 0 : Math.round(60000 / Math.max(300, ibi));

  if (forceFall) {
    jerk = 22;
    accelEnergy = 40;
    gyroX = 8;
  }

  return {
    petId: PET_ID,
    accelX,
    accelY,
    accelZ,
    gyroX,
    gyroY: (Math.random() - 0.5) * 0.3,
    gyroZ: (Math.random() - 0.5) * 0.3,
    enclosureTempC: 28 + Math.random() * 5,
    accelEnergy,
    jerk,
    heartRateBpm,
    hrvRmssdMs,
    signalQuality,
    artifactRejected,
    activity,
    movementCount: movement,
    layingMinutes: laying,
    standingMinutes: standing,
    sleepingMinutes: sleeping,
    activeMinutes: active,
  };
}

async function tickOnce() {
  const payload = nextPayload();
  const res = await fetch(`${BASE}/ingest`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'x-device-token': TOKEN,
    },
    body: JSON.stringify(payload),
  });
  const body = await res.text();
  if (!res.ok) {
    console.error('Ingest failed', res.status, body);
    return;
  }
  const parsed = JSON.parse(body);
  console.log(
    `sent ${payload.activity} bpm=${payload.heartRateBpm} score=${parsed.healthScore?.score} alerts=${parsed.alerts?.length ?? 0}`,
  );
}

console.log(`Simulating device → ${BASE}/ingest pet=${PET_ID}`);
await tickOnce();
setInterval(() => {
  tickOnce().catch((err) => console.error(err));
}, 2000);
