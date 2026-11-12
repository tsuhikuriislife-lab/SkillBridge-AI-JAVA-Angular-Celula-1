# Configuración: dominio (Hostinger), Resend, API keys y variables por capa

> **Responsables:**
> - **Mateo Munera** (líder del grupo): dueño de la cuenta de Resend y de las API keys.
> - **Dylan:** dominio y DNS en Hostinger (KAN-56).
> - **Leonardo:** webhooks (§9, KAN-58).
>
> **Datos reales del proyecto:**
> - Dominio: **`skillbridgeai.online`** (Hostinger, comprado el 2026-11-11).
> - Subdominio de envío: **`mail.skillbridgeai.online`**.
> - Remitente: `SkillBridge AI <notificaciones@mail.skillbridgeai.online>`.
> - Respuestas: **`support@skillbridgeai.online`** (buzón de correo de Hostinger).
> **Hazlo PRIMERO** (Bloque 0): la propagación DNS puede tardar desde minutos hasta algunas horas.

## Resumen: ¿qué cambia en cada capa?

| Capa | ¿Cambia? | Qué | Dónde |
|---|---|---|---|
| **Dominio / DNS** | ✅ Sí | 3 registros de Resend en `mail.` (el DMARC ya existe) | Hostinger hPanel (§2) |
| **Resend** | ✅ Sí | Dominio verificado + API key | resend.com (§3) |
| **`.env` raíz (local, NO se sube)** | ✅ Sí | `EMAIL_DRIVER`, `RESEND_API_KEY`, `RESEND_WEBHOOK_SECRET`, `MAIL_FROM`, `MAIL_REPLY_TO`, `MAIL_REDIRECT_TO`, `FRONTEND_URL` | §4 |
| **`.env.example` raíz (sí se sube)** | ✅ Sí | Las mismas variables **sin valores secretos** | KAN-56, Paso 11 |
| **`docker-compose.yml`** | ✅ Sí | Servicio `notifications` (KAN-56, Paso 11) + volumen de init en `postgres` (KAN-58, Paso 1) | — |
| **Redis** | ✅ Sí (sin cambios de config) | El microservicio usa la **base 1** (`redis://redis:6379/1`); el backend sigue en la 0 | KAN-57 |
| **PostgreSQL** | ✅ Sí | **Base de datos nueva `notifications`** en el mismo contenedor, con migraciones TypeORM propias (§6) | KAN-58 |
| **Backend (`application.yml`)** | ❌ No | Reutiliza `RABBITMQ_URL`, que ya existe. El nombre del exchange es una constante del contrato. **No hay secretos nuevos.** | — |
| **Backend (código)** | ✅ Sí | Publicación de eventos + seguridad | KAN-59 |
| **Backend (Flyway)** | ❌ No | La base del backend (`skillbridge`) no cambia → **no hay `V16`** | — |
| **Frontend (Angular)** | ❌ No | El frontend **nunca** habla con Resend ni conoce la API key | §7 |
| **GitHub Actions** | ❌ Sin secretos | Las pruebas usan clientes falsos; sólo se agrega un job (KAN-58, Paso 8) | — |
| **Producción / nube** | ✅ Sí | Variables del servicio `notifications` en el proveedor | §8 |

---

## 1. Comprar el dominio en Hostinger

1. Entra a <https://hpanel.hostinger.com> → **Dominios** → **Obtener un nuevo dominio**.
2. Busca el nombre (por ejemplo `skillbridge-ai.com`, `skillbridgeai.co`, etc.) y cómpralo.
3. Durante la compra activa **Protección de privacidad WHOIS** (gratis en Hostinger). Así tus datos personales no quedan públicos.
4. Espera a que el estado del dominio sea **Activo** (hPanel → Dominios → Portafolio de dominios). Suele tardar unos minutos.
5. **No cambies los nameservers.** Por defecto Hostinger administra el DNS (`ns1.dns-parking.com` / `ns2.dns-parking.com`), y ahí se agregan los registros. Si algún día pasan el DNS a Cloudflare, los registros de §2 se agregan **allá**.

> ✅ **Ya hecho:** se compró `skillbridgeai.online` y se creó el buzón `support@skillbridgeai.online` en Hostinger.
>
> **Estado del DNS al 2026-11-11:**
> - La raíz (`@`) ya tiene los registros del **correo de Hostinger**: MX `mx1`/`mx2.hostinger.com`, SPF `include:_spf.mail.hostinger.com` y DKIM `hostingermail-a/b/c._domainkey`.
> - Ya existe `_dmarc` con `v=DMARC1; p=none`, que también cubre los subdominios.
> - **No tocar ninguno de esos registros.** Los de Resend van en el subdominio `mail.`, así que no chocan.

### ¿Por qué enviar desde un subdominio (`mail.skillbridgeai.online`)?

Recomendado por Resend:
- **Aísla la reputación:** si los correos transaccionales tienen problemas (rebotes), no afectan al dominio principal (web y correo corporativo).
- **Evita conflictos de SPF:** Hostinger suele crear un registro SPF en la raíz (`@`) para su propio correo. Al usar `mail.`, los registros de Resend no chocan con esos.

Remitente final: `SkillBridge AI <notificaciones@mail.skillbridgeai.online>`.

---

## 2. Verificar el dominio en Resend y crear los registros DNS en Hostinger

### 2.1 En Resend
1. Crea la cuenta en <https://resend.com>. El dueño es **Mateo Munera**, el líder del grupo. Plan gratis: 3.000 correos al mes, 100 al día y 1 dominio, suficiente para el proyecto.
2. **Domains → Add Domain**.
   - Name: `mail.skillbridgeai.online`
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
   - **Nombre:** sólo la parte del host, **sin** el dominio: `send.mail`, `resend._domainkey.mail`. Hostinger agrega `.skillbridgeai.online` solo.
   - **Apunta a / Contenido / Valor:** pega el valor tal cual (sin comillas extra).
   - **Prioridad** (sólo MX): `10`.
   - **TTL:** deja el valor por defecto (14400) o pon `3600`.
   - Haz clic en **Agregar registro**.
3. **DMARC: ✅ ya existe en `skillbridgeai.online`** (`v=DMARC1; p=none`) y cubre a `mail.`, así que **no crees otro**. Como referencia, el formato es:

| Tipo | Nombre | Valor |
|---|---|---|
| TXT | `_dmarc` | `v=DMARC1; p=none; rua=mailto:tu-correo@gmail.com` |

> Errores típicos: escribir `send.mail.skillbridgeai.online` en "Nombre" (queda duplicado como `send.mail.skillbridgeai.online.skillbridgeai.online`), o borrar el `p=` del DKIM.

### 2.3 Comprobar la propagación y verificar

```bash
# Debe responder con el valor de SPF
dig +short TXT send.mail.skillbridgeai.online

# Debe responder con la llave DKIM (p=...)
dig +short TXT resend._domainkey.mail.skillbridgeai.online

# Debe responder "10 feedback-smtp.sa-east-1.amazonses.com."
dig +short MX send.mail.skillbridgeai.online
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
| `skillbridge-dev-dylan` | **Sending access** | `mail.skillbridgeai.online` | Dylan (local) |
| `skillbridge-dev-kevin` | Sending access | `mail.skillbridgeai.online` | Kevin (local) |
| `skillbridge-dev-leonardo` | **Full access** (sólo dev) | — | Leonardo (local: el health check usa `domains.list`, que requiere Full access) |
| `skillbridge-prod` | **Full access** | — | Despliegue (§8). El `/health` consulta `domains.list` |

3. **Sending access** es lo más seguro: si la llave se filtra, sólo sirve para enviar. **Pero** el check de Resend en `/health` (KAN-58) usa `domains.list()`, que necesita **Full access**. Opciones: usar Full access sólo donde corre `/health` con `EMAIL_DRIVER=resend` (producción y la máquina de Leonardo) o aceptar que `/health` marque `resend: down` con llaves de Sending access. Recomendado: Full access en producción, **guardada como secreto** y rotada si hay sospecha de filtración.
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
RESEND_WEBHOOK_SECRET=whsec_del_endpoint_de_webhook   # §9
MAIL_FROM=SkillBridge AI <notificaciones@mail.skillbridgeai.online>
MAIL_REPLY_TO=support@skillbridgeai.online
# En desarrollo, redirige TODO a tu correo para no escribirle a cuentas de prueba
MAIL_REDIRECT_TO=tu-correo-personal@gmail.com
FRONTEND_URL=http://localhost:8088
```

| Variable | Para qué | Valores |
|---|---|---|
| `EMAIL_DRIVER` | `log` = no envía (por defecto). `resend` = envío real | `log` / `resend` |
| `RESEND_API_KEY` | Autenticación con Resend | `re_...` (obligatoria si `resend`) |
| `RESEND_WEBHOOK_SECRET` | Verificar la firma de los webhooks (§9) | `whsec_...`. Sin él, `/webhooks/resend` responde 503 |
| `MAIL_FROM` | Remitente visible | `Nombre <direccion@dominio-verificado>` |
| `MAIL_REPLY_TO` | Dirección a la que llegan las respuestas (opcional) | `support@skillbridgeai.online` (buzón de Hostinger) |
| `MAIL_REDIRECT_TO` | **Sólo desarrollo**: redirige todos los correos | Tu correo, o vacío en producción |
| `FRONTEND_URL` | Base de los botones de los correos | `http://localhost:8088` / URL de producción |

### Plan B: el dominio todavía no está verificado
Puedes probar **hoy mismo** sin dominio:

```dotenv
EMAIL_DRIVER=resend
MAIL_FROM=SkillBridge AI <onboarding@resend.dev>
MAIL_REDIRECT_TO=<el correo con el que Mateo creó la cuenta de Resend>
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

## 6. Bases de datos: qué cambia y qué no

- **Backend (`skillbridge`, Flyway):** **no cambia.** No hay `V16`: el backend sólo publica eventos.
- **Microservicio (`notifications`, BD NUEVA en el mismo contenedor Postgres):** tabla `notification_log` (KAN-58) creada con una **migración TypeORM** que se aplica sola al arrancar. Cada servicio es dueño de sus datos: el microservicio **nunca** toca las tablas del backend, y viceversa.
- **Crearla en tu máquina** (una vez): `make db-notifications`. En volúmenes nuevos la crea sola el script `ops/postgres/init/10-notifications-db.sql`.
- **URL:** `postgresql://<POSTGRES_USER>:<POSTGRES_PASSWORD>@postgres:5432/notifications` (ya está en el compose). Si la contraseña tiene `@ : / #`, hay que codificarlos en la URL.
- **Redis:** el microservicio usa la **base 1** para las llaves de idempotencia (`notifications:idempotency:*`, con expiración de 7 días). No requiere configuración nueva.
- **Mejora futura:** usar un usuario de Postgres propio para `notifications`, con permisos sólo sobre esa base (mínimo privilegio).

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
| `MAIL_FROM` | `SkillBridge AI <notificaciones@mail.skillbridgeai.online>` |
| `MAIL_REDIRECT_TO` | **vacío** (si queda con valor, ¡ningún usuario recibe sus correos!) |
| `FRONTEND_URL` | URL pública del frontend (por ejemplo, `https://skillbridge.vercel.app`) |
| `RABBITMQ_URL` | La misma del backend. Si es un RabbitMQ administrado (CloudAMQP, etc.), usar `amqps://...` |
| `REDIS_URL` | Redis administrado, en una base o un prefijo distinto al del backend |
| `NOTIFICATIONS_DATABASE_URL` | Base `notifications` (puede ser la misma instancia que la del backend) |
| `RESEND_WEBHOOK_SECRET` | `whsec_` del endpoint de **producción** (§9) |

Checklist de salida a producción:
- [ ] Dominio **Verified** en Resend.
- [x] DMARC publicado (ya existía en la raíz).
- [ ] `MAIL_REDIRECT_TO` vacío.
- [ ] Prueba real: registrar un usuario con un correo propio y verificar que llega a **Recibidos** (no a Spam).
- [ ] Webhook de producción configurado con la URL real (`https://<dominio-api>/webhooks/resend`).

---

## 9. Webhooks de Resend (KAN-58)

Resend avisa por HTTP cada cambio de estado de un correo (enviado, entregado, rebotado, queja). El microservicio los recibe en `POST /webhooks/resend` y actualiza `notification_log`.

1. **Resend → Webhooks → Add Endpoint.**
   - **Endpoint URL:**
     - en local: la URL del túnel + `/webhooks/resend` (por ejemplo, `https://xxxx.trycloudflare.com/webhooks/resend`; ver la guía de Leonardo, Paso 9);
     - en producción: `https://<dominio-del-microservicio>/webhooks/resend`.
   - **Events:** `email.sent`, `email.delivered`, `email.delivery_delayed`, `email.bounced`, `email.complained` y `email.failed`.
2. Copia el **Signing secret** (`whsec_…`) a `RESEND_WEBHOOK_SECRET` en tu `.env` y reinicia `notifications`.
3. **Un secreto por endpoint:** el de tu túnel local y el de producción son distintos. Nunca subas ninguno a git.
4. **Por qué es seguro:** cada webhook trae `svix-id`, `svix-timestamp` y `svix-signature` (HMAC-SHA256 con tu secreto). Si alguien llama a la URL sin firma válida, el servicio responde **401** y no escribe nada.
5. **Túnel local:** instala `cloudflared` (Arch: `sudo pacman -S cloudflared`) o `ngrok`. La URL de `trycloudflare.com` cambia en cada ejecución: actualízala en Resend.
6. **Tracking:** `email.opened` y `email.clicked` se ignoran a propósito (el tracking está desactivado, §2.3).
