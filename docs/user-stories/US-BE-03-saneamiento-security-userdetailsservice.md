# US-BE-03: Saneamiento de Configuración de Seguridad y UserDetailsService

**Estado:** `[ ] Por hacer`

---

## 1. Descripción y Objetivo de Ingeniería
Corregir la advertencia de Spring Security en tiempo de inicio y ejecución de pruebas provocada por la coexistencia simultánea de múltiples beans de tipo `UserDetailsService`, eliminando contraseñas autogeneradas en el log y garantizando un contexto de seguridad determinista.

---

## 2. Criterios de Aceptación y Arquitectura
1. **Unificación de Bean de Autenticación:** Configurar explícitamente `DatabaseUserDetailsService` como proveedor primario (`@Primary`) o remover la definición en memoria huérfana de las clases de configuración.
2. **Cero Advertencias en Logs:** Asegurar que la ejecución de `mvn test` no reporte advertencias de seguridad ni passwords efímeras generadas por defecto.

---

## 3. Componentes Técnicos y Referencias de Implementación
- Configuración de filtros y proveedores de seguridad:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/configuration/security/SecurityConfiguration.java` línea 30:95)*.
- Adaptador de servicio de detalles de usuario:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/security/DatabaseUserDetailsService.java` línea 15:45)*.
