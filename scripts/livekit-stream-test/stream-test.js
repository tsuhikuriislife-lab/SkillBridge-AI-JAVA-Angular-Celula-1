#!/usr/bin/env node

const { AccessToken, RoomServiceClient } = require('livekit-server-sdk');
const {
  AudioFrame,
  AudioSource,
  ConnectionState,
  LocalAudioTrack,
  LocalVideoTrack,
  Room,
  RoomEvent,
  TrackSource,
  VideoBufferType,
  VideoFrame,
  VideoSource,
  dispose,
} = require('@livekit/rtc-node');

const VIDEO_WIDTH = 640;
const VIDEO_HEIGHT = 360;
const VIDEO_FPS = 15;
const AUDIO_SAMPLE_RATE = 48000;
const AUDIO_FRAME_SAMPLES = 480;
const AUDIO_FRAME_DURATION_MS = 10;
const SUBSCRIPTION_TIMEOUT_MS = 10000;
const CA_72_SAMPLE_INTERVAL_MS = 1000;
const CA_72_WINDOW_DURATION_MS = 5000;
const CA_72_STATS_RETRY_MS = 5000;
const COLOR_BARS = [
  [255, 255, 255],
  [255, 255, 0],
  [0, 255, 255],
  [0, 255, 0],
  [255, 0, 255],
  [255, 0, 0],
  [0, 0, 255],
];

let totalTests = 0;
let passedTests = 0;
let failedTests = 0;

function assert(condition, message) {
  totalTests++;
  if (condition) {
    passedTests++;
    console.log(`  \x1b[32m✔ PASS:\x1b[0m ${message}`);
  } else {
    failedTests++;
    console.error(`  \x1b[31m✘ FAIL:\x1b[0m ${message}`);
  }
}

function printEvent(role, event) {
  console.log(`  \x1b[34mEVENT:\x1b[0m ${role} ${event}`);
}

function formatError(error) {
  if (!(error instanceof Error)) {
    return String(error);
  }

  const cause = error.cause ? `\nCaused by: ${formatError(error.cause)}` : '';
  return `${error.stack || `${error.name}: ${error.message}`}${cause}`;
}

function redactSecrets(text, secrets) {
  return secrets.reduce((result, secret) => {
    return secret ? result.split(secret).join('[REDACTED]') : result;
  }, text);
}

function wait(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

function waitForTrackSubscribed(room, trackName) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      room.off(RoomEvent.TrackSubscribed, onTrackSubscribed);
      reject(new Error(`El Alumno no recibió la pista ${trackName} a tiempo.`));
    }, SUBSCRIPTION_TIMEOUT_MS);

    const onTrackSubscribed = (track, publication, participant) => {
      if (participant.identity !== 'tutor' || publication.name !== trackName) {
        return;
      }

      clearTimeout(timeout);
      room.off(RoomEvent.TrackSubscribed, onTrackSubscribed);
      resolve(performance.now());
    };

    room.on(RoomEvent.TrackSubscribed, onTrackSubscribed);
  });
}

function createVideoFrameData(frameNumber) {
  const frameData = new Uint8Array(VIDEO_WIDTH * VIDEO_HEIGHT * 4);
  const stripeStart = (frameNumber * 12) % VIDEO_WIDTH;
  const stripeWidth = 24;

  for (let y = 0; y < VIDEO_HEIGHT; y++) {
    for (let x = 0; x < VIDEO_WIDTH; x++) {
      const barIndex = Math.floor((x * COLOR_BARS.length) / VIDEO_WIDTH);
      const color = (x - stripeStart + VIDEO_WIDTH) % VIDEO_WIDTH < stripeWidth
        ? [255, 255, 255]
        : COLOR_BARS[barIndex];
      const pixelOffset = (y * VIDEO_WIDTH + x) * 4;

      frameData[pixelOffset] = color[0];
      frameData[pixelOffset + 1] = color[1];
      frameData[pixelOffset + 2] = color[2];
      frameData[pixelOffset + 3] = 255;
    }
  }

  return frameData;
}

async function publishVideoFrames(source, signal) {
  let frameNumber = 0;

  while (!signal.aborted) {
    const frameStartedAt = performance.now();
    const frame = new VideoFrame(
      createVideoFrameData(frameNumber),
      VIDEO_WIDTH,
      VIDEO_HEIGHT,
      VideoBufferType.RGBA,
    );

    source.captureFrame(frame);
    frameNumber++;

    const remainingTime = 1000 / VIDEO_FPS - (performance.now() - frameStartedAt);
    if (remainingTime > 0) {
      await wait(remainingTime);
    }
  }
}

async function publishAudioFrames(source, signal) {
  let sampleOffset = 0;

  while (!signal.aborted) {
    const frameStartedAt = performance.now();
    const frame = AudioFrame.create(AUDIO_SAMPLE_RATE, 1, AUDIO_FRAME_SAMPLES);

    for (let sample = 0; sample < AUDIO_FRAME_SAMPLES; sample++) {
      const sampleIndex = sampleOffset + sample;
      frame.data[sample] = Math.round(
        Math.sin((2 * Math.PI * 440 * sampleIndex) / AUDIO_SAMPLE_RATE) * 8192,
      );
    }

    await source.captureFrame(frame);
    sampleOffset += AUDIO_FRAME_SAMPLES;

    const remainingTime = AUDIO_FRAME_DURATION_MS - (performance.now() - frameStartedAt);
    if (remainingTime > 0) {
      await wait(remainingTime);
    }
  }
}

function sumInboundBytes(statsResult) {
  const totals = { total: 0n, audio: 0n, video: 0n };

  for (const stat of statsResult.subscriberStats) {
    if (stat.stats.case !== 'inboundRtp') {
      continue;
    }

    const inboundRtp = stat.stats.value;
    const bytesReceived = inboundRtp.inbound?.bytesReceived ?? 0n;
    totals.total += bytesReceived;

    if (inboundRtp.stream?.kind === 'audio') {
      totals.audio += bytesReceived;
    } else if (inboundRtp.stream?.kind === 'video') {
      totals.video += bytesReceived;
    }
  }

  return totals;
}

async function getInitialInboundSample(studentRoom) {
  const retryDeadline = performance.now() + CA_72_STATS_RETRY_MS;

  while (true) {
    const totals = sumInboundBytes(await studentRoom.getRtcStats());
    const sampledAt = performance.now();
    if (totals.total > 0n) {
      return { totals, sampledAt };
    }

    const remainingTime = retryDeadline - sampledAt;
    if (remainingTime <= 0) {
      return null;
    }

    await wait(Math.min(CA_72_SAMPLE_INTERVAL_MS, remainingTime));
  }
}

async function measureCa72(studentRoom) {
  const firstSample = await getInitialInboundSample(studentRoom);
  if (!firstSample) {
    console.error(
      '  CA 7.2 FAIL: no aparecieron estadísticas inbound_rtp con bytesReceived tras reintentar durante 5 segundos.',
    );
    assert(false, 'CA 7.2: el Alumno no recibió estadísticas inbound_rtp');
    return;
  }

  const samples = [firstSample];
  console.log(
    `  CA 7.2 muestra 1/6: bytesReceived=${firstSample.totals.total} (video=${firstSample.totals.video}, audio=${firstSample.totals.audio})`,
  );

  for (let sampleIndex = 1; sampleIndex <= CA_72_WINDOW_DURATION_MS / CA_72_SAMPLE_INTERVAL_MS; sampleIndex++) {
    const targetTime = firstSample.sampledAt + sampleIndex * CA_72_SAMPLE_INTERVAL_MS;
    const remainingTime = targetTime - performance.now();
    if (remainingTime > 0) {
      await wait(remainingTime);
    }

    const totals = sumInboundBytes(await studentRoom.getRtcStats());
    const sample = { totals, sampledAt: performance.now() };
    samples.push(sample);
    console.log(
      `  CA 7.2 muestra ${sampleIndex + 1}/6: bytesReceived=${totals.total} (video=${totals.video}, audio=${totals.audio})`,
    );
  }

  const lastSample = samples[samples.length - 1];
  const windowSeconds = (lastSample.sampledAt - firstSample.sampledAt) / 1000;
  const bytesInWindow = lastSample.totals.total - firstSample.totals.total;
  const averageKbps = (Number(bytesInWindow) * 8) / windowSeconds / 1000;
  const videoBytesInWindow = lastSample.totals.video - firstSample.totals.video;
  const audioBytesInWindow = lastSample.totals.audio - firstSample.totals.audio;

  console.log(
    `  CA 7.2 bytes recibidos en la ventana: ${bytesInWindow} bytes en ${windowSeconds.toFixed(2)} s; tasa media: ${averageKbps.toFixed(2)} kbps; video: ${videoBytesInWindow} bytes; audio: ${audioBytesInWindow} bytes`,
  );
  assert(lastSample.totals.total > 0n, 'CA 7.2: bytesReceived final mayor que cero');
  assert(
    lastSample.totals.total > firstSample.totals.total,
    'CA 7.2: bytesReceived aumentó durante la ventana',
  );
  assert(videoBytesInWindow > 0n, 'CA 7.2: aumentaron los bytes recibidos de video');
  assert(audioBytesInWindow > 0n, 'CA 7.2: aumentaron los bytes recibidos de audio');
}

async function measureCa73(studentRoom, tutorRoom, stopMediaProducers) {
  const eventTimes = {
    videoUnsubscribed: null,
    audioUnsubscribed: null,
    tutorDisconnected: null,
    studentDisconnected: null,
    fatalError: null,
  };
  let resolveExpectedEvents;
  const expectedEvents = new Promise((resolve) => {
    resolveExpectedEvents = resolve;
  });

  const markEvent = (key) => {
    if (eventTimes[key] === null) {
      eventTimes[key] = performance.now();
    }
    if (eventTimes.videoUnsubscribed !== null
      && eventTimes.audioUnsubscribed !== null
      && eventTimes.tutorDisconnected !== null) {
      resolveExpectedEvents();
    }
  };

  const onTrackUnsubscribed = (track, publication, participant) => {
    if (participant.identity !== 'tutor') {
      return;
    }
    if (publication.name === 'tutor-video') {
      markEvent('videoUnsubscribed');
    } else if (publication.name === 'tutor-audio') {
      markEvent('audioUnsubscribed');
    }
  };
  const onParticipantDisconnected = (participant) => {
    if (participant.identity === 'tutor') {
      markEvent('tutorDisconnected');
    }
  };
  const onStudentDisconnected = () => markEvent('studentDisconnected');
  const onEncryptionError = (error) => {
    if (eventTimes.fatalError === null) {
      eventTimes.fatalError = { at: performance.now(), error };
    }
  };
  const onUnhandledRejection = (reason) => {
    if (eventTimes.fatalError === null) {
      eventTimes.fatalError = { at: performance.now(), error: reason };
    }
  };
  const onUncaughtException = (error) => {
    if (eventTimes.fatalError === null) {
      eventTimes.fatalError = { at: performance.now(), error };
    }
  };

  studentRoom.on(RoomEvent.TrackUnsubscribed, onTrackUnsubscribed);
  studentRoom.on(RoomEvent.ParticipantDisconnected, onParticipantDisconnected);
  studentRoom.on(RoomEvent.Disconnected, onStudentDisconnected);
  studentRoom.on(RoomEvent.EncryptionError, onEncryptionError);
  process.once('unhandledRejection', onUnhandledRejection);
  process.once('uncaughtException', onUncaughtException);

  let timeout;
  let disconnectError;
  let disconnectCompleted = false;

  try {
    await stopMediaProducers();

    const startedAt = performance.now();
    const disconnectOperation = tutorRoom.disconnect().then(() => {
      disconnectCompleted = true;
    }).catch((error) => {
      disconnectError = error;
    });
    const timeoutOperation = new Promise((resolve) => {
      timeout = setTimeout(() => resolve('timeout'), 1000);
    });

    await Promise.race([
      Promise.all([disconnectOperation, expectedEvents]),
      timeoutOperation,
    ]);
    clearTimeout(timeout);

    const printLatency = (label, eventTime) => {
      if (eventTime === null) {
        console.log(`  CA 7.3 latencia ${label}: no recibido`);
      } else {
        console.log(`  CA 7.3 latencia ${label}: ${(eventTime - startedAt).toFixed(1)} ms`);
      }
    };
    printLatency('TrackUnsubscribed video', eventTimes.videoUnsubscribed);
    printLatency('TrackUnsubscribed audio', eventTimes.audioUnsubscribed);
    printLatency('ParticipantDisconnected del Tutor', eventTimes.tutorDisconnected);

    const assertEventLatency = (key, label) => {
      const latency = eventTimes[key] === null ? Infinity : eventTimes[key] - startedAt;
      assert(latency < 1000, `CA 7.3: ${label} recibido en menos de 1000 ms`);
    };
    assertEventLatency('videoUnsubscribed', 'TrackUnsubscribed de video');
    assertEventLatency('audioUnsubscribed', 'TrackUnsubscribed de audio');
    assertEventLatency('tutorDisconnected', 'ParticipantDisconnected del Tutor');

    if (disconnectError) {
      assert(false, 'CA 7.3: la desconexión del Tutor se completó sin error');
      console.error(redactSecrets(formatError(disconnectError), [
        process.env.LIVEKIT_API_KEY,
        process.env.LIVEKIT_API_SECRET,
      ]));
    }

    const studentStable = studentRoom.isConnected
      && studentRoom.connectionState === ConnectionState.CONN_CONNECTED
      && eventTimes.studentDisconnected === null;
    assert(studentStable, 'CA 7.3: el Alumno sigue conectado y estable tras la salida del Tutor');

    const noFatalError = eventTimes.fatalError === null;
    assert(noFatalError, 'CA 7.3: no hubo errores fatales en el cliente del Alumno');
    if (!noFatalError) {
      console.error(redactSecrets(formatError(eventTimes.fatalError.error), [
        process.env.LIVEKIT_API_KEY,
        process.env.LIVEKIT_API_SECRET,
      ]));
    }
  } finally {
    clearTimeout(timeout);
    studentRoom.off(RoomEvent.TrackUnsubscribed, onTrackUnsubscribed);
    studentRoom.off(RoomEvent.ParticipantDisconnected, onParticipantDisconnected);
    studentRoom.off(RoomEvent.Disconnected, onStudentDisconnected);
    studentRoom.off(RoomEvent.EncryptionError, onEncryptionError);
    process.removeListener('unhandledRejection', onUnhandledRejection);
    process.removeListener('uncaughtException', onUncaughtException);
  }
}

async function main() {
  const liveKitUrl = process.env.LIVEKIT_URL || 'ws://127.0.0.1:7880';
  const apiKey = process.env.LIVEKIT_API_KEY;
  const apiSecret = process.env.LIVEKIT_API_SECRET;
  const roomServiceUrl = liveKitUrl
    .replace(/^wss:/i, 'https:')
    .replace(/^ws:/i, 'http:');
  const roomName = `hu29-7-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const rooms = [];
  const tokens = [];
  const mediaProducerErrors = [];
  let mediaProducerController;
  let mediaProducers = [];
  let tutorDisconnectStarted = false;
  let roomServiceClient;

  console.log('\n======================================================');
  console.log('SkillBridge AI - LiveKit Stream Connection Test');
  console.log('======================================================\n');
  console.log(`URL de LiveKit: ${liveKitUrl}`);
  console.log(`Sala de prueba: ${roomName}\n`);

  try {
    const credentialsAvailable = Boolean(apiKey && apiSecret);
    assert(credentialsAvailable, 'LIVEKIT_API_KEY y LIVEKIT_API_SECRET están configuradas');
    if (!credentialsAvailable) {
      throw new Error('Faltan LIVEKIT_API_KEY o LIVEKIT_API_SECRET.');
    }

    roomServiceClient = new RoomServiceClient(roomServiceUrl, apiKey, apiSecret);

    const tutorToken = new AccessToken(apiKey, apiSecret, { identity: 'tutor' });
    tutorToken.addGrant({
      roomJoin: true,
      room: roomName,
      canPublish: true,
      canSubscribe: true,
    });

    const studentToken = new AccessToken(apiKey, apiSecret, { identity: 'alumno' });
    studentToken.addGrant({
      roomJoin: true,
      room: roomName,
      canPublish: false,
      canSubscribe: true,
    });

    tokens.push(await tutorToken.toJwt(), await studentToken.toJwt());

    const tutorRoom = new Room();
    const studentRoom = new Room();
    rooms.push({ role: 'Tutor', room: tutorRoom }, { role: 'Alumno', room: studentRoom });

    for (const { role, room } of rooms) {
      room.on(RoomEvent.Connected, () => printEvent(role, 'conectado a la sala'));
      room.on(RoomEvent.Disconnected, () => printEvent(role, 'desconectado de la sala'));
    }

    await tutorRoom.connect(liveKitUrl, tokens[0]);
    assert(tutorRoom.isConnected, 'Tutor conectado a la sala');

    await studentRoom.connect(liveKitUrl, tokens[1]);
    assert(studentRoom.isConnected, 'Alumno conectado a la sala');

    await wait(2000);
    assert(
      tutorRoom.isConnected && studentRoom.isConnected,
      'Tutor y Alumno permanecen conectados tras 2 segundos',
    );

    const videoSubscribedEvent = waitForTrackSubscribed(studentRoom, 'tutor-video');
    const audioSubscribedEvent = waitForTrackSubscribed(studentRoom, 'tutor-audio');
    const videoSource = new VideoSource(VIDEO_WIDTH, VIDEO_HEIGHT);
    const audioSource = new AudioSource(AUDIO_SAMPLE_RATE, 1);

    const videoTrack = LocalVideoTrack.createVideoTrack('tutor-video', videoSource);
    const audioTrack = LocalAudioTrack.createAudioTrack('tutor-audio', audioSource);
    mediaProducerController = new AbortController();
    mediaProducers = [
      publishVideoFrames(videoSource, mediaProducerController.signal),
      publishAudioFrames(audioSource, mediaProducerController.signal),
    ].map((producer) => producer.catch((error) => {
      if (!mediaProducerController.signal.aborted) {
        mediaProducerErrors.push(error);
      }
    }));

    if (!tutorRoom.localParticipant) {
      throw new Error('El Tutor no tiene participante local para publicar pistas.');
    }

    const videoPublishStartedAt = performance.now();
    const videoPublishPromise = tutorRoom.localParticipant.publishTrack(videoTrack, {
      source: TrackSource.SOURCE_CAMERA,
    }).then((publication) => {
      console.log(
        `  Latencia informativa hasta resolver publishTrack (video): ${(performance.now() - videoPublishStartedAt).toFixed(1)} ms`,
      );
      return publication;
    });

    const audioPublishStartedAt = performance.now();
    const audioPublishPromise = tutorRoom.localParticipant.publishTrack(audioTrack, {
      source: TrackSource.SOURCE_MICROPHONE,
    }).then((publication) => {
      console.log(
        `  Latencia informativa hasta resolver publishTrack (audio): ${(performance.now() - audioPublishStartedAt).toFixed(1)} ms`,
      );
      return publication;
    });

    const [videoSubscribedAt, audioSubscribedAt] = await Promise.all([
      videoSubscribedEvent,
      audioSubscribedEvent,
      videoPublishPromise,
      audioPublishPromise,
    ]);

    const videoSubscriptionLatency = videoSubscribedAt - videoPublishStartedAt;
    const audioSubscriptionLatency = audioSubscribedAt - audioPublishStartedAt;
    console.log(`  CA 7.1 latencia de suscripción video: ${videoSubscriptionLatency.toFixed(1)} ms`);
    console.log(`  CA 7.1 latencia de suscripción audio: ${audioSubscriptionLatency.toFixed(1)} ms`);
    assert(videoSubscriptionLatency < 2000, 'CA 7.1: el Alumno recibe video en menos de 2000 ms');
    assert(audioSubscriptionLatency < 2000, 'CA 7.1: el Alumno recibe audio en menos de 2000 ms');

    await measureCa72(studentRoom);
    await measureCa73(studentRoom, tutorRoom, async () => {
      mediaProducerController.abort();
      await Promise.allSettled(mediaProducers);
      tutorDisconnectStarted = true;
    });
  } catch (error) {
    const details = redactSecrets(formatError(error), [
      apiKey,
      apiSecret,
      ...tokens,
    ]);
    if (apiKey && apiSecret) {
      assert(false, 'La conexión o ejecución del test falló');
    }
    console.error('\nError completo (credenciales y tokens redactados):');
    console.error(details);
  } finally {
    if (mediaProducerController) {
      mediaProducerController.abort();
      await Promise.allSettled(mediaProducers);
    }

    for (const error of mediaProducerErrors) {
      assert(false, 'El generador de audio o video falló');
      console.error(redactSecrets(formatError(error), [apiKey, apiSecret, ...tokens]));
    }

    for (const { role, room } of rooms) {
      if (!room.isConnected || (role === 'Tutor' && tutorDisconnectStarted)) {
        continue;
      }

      try {
        await room.disconnect();
        assert(!room.isConnected, `${role} desconectado limpiamente`);
      } catch (error) {
        const details = redactSecrets(formatError(error), [apiKey, apiSecret, ...tokens]);
        assert(false, `No se pudo desconectar limpiamente a ${role}`);
        console.error(details);
      }
    }

    if (roomServiceClient) {
      try {
        await roomServiceClient.deleteRoom(roomName);
      } catch (error) {
        const details = redactSecrets(formatError(error), [apiKey, apiSecret, ...tokens]);
        console.warn(`\x1b[33mWARN:\x1b[0m No se pudo borrar la sala ${roomName}: ${details}`);
      }
    } else {
      console.warn('\x1b[33mWARN:\x1b[0m No se pudo intentar borrar la sala sin credenciales.');
    }

    try {
      await dispose();
    } catch (error) {
      const details = redactSecrets(formatError(error), [apiKey, apiSecret, ...tokens]);
      assert(false, 'No se pudo liberar el SDK de LiveKit');
      console.error(details);
    }
  }

  console.log('\n======================================================');
  console.log(`Pruebas ejecutadas: ${totalTests} | Aprobadas: ${passedTests} | Fallidas: ${failedTests}`);
  if (failedTests === 0) {
    console.log('\x1b[32m✔ CA 7.1, CA 7.2 y CA 7.3 verificados.\x1b[0m');
  } else {
    console.error('\x1b[31m✘ Prueba fallida: falló CA 7.1, CA 7.2, CA 7.3 o limpieza.\x1b[0m');
  }
  console.log('======================================================\n');

  process.exitCode = failedTests === 0 ? 0 : 1;
}

main().catch((error) => {
  const details = redactSecrets(formatError(error), [
    process.env.LIVEKIT_API_KEY,
    process.env.LIVEKIT_API_SECRET,
  ]);
  console.error('\x1b[31m✘ FAIL:\x1b[0m Error fatal no controlado.');
  console.error(details);
  process.exitCode = 1;
});
