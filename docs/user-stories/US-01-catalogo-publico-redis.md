# US-01: Catálogo Público de Servicios con Patrón Cache-Aside en Redis

**Estado:** `[✅] Terminada`

---

## 1. Descripción y Objetivo de Ingeniería
Permitir a los visitantes y usuarios consultar los servicios y mentorías activos con alta velocidad de respuesta mediante el patrón Cache-Aside sobre Redis y persistencia relacional en PostgreSQL.

---

## 2. Arquitectura de la Solución (Hexagonal & Caché)
1. **Endpoint REST Inbound:** `GET /api/offerings` gestionado por `OfferingController`.
2. **Capa de Aplicación:** `OfferingCatalogService` consulta primero la caché Redis. En caso de acierto (cache hit) retorna la respuesta en tiempo submilisegundo; en caso de fallo (cache miss), consulta PostgreSQL y repuebla la caché con TTL configurado.

---

## 3. Componentes Técnicos y Referencias de Implementación
- Controlador de catálogo público:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/OfferingController.java` línea 25:50)*.
- Caso de uso de catálogo:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/application/service/OfferingCatalogService.java` línea 20:60)*.
- Adaptador de persistencia relacional:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/OfferingPersistenceAdapter.java` línea 20:45)*.
