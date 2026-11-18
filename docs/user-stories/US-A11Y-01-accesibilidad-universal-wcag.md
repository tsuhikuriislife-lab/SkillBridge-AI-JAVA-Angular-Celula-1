# US-A11Y-01: Accesibilidad Web Universal bajo Estándar WCAG 2.1 AA

**Estado:** `[ ] Por hacer`

---

## 1. Descripción y Objetivo de Ingeniería

Garantizar que la plataforma web de SkillBridge AI sea accesible e inclusiva para todas las personas, incluidas aquellas con discapacidades visuales, motrices o cognitivas, alcanzando la conformidad con las pautas de accesibilidad **WCAG 2.1 nivel AA**. Se implementará navegación íntegra por teclado, trampas de foco en modales, regiones dinámicas ARIA (`aria-live`), ratios de contraste cromático adecuados y soporte completo para lectores de pantalla (NVDA, JAWS, VoiceOver).

---

## 2. Arquitectura de la Solución (Frontend Accessible Components & UX)

1. **Gestión de Foco y Navegación por Teclado:**
   - Incorporación del enlace "Saltar al contenido principal" (`skip-to-content`) al inicio del DOM para usuarios de teclado.
   - Indicadores visibles y destacados de foco (`:focus-visible`) con contorno accesible en todos los elementos interactivos (botones, inputs, enlaces, tarjetas clickeables).
   - Implementación de foco atrapado (Focus Trap) en diálogos y modales (`MeetingRoomComponent`, modales de confirmación de reserva) para impedir que el tabulador se desplace al fondo inactivo.
2. **Semántica HTML5 y Atributos ARIA:**
   - Reemplazo de estructuras `<div>` con eventos `(click)` por elementos nativos `<button>` o adición explícita de `role="button"`, `tabindex="0"` y `(keydown.enter)` / `(keydown.space)`.
   - Señalización de estados dinámicos: `aria-expanded` en menús desplegables, `aria-busy` durante cargas asíncronas y `aria-invalid` con `aria-describedby` en errores de formulario.
   - Configuración de regiones en vivo `aria-live="polite"` en `ToastService` para que los anuncios de éxito o error sean narrados automáticamente por los lectores de pantalla.
3. **Auditoría de Color y Tipografía:**
   - Ajuste de paleta de colores en variables CSS globales para asegurar un ratio de contraste mínimo de **4.5:1** para texto normal y **3:1** para texto grande e iconografía interactiva.
   - Compatibilidad total con zoom del navegador hasta el 200% sin ruptura de maquetación ni pérdida de funcionalidad.

---

## 3. Componentes Técnicos y Referencias de Implementación

- Plantilla principal y navegación de la aplicación:
  *(Ver `frontend/src/app/app.component.ts`)*.
- Servicio y contenedor de toasts/notificaciones accesibles:
  *(Ver `frontend/src/app/core/toast.service.ts`)*.
- Componente de sala de reuniones LiveKit:
  *(Ver `frontend/src/app/features/meeting-room/meeting-room.component.html`)*.
- Formulario de autenticación accesible:
  *(Ver `frontend/src/app/features/login/login.html`)*.

---

## 4. Criterios de Aceptación Técnicos

- **CA-A11Y-01.1:** Todos los flujos críticos (login, exploración de catálogo, detalle de servicio, reserva y controles de la sala de videoconferencia) deben ser 100% operables utilizando exclusivamente el teclado (Tab, Shift+Tab, Enter, Espacio, Escape).
- **CA-A11Y-01.2:** Cada imagen y gráfico informativo debe contar con un texto alternativo descriptivo (`alt="..."`), y los íconos puramente decorativos deben incluir `aria-hidden="true"`.
- **CA-A11Y-01.3:** La auditoría automatizada con herramientas como Google Lighthouse o Axe Core debe arrojar una puntuación de Accesibilidad de **$\ge 95/100$** en todas las vistas principales de la aplicación.
