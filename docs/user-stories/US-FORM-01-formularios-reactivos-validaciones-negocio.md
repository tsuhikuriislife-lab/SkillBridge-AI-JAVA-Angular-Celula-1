# US-FORM-01: Formularios Reactivos con Validación y Reglas de Negocio

**Estado:** `[ ] Por hacer`

---

## 1. Descripción y Objetivo de Ingeniería

Elevar la robustez, consistencia y experiencia de usuario en todos los formularios de SkillBridge AI. Reemplaza formularios basados en plantillas (`FormsModule`) por formularios reactivos fuertemente tipados (`ReactiveFormsModule` de Angular 20) con validaciones asíncronas en tiempo real, debounce para optimización de llamadas de red, señalización visual accesible de errores y estricta congruencia con las reglas de negocio del dominio en el backend.

---

## 2. Arquitectura de la Solución (Frontend Reactivo & Backend Invariants)

1. **Frontend (Angular 20 Standalone):**
   - Tipado estricto con `FormGroup<{ [key]: FormControl<T> }>`.
   - Validadores personalizados síncronos: fortaleza de contraseña (mínimo 8 caracteres, mayúscula, número, caracter especial), comparación de contraseñas (`confirmPassword`), fechas futuras para programación de mentorías y rangos de precios válidos.
   - Validadores asíncronos con debounce (300ms) contra endpoints del backend para comprobación de disponibilidad de email (`emailAvailabilityValidator`) y código único de servicio.
   - Componente reutilizable de control de errores de formulario (`<app-form-error control="..." [messages]="...">`).
2. **Backend (Bean Validation & Domain Invariants):**
   - Refuerzo de anotaciones Jakarta Validation (`@NotNull`, `@NotBlank`, `@Size`, `@PositiveOrZero`, `@FutureOrPresent`) en todos los DTOs de entrada (`*Request.java`).
   - Verificación de invariantes en constructores de modelos de dominio puros (`domain/model/`), lanzando `BusinessRuleException` si las reglas no se cumplen.
   - Mapeo estructurado de `MethodArgumentNotValidException` a RFC 7807 en `GlobalExceptionHandler` retornando el mapa completo campo -> mensaje amigable.

---

## 3. Componentes Técnicos y Referencias de Implementación

- Manejador global de excepciones y validaciones:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/GlobalExceptionHandler.java` línea 43:52)*.
- DTO de creación/edición de servicios:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/dto/ServiceUpsertRequest.java`)*.
- Formulario de autenticación en frontend:
  *(Ver `frontend/src/app/features/login/login.ts` línea 20:30)*.
- Formulario de creación de ofertas/servicios:
  *(Ver `frontend/src/app/features/provider/create-offering.component.ts`)*.

---

## 4. Criterios de Aceptación Técnicos

- **CA-FORM-01.1:** Todos los formularios deben deshabilitar el botón de envío y evitar peticiones HTTP innecesarias mientras el formulario se encuentre en estado `invalid` o `pending`.
- **CA-FORM-01.2:** Cada campo inválido debe contar con el atributo `aria-invalid="true"` y estar asociado a su mensaje de error mediante `aria-describedby` para compatibilidad con lectores de pantalla.
- **CA-FORM-01.3:** El backend debe responder con HTTP `400 Bad Request` y un arreglo detallado de violaciones de validación cuando el payload recibido no cumpla con las restricciones de Bean Validation.
