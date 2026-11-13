# Catálogo de Historias de Usuario (US)

Este directorio centraliza la especificación técnica y trazabilidad de todas las historias de usuario y tareas de ingeniería desarrolladas en **SkillBridge AI**.

---

## 1. Reglas de Documentación

Conforme a las directrices de `AGENTS.md`, cada documento debe cumplir estrictamente con los siguientes estándares:

1. **Nomenclatura de Archivo:**
   `US-<CODIGO>-<descripcion-corta>.md` (ej. `US-PS06-feature-store.md`).

2. **Campo de Estado Obligatorio:**
   Cada historia debe comenzar con uno de los siguientes estados:
   - `[ ] Por hacer`
   - `[🔄] En curso`
   - `[👀] En revisión`
   - `[✅] Terminada`

3. **Profundidad y Enfoque Técnico:**
   - Detallar la solución arquitectónica implementada (patrones hexagonales, puertos, adaptadores, eventos de dominio, consistencia de datos).
   - Indicar contratos de API, payloads de mensajería y estrategias de resiliencia (reintentos, DLQ, fallbacks).

4. **Prohibición de Pegado de Código Fuente Masivo:**
   - **NO** pegar bloques extensos de código.
   - Toda mención al código debe utilizar referencias puntuales relativas con rango de líneas exacto:
     > *(Ej: `Ver backend/src/main/java/com/riwi/skillbridge/application/service/FeatureStoreService.java línea 32:56`)*.

---

## 2. Índice de Historias de Usuario

| Código | Título | Estado | Módulo Principal | Documento |
|---|---|---|---|---|
| **US-PS06** | Feature Store con Redis y Fallback PostgreSQL | `[✅] Terminada` | Backend / Analytics | [`US-PS06-feature-store.md`](US-PS06-feature-store.md) |
| **US-PS02** | Pipeline ETL de Extracción y Limpieza de Catálogo | `[✅] Terminada` | Analytics / PySpark | [`US-PS02-etl-catalogo.md`](US-PS02-etl-catalogo.md) |
| **US-29.11**| Videoconferencias en Tiempo Real con LiveKit | `[✅] Terminada` | Backend / Frontend | [`US-29.11-videoconferencias-livekit.md`](US-29.11-videoconferencias-livekit.md) |
