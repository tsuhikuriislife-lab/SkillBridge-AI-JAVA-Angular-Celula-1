# US-AUTH-05: Validación Google OAuth2 y Recuperación de Contraseña

**Estado:** `[ ] Por hacer`

---

## 1. Descripción y Objetivo de Ingeniería

Proveer mecanismos modernos de validación de identidad y recuperación de acceso para los usuarios de SkillBridge AI. Permite el inicio de sesión e inscripción directa mediante cuentas de Google (OAuth2 / OpenID Connect) y un flujo desacoplado y seguro de restablecimiento de contraseña mediante tokens temporales enviados por correo electrónico a través del sistema de eventos en RabbitMQ.

---

## 2. Arquitectura de la Solución (Hexagonal & EDA)

1. **Flujo de Google OAuth2 (Sign-In con Gmail):**
   - El frontend consume el flujo OAuth2 de Google o envía el `id_token` firmado por Google hacia el endpoint `POST /api/auth/google`.
   - `GoogleAuthVerificationAdapter` (implementando `OAuthVerificationPort`) valida la firma del token criptográfico contra los servidores de Google usando el SDK oficial de Google API Client.
   - Si el usuario no existe, se aprovisiona automáticamente en `app_users` con rol `STUDENT` y estado `ACTIVE`. Se emite la cookie segura HttpOnly con el JWT de SkillBridge.
2. **Flujo de Recuperación de Contraseña:**
   - Endpoint `POST /api/auth/forgot-password` recibe el email del usuario.
   - Generación de token criptográficamente seguro (UUIDv4 + SHA-256) con expiración de 15 minutos en tabla `password_reset_tokens`.
   - Publicación asíncrona del evento de dominio `PasswordResetRequestedEvent` en el exchange `skillbridge.exchange` de RabbitMQ.
   - Servicio de notificaciones consume el evento y despacha el correo transaccional con la URL temporal `/reset-password?token=...`.
   - Endpoint `POST /api/auth/reset-password` valida vigencia del token y actualiza la contraseña con hash BCrypt.

---

## 3. Componentes Técnicos y Referencias de Implementación

- Modelo de usuario y ciclo de vida de credenciales:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/domain/model/UserAccount.java` línea 10:45)*.
- Adaptador de persistencia de usuarios:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/UserPersistenceAdapter.java` línea 25:65)*.
- Configuración de seguridad Spring Security y filtros JWT:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/security/SecurityConfiguration.java` línea 40:85)*.
- Servicio de notificaciones y RabbitMQ producer:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/application/service/BookingHistoryService.java` línea 30:50)*.

---

## 4. Criterios de Aceptación Técnicos

- **CA-AUTH-05.1:** La autenticación con Google debe validar rigurosamente la audiencia (`aud`), emisor (`iss`) y firma criptográfica de Google Identity antes de emitir la sesión interna.
- **CA-AUTH-05.2:** La solicitud de recuperación de contraseña siempre debe retornar código HTTP 200 genérico ("Si el correo existe, se han enviado instrucciones") para prevenir ataques de enumeración de usuarios.
- **CA-AUTH-05.3:** Cada token de restablecimiento de contraseña debe ser de un solo uso (`used = true`) y quedar invalidado inmediatamente después de su primer consumo o al cumplirse el TTL de 15 minutos.
