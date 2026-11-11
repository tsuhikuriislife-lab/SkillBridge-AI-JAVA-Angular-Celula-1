# Configuración: dominio (Hostinger), Resend, API keys y variables por capa

> **Responsables:** Dylan (compra del dominio y dueño de la cuenta de Resend) y Persona C (DNS y verificación).
> **Hazlo PRIMERO** (Bloque 0): la propagación DNS puede tardar desde minutos hasta algunas horas.

## Resumen: ¿qué cambia en cada capa?

| Capa | ¿Cambia? | Qué | Dónde |
|---|---|---|---|
| **Dominio / DNS** | ✅ Sí | 3 registros de Resend + 1 DMARC | Hostinger hPanel (§2) |
| **Resend** | ✅ Sí | Dominio verificado + API key | resend.com (§3) |
| **`.env` raíz (local, NO se sube)** | ✅ Sí | `EMAIL_DRIVER`, `RESEND_API_KEY`, `MAIL_FROM`, `MAIL_REPLY_TO`, `MAIL_REDIRECT_TO`, `FRONTEND_URL` | §4 |
| **`.env.example` raíz (sí se sube)** | ✅ Sí | Las mismas variables **sin valores secretos** | Guía C, Paso 5 |
| **`docker-compose.yml`** | ✅ Sí | Servicio `notifications` | Guía C, Paso 5 |
| **Backend (`application.yml`)** | ❌ No | Reutiliza `RABBITMQ_URL`, que ya existe. El nombre del exchange es una constante del contrato. **No hay secretos nuevos.** | — |
| **Backend (código)** | ✅ Sí | Publicación de eventos | Guía A |
| **Base de datos** | ❌ No (v1) | No hay tablas nuevas → **no hay migración Flyway** (§6) | — |
| **Frontend (Angular)** | ❌ No | El frontend **nunca** habla con Resend ni conoce la API key | §7 |
| **GitHub Actions** | ❌ Sin secretos | Las pruebas usan clientes falsos; sólo se agrega un job (Guía C, Paso 7) | — |
| **Producción / nube** | ✅ Sí | Variables del servicio `notifications` en el proveedor | §8 |

---

## 1. Comprar el dominio en Hostinger

1. Entra a <https://hpanel.hostinger.com> → **Dominios** → **Obtener un nuevo dominio**.
2. Busca el nombre (por ejemplo `skillbridge-ai.com`, `skillbridgeai.co`, etc.) y cómpralo.
3. Durante la compra activa **Protección de privacidad WHOIS** (gratis en Hostinger). Así tus datos personales no quedan públicos.
4. Espera a que el estado del dominio sea **Activo** (hPanel → Dominios → Portafolio de dominios). Suele tardar unos minutos.
5. **No cambies los nameservers.** Por defecto Hostinger administra el DNS (`ns1.dns-parking.com` / `ns2.dns-parking.com`), y ahí se agregan los registros. Si algún día pasan el DNS a Cloudflare, los registros de §2 se agregan **allá**.

> En esta guía usamos `tudominio.com` como ejemplo. **Reemplázalo** por el dominio real.

### ¿Por qué enviar desde un subdominio (`mail.tudominio.com`)?

Recomendado por Resend:
- **Aísla la reputación:** si los correos transaccionales tienen problemas (rebotes), no afectan al dominio principal (web y correo corporativo).
- **Evita conflictos de SPF:** Hostinger suele crear un registro SPF en la raíz (`@`) para su propio correo. Al usar `mail.`, los registros de Resend no chocan con esos.

Remitente final: `SkillBridge AI <notificaciones@mail.tudominio.com>`.

---

## 2. Verificar el dominio en Resend y crear los registros DNS en Hostinger

### 2.1 En Resend
1. Crea la cuenta en <https://resend.com> (Dylan es dueño). Plan gratis: 3.000 correos al mes, 100 al día y 1 dominio, suficiente para el proyecto.
2. **Domains → Add Domain**.
   - Name: `mail.tudominio.com`
   - Region: `South America (São Paulo) – sa-east-1` (la más cercana a Colombia) o la que sugiera Resend.
3. Resend muestra una tabla de registros. **Copia los valores EXACTOS de tu pantalla.** Se verán así:

| Tipo | Nombre (Host) | Valor | Prioridad |
|---|---|---|---|
| MX | `send.mail` | `feedback-smtp.sa-east-1.amazonses.com` | `10` |
| TXT | `send.mail` | `v=spf1 include:amazonses.com ~all` | — |
| TXT | `resend._domainkey.mail` | `p=MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQ...` (larga, única para tu cuenta) | — |

- **MX en `send.mail`:** recibe los rebotes y quejas, que Resend procesa automáticamente. **No** afecta a tu correo normal.
- **SPF (TXT):** autoriza a los servidores de Resend (Amazon SES) a enviar en nombre de tu subdominio.
- **DKIM (TXT):** firma criptográfica. Gmail y Outlook la usan para confirmar que el correo es auténtico y no fue alterado.

### 2.2 En Hostinger (hPanel)
1. **Dominios → Portafolio de dominios →** tu dominio **→ Administrar → DNS / Nameservers →** pestaña **Registros DNS**.
2. Por cada fila de la tabla de Resend, en **"Administrar registros DNS"**:
   - **Tipo:** MX o TXT.
   - **Nombre:** sólo la parte del host, **sin** el dominio: `send.mail`, `resend._domainkey.mail`. Hostinger agrega `.tudominio.com` solo.
   - **Apunta a / Contenido / Valor:** pega el valor tal cual (sin comillas extra).
   - **Prioridad** (sólo MX): `10`.
   - **TTL:** deja el valor por defecto (14400) o pon `3600`.
   - Haz clic en **Agregar registro**.
3. **Recomendado: DMARC.** Mejora la entregabilidad y Gmail lo exige para volúmenes altos.

| Tipo | Nombre | Valor |
|---|---|---|
| TXT | `_dmarc` | `v=DMARC1; p=none; rua=mailto:tu-correo@gmail.com` |

> Errores típicos: escribir `send.mail.tudominio.com` en "Nombre" (queda duplicado como `send.mail.tudominio.com.tudominio.com`), o borrar el `p=` del DKIM.

### 2.3 Comprobar la propagación y verificar

```bash
# Debe responder con el valor de SPF
dig +short TXT send.mail.tudominio.com

# Debe responder con la llave DKIM (p=...)
dig +short TXT resend._domainkey.mail.tudominio.com

# Debe responder "10 feedback-smtp.sa-east-1.amazonses.com."
dig +short MX send.mail.tudominio.com
```

> En Arch Linux, `dig` viene en el paquete `bind` (`sudo pacman -S bind`). Alternativa web: <https://dnschecker.org>.

Cuando los 3 respondan, en Resend → **Domains →** tu dominio → **Verify DNS Records**. Debe quedar en **Verified** ✅. En Hostinger suele tardar entre 5 y 60 minutos, y como máximo 48 h.

En la configuración del dominio en Resend, **desactiva Click tracking y Open tracking**: en correos transaccionales de seguridad reescribir los enlaces genera desconfianza y puede afectar la entrega.

---

## 3. Crear las API keys de Resend

1. Resend → **API Keys → Create API Key**.
2. Crea **una por entorno o persona**. Así se puede revocar una sin afectar a los demás:

| Nombre | Permiso | Dominio | Quién la usa |
|---|---|---|---|
| `skillbridge-dev-dylan` | **Sending access** | `mail.tudominio.com` | Dylan (local) |
| `skillbridge-dev-persona-b` | Sending access | `mail.tudominio.com` | Persona B (local) |
| `skillbridge-dev-persona-c` | Sending access | `mail.tudominio.com` | Persona C (local) |
| `skillbridge-prod` | Sending access | `mail.tudominio.com` | Despliegue (§8) |

3. **Sending access** (no *Full access*): si la llave se filtra, sólo sirve para enviar, no para borrar dominios ni leer datos.
4. La llave (`re_...`) **se muestra una sola vez**. Cópiala directo a tu `.env` (§4).
5. **Compartirla con el equipo:** usa un canal seguro, como un enlace de un solo uso (Bitwarden Send o <https://onetimesecret.com>). **Nunca** por el chat grupal, Jira, un commit o un screenshot.

### Reglas de oro de la API key
- ❌ Nunca en Angular (`frontend/`), ni en `environment.ts`, ni en el navegador (regla 2 de `AGENTS.md`).
- ❌ Nunca en git: `.env` ya está en `.gitignore`. Antes de cada commit, revisa `git status`.
- ❌ Nunca en `docker-compose.yml` como valor literal: se usa `${RESEND_API_KEY:-}`.
- ✅ Sólo en `.env` local o en los secretos del proveedor cloud.
- 🚨 **Si se filtra:** Resend → API Keys → **Revoke**, crea una nueva y avisa al equipo.

---

## 4. Tu `.env` local (raíz del repo)

Agrega al final de tu `.env` (el que **no** se sube):

```dotenv
# ---------- Notificaciones ----------
EMAIL_DRIVER=resend
RESEND_API_KEY=re_tu_llave_personal
MAIL_FROM=SkillBridge AI <notificaciones@mail.tudominio.com>
MAIL_REPLY_TO=
# En desarrollo, redirige TODO a tu correo para no escribirle a cuentas de prueba
MAIL_REDIRECT_TO=tu-correo-personal@gmail.com
FRONTEND_URL=http://localhost:8088
```

| Variable | Para qué | Valores |
|---|---|---|
| `EMAIL_DRIVER` | `log` = no envía (por defecto). `resend` = envío real | `log` / `resend` |
| `RESEND_API_KEY` | Autenticación con Resend | `re_...` (obligatoria si `resend`) |
| `MAIL_FROM` | Remitente visible | `Nombre <direccion@dominio-verificado>` |
| `MAIL_REPLY_TO` | Dirección a la que llegan las respuestas (opcional) | Por ejemplo, `soporte@tudominio.com` |
| `MAIL_REDIRECT_TO` | **Sólo desarrollo**: redirige todos los correos | Tu correo, o vacío en producción |
| `FRONTEND_URL` | Base de los botones de los correos | `http://localhost:8088` / URL de producción |

### Plan B: el dominio todavía no está verificado
Puedes probar **hoy mismo** sin dominio:

```dotenv
EMAIL_DRIVER=resend
MAIL_FROM=SkillBridge AI <onboarding@resend.dev>
MAIL_REDIRECT_TO=<el correo con el que Dylan creó la cuenta de Resend>
```

`onboarding@resend.dev` es el remitente de pruebas de Resend. **Sólo** puede enviar al correo del dueño de la cuenta, por eso se combina con `MAIL_REDIRECT_TO`. También puedes enviar a las direcciones de prueba `delivered@resend.dev` y `bounced@resend.dev` (no llegan a nadie, pero aparecen en el dashboard).

### Aplicar los cambios

```bash
# Recrea sólo el contenedor de notificaciones con las variables nuevas
docker compose up -d --build notifications

# Confirma qué driver tomó
docker compose logs notifications | grep "Driver de correo"
```

---

## 5. Usuarios de prueba con correos reales (sólo tu BD local)

Los usuarios *seed* (migración `V14`) usan correos como `admin@skillbridge.com`. Ese dominio **no es nuestro**: enviarle correos genera rebotes y daña la reputación del dominio. Tienes dos opciones:

**Opción A (recomendada):** dejar `MAIL_REDIRECT_TO` activo en desarrollo. Todo llega a tu bandeja y el asunto dice a quién iba dirigido.

**Opción B:** cambiar el correo del admin **sólo en tu base local** (no es una migración):

```bash
# Cambia el email del admin de TU base de datos local (no afecta al repo ni a tus compañeros)
docker compose exec postgres psql -U skillbridge -d skillbridge \
  -c "UPDATE app_users SET email = 'tu-correo+admin@gmail.com' WHERE role = 'ADMIN';"
```

> El `+admin` funciona en Gmail: llega a tu misma bandeja y se puede filtrar.
> ⚠️ Después de esto, el login del admin es con el correo nuevo (la contraseña no cambia).

---

## 6. Base de datos: ¿por qué NO hay migración en v1?

- El **backend** no guarda nada nuevo: publica eventos y sigue usando sus tablas actuales.
- El **microservicio** no tiene base de datos: la idempotencia la resuelven el `eventId` y la `idempotencyKey` de Resend (24 h), y el rastro de fallos queda en la DLQ de RabbitMQ y en el dashboard de Resend.
- Por eso **no se crea `V16__...sql`**. Regla 3 de `AGENTS.md`: sólo se migra si cambia el esquema.

**Mejora futura (no hoy):** si piden un historial de correos enviados, la opción limpia sería que el microservicio tenga **su propia** base o esquema (por ejemplo, un esquema `notifications` en el mismo Postgres, con su propia herramienta de migraciones). **No** debería escribir en las tablas del backend, porque cada servicio es dueño de sus datos.

---

## 7. Frontend: ¿por qué NO cambia?

- Los correos los dispara el **backend** al cambiar el estado. El frontend sigue llamando a los mismos endpoints (`/api/auth/register`, `/api/admin/users/...`, `/api/users/{id}/status`).
- La API key de Resend **nunca** debe llegar al navegador (regla 2 de `AGENTS.md`).
- Opcional (fuera de alcance): un aviso en la UI tipo "Te enviamos un correo de confirmación" tras el registro.

---

## 8. Producción / despliegue

En el proveedor donde corra el compose o los contenedores (ver `docs/DEPLOYMENT-CHECKLIST.md`):

| Variable | Valor en producción |
|---|---|
| `EMAIL_DRIVER` | `resend` |
| `RESEND_API_KEY` | Llave `skillbridge-prod` (como **secreto** del proveedor) |
| `MAIL_FROM` | `SkillBridge AI <notificaciones@mail.tudominio.com>` |
| `MAIL_REDIRECT_TO` | **vacío** (si queda con valor, ¡ningún usuario recibe sus correos!) |
| `FRONTEND_URL` | URL pública del frontend (por ejemplo, `https://skillbridge.vercel.app`) |
| `RABBITMQ_URL` | La misma del backend. Si es un RabbitMQ administrado (CloudAMQP, etc.), usar `amqps://...` |

Checklist de salida a producción:
- [ ] Dominio **Verified** en Resend.
- [ ] DMARC publicado.
- [ ] `MAIL_REDIRECT_TO` vacío.
- [ ] Prueba real: registrar un usuario con un correo propio y verificar que llega a **Recibidos** (no a Spam).
