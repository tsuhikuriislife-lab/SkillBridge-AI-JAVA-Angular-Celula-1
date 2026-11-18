# US-FE-02: Cobertura de Pruebas Unitarias en Componentes Críticos LiveKit y Checkout

**Estado:** `[ ] Por hacer`

---

## 1. Descripción y Objetivo de Ingeniería
Implementar la suite de pruebas unitarias para los dos componentes frontend con mayor impacto en la experiencia del usuario y actualmente con 0% de cobertura: la sala de videoconferencias WebRTC (`MeetingRoomComponent`) y el flujo de contratación y pago (`CheckoutComponent`).

---

## 2. Criterios de Aceptación y Arquitectura
1. **Simulación de WebRTC (LiveKit):** Crear `meeting-room.component.spec.ts` simulando el SDK de LiveKit (`Room`, eventos de conexión, toggles de audio y video) y validando la liberación de recursos en `ngOnDestroy`.
2. **Pruebas de Flujo de Checkout:** Crear `checkout.component.spec.ts` validando el manejo de formularios reactivos, cálculo de montos y manejo de errores HTTP al confirmar reservas.

---

## 3. Componentes Técnicos y Referencias de Implementación
- Componente de sala de reunión LiveKit:
  *(Ver `frontend/src/app/features/meeting-room/meeting-room.component.ts` línea 1:85)*.
- Componente de checkout y pago:
  *(Ver `frontend/src/app/features/checkout/checkout.component.ts` línea 1:70)*.
