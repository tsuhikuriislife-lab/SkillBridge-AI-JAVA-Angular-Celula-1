#!/usr/bin/env node

const { AccessToken, RoomServiceClient } = require('livekit-server-sdk');
const { Room, RoomEvent, dispose } = require('@livekit/rtc-node');

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
    for (const { role, room } of rooms) {
      if (!room.isConnected) {
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
    console.log('\x1b[32m✔ Conexión de Tutor y Alumno completada exitosamente.\x1b[0m');
  } else {
    console.error('\x1b[31m✘ HU incompleta: falló una validación de conexión o limpieza.\x1b[0m');
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
