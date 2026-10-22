# SkillBridge AI - Instrumentación y Monitoreo de Métricas en LiveKit

Este documento describe la arquitectura, configuración, telemetría y reglas de alerta implementadas para el servidor de videoconferencias en tiempo real **LiveKit**, orientado a la observabilidad SRE/DevOps.

---

## 1. Arquitectura y Puertos de Red

LiveKit Server se integra en la topología de contenedores (`docker-compose.yml`) compartiendo la red interna `skillbridge` con Redis y Prometheus.

| Servicio / Puerto | Protocolo | Función |
| :--- | :--- | :--- |
| `7880` | TCP / HTTP & WebSocket | Señalización, autenticación y API de control |
| `7881` | TCP | Canal de contingencia RTC (fallback cuando UDP está bloqueado) |
| `6789` | TCP / HTTP (`/metrics`) | Exportador de métricas Prometheus |
| `50000-50020` | UDP | Transporte de medios WebRTC (Audio, Video y DataChannels) |

### Backend de Estado:
- **Redis (`redis:6379`)**: Gestiona la coordinación distribuida de salas y participantes para escalabilidad horizontal y balanceo.

---

## 2. Diccionario de Métricas Críticas

| Métrica | Tipo | Etiquetas comunes | Descripción |
| :--- | :--- | :--- | :--- |
| `livekit_room_total` | Gauge | `job="livekit"`, `instance` | Número total de salas de clase activas en el nodo SFU |
| `livekit_participant_total` | Gauge | `job="livekit"`, `instance` | Total de participantes conectados simultáneamente |
| `livekit_packet_total` | Counter | `job="livekit"`, `instance`, `track` | Conteo acumulado de paquetes WebRTC procesados |
| `livekit_packet_drop_total` | Counter | `job="livekit"`, `instance` | Conteo acumulado de paquetes descartados o perdidos |
| `livekit_jitter_us` | Gauge / Summary | `job="livekit"`, `instance` | Variación de retardo en microsegundos |
| `livekit_server_build_info` | Gauge (recording rule) | `job="livekit"`, `instance`, `version` | Valor 1 cuando el target está UP; `version` refleja el tag de imagen configurado (`v1.8`), no una métrica nativa de LiveKit |

---

## 3. Reglas de Agregación PromQL (Recording Rules)

Ubicadas en `ops/prometheus/rules/livekit-rules.yml`:

### Build configurado de LiveKit
La regla `livekit_server_build_info` usa `up{job="livekit"}` y agrega la etiqueta `version="v1.8"`, correspondiente a la imagen configurada para LiveKit en `docker-compose.yml`. Se puede consultar con:
```promql
livekit_server_build_info{job="livekit"}
```
La serie solo existe mientras el target de LiveKit esté `UP`. Es una recording rule de Prometheus, no una métrica exportada nativamente por LiveKit.

### Concurrencia de Usuarios y Salas (CA 9.1)
```promql
# Total de participantes activos en el cluster
job:livekit_participant_total:sum = sum(livekit_participant_total) by (job)

# Total de salas activas
job:livekit_room_total:sum = sum(livekit_room_total) by (job)

# Promedio de participantes por sala
job:livekit_participants_per_room:avg = sum(livekit_participant_total) by (job) / clamp_min(sum(livekit_room_total) by (job), 1)
```

### Throughput y Tasa de Paquetes Procesados (CA 9.2)
```promql
# Tasa global de paquetes procesados por segundo (pps)
job:livekit_packet_rate:sum = sum(rate(livekit_packet_total[1m])) by (job)

# Tasa de paquetes por pista activa individual
track:livekit_packet_rate:rate1m = rate(livekit_packet_total[1m])
```

### Tasa Porcentual de Pérdida de Paquetes (CA 9.3)
```promql
# Porcentaje de paquetes perdidos en ventana de 2 minutos
job:livekit_packet_loss_percentage:rate2m = 
  (sum(rate(livekit_packet_drop_total[2m])) by (job, instance) 
  / clamp_min(sum(rate(livekit_packet_total[2m])) by (job, instance), 1)) * 100
```

### Variación de Retardo (Jitter en Milisegundos)
```promql
job:livekit_jitter_ms:avg = avg(livekit_jitter_us) by (job, instance) / 1000
```

### Disponibilidad de Transmisión por Participante
```promql
job:livekit_participant_transmission_availability:ratio = 
  count(rate(livekit_packet_total[1m]) > 0) by (job) 
  / clamp_min(sum(livekit_participant_total) by (job), 1)
```

---

## 4. Reglas de Alerta (Alerting Rules)

### 4.1 CA 9.3: Alerta por Pérdida de Paquetes > 5% (`LiveKitHighPacketLoss`)
- **Expresión:** `(sum(rate(livekit_packet_drop_total[2m])) by (instance) / clamp_min(sum(rate(livekit_packet_total[2m])) by (instance), 1)) * 100 > 5`
- **Ventana de persistencia (`for`):** `1m`
- **Severidad:** `critical`
- **Impacto:** Detección inmediata de enlaces inestables o saturación de ancho de banda que degrada la inteligibilidad del audio y la fluidez del video.

### 4.2 Jitter Elevado (`LiveKitHighJitter`)
- **Expresión:** `(avg(livekit_jitter_us) by (instance) / 1000) > 30`
- **Ventana de persistencia (`for`):** `2m`
- **Severidad:** `warning`
- **Impacto:** Variación de retardo excesiva (>30ms) provocando buffering o artefactos de voz robótica.

### 4.3 Saturación de Concurrencia (`LiveKitRoomSaturation`)
- **Expresión:** `livekit_participant_total > 50`
- **Ventana de persistencia (`for`):** `2m`
- **Severidad:** `warning`
- **Impacto:** Alerta de capacidad operativa para balancear o escalar nodos SFU.

### 4.4 Desconexión Masiva (`LiveKitMassiveDisconnection`)
- **Expresión:** `(delta(livekit_participant_total[1m]) < -10) and (livekit_room_total > 0)`
- **Ventana de persistencia (`for`):** `30s`
- **Severidad:** `critical`
- **Impacto:** Caída abruptly masiva de alumnos durante una clase activa.

---

## 5. Dashboard en Grafana (`livekit.json`)

Se encuentra provisionado automáticamente en `ops/grafana/provisioning/dashboards/livekit.json` bajo el UID `skillbridge-livekit`:

1. **Salas Activas** (Stat panel)
2. **Participantes Activos** (Stat panel con umbrales visuales, CA 9.1)
3. **Promedio Usuarios / Sala** (Stat panel)
4. **Disponibilidad de Transmisión** (Gauge panel con semáforo porcentual)
5. **Tasa de Paquetes Procesados** (Time series pps global y por pista, CA 9.2)
6. **Tasa de Pérdida de Paquetes (%)** (Time series con línea de umbral en 5%, CA 9.3)
7. **Variación de Retardo (Jitter en ms)** (Time series con umbral de 30ms)

---

## 6. Validación de Criterios de Aceptación

Para ejecutar la suite automatizada de validación:
```bash
# Vía make
make test-livekit

# Vía node
node scripts/validate-livekit-metrics.js
```
