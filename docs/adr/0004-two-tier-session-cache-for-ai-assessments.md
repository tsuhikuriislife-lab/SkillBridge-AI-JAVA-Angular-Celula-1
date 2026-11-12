# ADR-0004: Caché en Dos Niveles (Redis + Memoria Local) para Sesiones de Diagnóstico IA

## Estado
Aceptado.

## Contexto
El diagnóstico técnico previo (*Mini-Assessment*) genera un conjunto de preguntas y opciones, almacenando de forma segura las respuestas correctas y sus justificaciones pedagógicas en el backend para evitar que el estudiante haga trampa inspeccionando el payload en el navegador.

Dado que la generación por IA consume tiempo y tokens, la sesión generada debe persistir durante el tiempo en que el estudiante responde el examen (típicamente 5 a 15 minutos). Sin embargo, una falla temporal de conectividad con Redis no debe impedir que un usuario complete o califique su diagnóstico.

## Decisión
Implementar el puerto `AssessmentSessionCachePort` mediante un adaptador de doble nivel (`RedisAssessmentCacheAdapter`):
1. **Nivel Primario Distribuido (Redis 8):** Las sesiones se serializan como JSON y se almacenan con clave `assessment:session:{id}` y un TTL configurable (30 minutos por defecto).
2. **Nivel Secundario de Respaldo (Memoria Local):** Simultáneamente, cada sesión se almacena en un mapa concurrente thread-safe (`ConcurrentHashMap<UUID, LocalCacheEntry>`) con marca de tiempo de expiración.
3. **Mecanismo de Lectura Resiliente:** El adaptador intenta primero consultar Redis. Si la lectura arroja una excepción de timeout, conexión o serialización, captura el fallo y recurre inmediatamente a la caché local sin propagar un error 500 al cliente.

## Consecuencias
- **Positivas:**
  - **Alta Disponibilidad:** La funcionalidad de diagnóstico continúa operando incluso ante caídas o reinicios temporales de Redis.
  - **Seguridad Garantizada:** El cliente solo recibe las preguntas y opciones (`ClientAssessmentQuestionResponse`). Las respuestas correctas residen exclusivamente en el servidor durante la ventana de evaluación.
  - **Eficiencia de Memoria:** El TTL de 30 minutos previene la fuga de memoria y el almacenamiento indefinido de diagnósticos abandonados.
- **Negativas / Desafíos:**
  - En un despliegue horizontal multi-instancia sin afinidad de sesión (sticky sessions), si Redis cae por completo, un cliente que generó el examen en el nodo A y envía las respuestas al nodo B requerirá que Redis esté disponible para compartir la sesión. No obstante, en la arquitectura monolítica modular actual este riesgo es mínimo y se mitiga completamente cuando Redis está activo.
