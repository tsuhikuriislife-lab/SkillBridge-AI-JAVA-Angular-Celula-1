# US-BE-02: Cobertura de Pruebas en Servicios de Aplicación y Adaptadores JPA Outbound

**Estado:** `[ ] Por hacer`

---

## 1. Descripción y Objetivo de Ingeniería
Implementar pruebas unitarias con Mockito para los servicios de aplicación con 0% de cobertura (`AuthService`, `AssessmentService`, `AiRecommendationService`) y pruebas de integración livianas (`@DataJpaTest`) para los adaptadores de persistencia relacional.

---

## 2. Criterios de Aceptación y Arquitectura
1. **Unit Tests de Casos de Uso:** Aislar la lógica de negocio simulando los puertos de salida (`out`), probando flujos felices, excepciones de dominio y validaciones de negocio.
2. **Pruebas de Adaptadores Outbound:** Verificar la conversión bidireccional entre entidades JPA de infraestructura y modelos puros de dominio en los adaptadores de persistencia.

---

## 3. Componentes Técnicos y Referencias de Implementación
- Casos de uso de autenticación y evaluaciones:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/application/service/AuthService.java` línea 25:80)*.
  *(Ver `backend/src/main/java/com/riwi/skillbridge/application/service/AssessmentService.java` línea 20:75)*.
- Adaptadores de persistencia JPA:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/OfferingPersistenceAdapter.java` línea 15:55)*.
