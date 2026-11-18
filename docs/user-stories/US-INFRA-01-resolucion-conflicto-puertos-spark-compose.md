# US-INFRA-01: Resolución de Conflicto de Puertos en Spark Worker y Perfiles de Compose

**Estado:** `[ ] Por hacer`

---

## 1. Descripción y Objetivo de Ingeniería
Resolver la colisión de binding en el puerto host 8081 detectada entre el contenedor `backend` y `spark-worker`. Homogeneizar la activación del clúster de Apache Spark bajo el perfil `data` y habilitar la persistencia de reglas de alerta en Prometheus para evitar fallas en la observabilidad de LiveKit.

---

## 2. Criterios de Aceptación y Arquitectura
1. **Desacoplamiento de Puertos Host:** El puerto del UI de `spark-worker` debe remapearse a `8082:8081` en `docker-compose.yml`, permitiendo que `backend` use `8081:8080` sin conflicto de bind.
2. **Perfil `data` Homogéneo:** Tanto `spark-master` como `spark-worker` deben contar con `profiles: ["data"]`, de modo que no se inicien en el stack estándar transaccional (`docker compose up`).
3. **Observabilidad Prometheus:** Montar el volumen de reglas `./ops/prometheus/rules:/etc/prometheus/rules:ro` en el servicio `prometheus` y ajustar el patrón de carga en `prometheus.yml`.

---

## 3. Componentes Técnicos y Referencias de Implementación
- Modificación del servicio `backend` y `spark-worker`:
  *(Ver `docker-compose.yml` línea 25:35 y 110:135)*.
- Modificación del servicio `prometheus` en compose:
  *(Ver `docker-compose.yml` línea 80:95)*.
- Configuración de reglas en Prometheus:
  *(Ver `ops/prometheus/prometheus.yml` línea 10:20)*.
