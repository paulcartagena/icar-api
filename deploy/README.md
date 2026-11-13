# Deploy en AWS (EC2 + RDS)

Guía paso a paso para publicar la API desde la consola de AWS.

```
Internet ──HTTPS──▶ EC2 t4g.micro (Ubuntu + Docker)
                     ├─ Caddy  (HTTPS automático con Let's Encrypt)
                     └─ API    (imagen ghcr.io/paulcartagena/icar-api)
                              │ red privada de AWS
                              ▼
                    RDS PostgreSQL db.t4g.micro (sin acceso público)
```

- **URL pública:** `https://<IP-con-guiones>.sslip.io` (por ejemplo `https://3-15-120-8.sslip.io`). [sslip.io](https://sslip.io) es un DNS gratis que resuelve ese nombre a tu IP, así que no hace falta comprar un dominio. Si después compras uno, solo cambia `API_DOMAIN`.
- **Deploy automático:** cada merge a `main` corre los tests, publica la imagen en GitHub Container Registry y la despliega en el EC2. Esto queda activo después del paso 8.
- **Costo estimado:** ~25 USD/mes en us-east-1 (EC2 ~6, IP pública ~4, discos ~4, RDS ~12). Es una estimación: revisa la calculadora de AWS.

> ⚠️ **Si skill-match-api sigue desplegada en la misma cuenta**, los costos se suman (~50 USD/mes) y los créditos del plan gratis se gastan el doble de rápido. Revisa el saldo en **Billing → Credits** antes de empezar.

---

## Antes de empezar

1. Mergea el PR de este deploy. El primer workflow en `main` publica la imagen. Revisa en **Actions** que haya terminado en verde.
2. **Haz pública la imagen:** en GitHub, ve a tu perfil → **Packages** → `icar-api` → **Package settings** → **Change visibility** → **Public**. Así el servidor puede descargarla sin credenciales. La imagen no contiene secretos: todos van en el `.env` del servidor.

Elige **una región** y usa siempre la misma en todos los pasos. `us-east-1` (N. Virginia) suele ser la más barata.

---

## 0. Alerta de gasto (2 min)

**Billing and Cost Management → Budgets → Create budget**
- Template: **Monthly cost budget**
- Monto: **25 USD** (o la suma de tus proyectos)
- Email: el tuyo

AWS te avisa si el gasto mensual se acerca a ese monto.

## 1. Security groups (5 min)

**EC2 → Security Groups → Create security group** (en la VPC por defecto).

**`icar-api-sg`** (para el servidor)
| Tipo | Puerto | Origen | Para qué |
|---|---|---|---|
| HTTP | 80 | `0.0.0.0/0` | Let's Encrypt y redirección a HTTPS |
| HTTPS | 443 | `0.0.0.0/0` | La API |
| SSH | 22 | `0.0.0.0/0` | Deploy automático desde GitHub y tu acceso. Solo se entra con la clave privada; Ubuntu no permite contraseñas. |

**`icar-db-sg`** (para la base de datos)
| Tipo | Puerto | Origen |
|---|---|---|
| PostgreSQL | 5432 | el security group `icar-api-sg` (no una IP) |

Así, solo el servidor de la API puede conectarse a la base de datos.

## 2. Base de datos RDS (5 min de clics + ~10 min de espera)

**RDS → Create database**
- **Standard create**, engine **PostgreSQL**, versión **17.x**
- Template: **Free tier** (si aparece) o **Dev/Test**
- **DB instance identifier:** `icar-db`
- **Master username:** `icar`
- **Password:** genera una fuerte y **guárdala**; la vas a usar en el paso 5
- **Instance class:** `db.t4g.micro`
- **Storage:** gp3, **20 GB**, sin autoscaling
- **Connectivity:**
  - **Public access: No**
  - VPC security group: elige **`icar-db-sg`** y quita `default`
- **Additional configuration:**
  - **Initial database name:** `icar` ⚠️ si lo dejas vacío, la base no se crea y la API no arranca
  - Backups automáticos: déjalos activados (1–7 días). Aquí van los datos de la iglesia (donaciones, miembros), así que no los apagues.
  - Multi-AZ: **no** (duplica el costo)

Cuando quede en estado **Available**, copia el **Endpoint** que aparece en *Connectivity & security*, algo como `icar-db.xxxx.us-east-1.rds.amazonaws.com`.

## 3. Servidor EC2 (5 min)

**EC2 → Launch instance**
- **Name:** `icar-api`
- **AMI:** **Ubuntu Server 24.04 LTS**, arquitectura **64-bit (Arm)**
- **Instance type:** `t4g.micro`
- **Key pair:** créala (tipo ED25519, formato `.pem`) y **guarda el archivo**; lo vas a usar en el paso 8
- **Network settings:** *Select existing security group* → **`icar-api-sg`**
- **Storage:** 20 GB gp3

**IP fija:** **EC2 → Elastic IPs → Allocate Elastic IP address**, luego **Actions → Associate** con la instancia `icar-api`. Anota la IP; sin esto, la IP cambiaría al reiniciar el servidor y con ella la URL.

## 4. Preparar el servidor (5 min)

Conéctate desde la consola: **EC2 → Instances → `icar-api` → Connect → EC2 Instance Connect → Connect**. Se abre una terminal en el navegador.

```bash
curl -fsSL https://raw.githubusercontent.com/paulcartagena/icar-api/main/deploy/setup-ec2.sh | bash
```

El script instala Docker, agrega 1 GB de swap y descarga `docker-compose.yml`, `Caddyfile` y un `.env` de ejemplo en `~/icar`.

Al terminar, **cierra la terminal y vuelve a conectarte** para que tu usuario pueda usar Docker.

## 5. Configurar los secretos (5 min)

Genera el secreto JWT:
```bash
openssl rand -base64 48
```

Edita el archivo de configuración:
```bash
nano ~/icar/.env
```

| Variable | Valor |
|---|---|
| `API_DOMAIN` | tu Elastic IP con guiones + `.sslip.io`, por ejemplo `3-15-120-8.sslip.io` |
| `PROD_URL_DB` | `jdbc:postgresql://<endpoint-del-paso-2>:5432/icar?sslmode=require` |
| `PROD_USERNAME_DB` | `icar` |
| `PROD_PASSWORD_DB` | la contraseña del paso 2 |
| `JWT_SECRET` | lo que imprimió `openssl` |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | las credenciales del primer admin (contraseña de 12 a 72 caracteres) |
| `ADMIN_FULL_NAME` | opcional, el nombre que se muestra |
| `CORS_ALLOWED_ORIGINS` | déjalo como está hasta que el panel o el sitio tengan URL de producción |
| `TRUSTED_PROXY_SECRET` | otro valor de `openssl rand -base64 32`; el mismo va en `API_PROXY_SECRET` de icar-web (Vercel). Así el límite de formularios y donaciones cuenta a cada visitante y no al servidor de la web |
| `API_DOCS_ENABLED` | `false`; ponlo en `true` solo mientras necesites `/scalar` (paso 7) |
| `PAYPAL_*` | vacías hasta configurar las donaciones online (paso 9) |

Guarda con `Ctrl+O`, `Enter`, y sal con `Ctrl+X`.

## 6. Arrancar (2 min)

```bash
cd ~/icar
docker compose up -d
docker compose logs -f api
```

Espera a ver `Started IcarApiApplication` y sal de los logs con `Ctrl+C`. En el primer arranque, Flyway crea las tablas y se crea el admin. Después del primer arranque puedes borrar `ADMIN_PASSWORD` del `.env`: solo se usa si no existe ningún admin.

Comprueba desde tu navegador:
- `https://<API_DOMAIN>/actuator/health` → `{"status":"UP"}`
- `https://<API_DOMAIN>/public/ministries` → `[]` (todavía no hay ministerios)

El certificado HTTPS tarda unos segundos la primera vez. Si falla, mira `docker compose logs caddy`.

## 7. Datos iniciales (opcional, mientras no haya panel)

Sin el panel de administración, la forma más cómoda de cargar datos es Scalar:
1. Pon `API_DOCS_ENABLED=true` en `~/icar/.env` y reinicia con `docker compose up -d`.
2. Abre `https://<API_DOMAIN>/scalar` y haz `POST /auth/login` con el admin → copia el `accessToken`.
3. En **Authentication** de Scalar, pega el token. Ya puedes crear usuarios (pastor, tesorero), categorías, ministerios con sus horarios, "sobre nosotros" y contacto.
4. Al terminar, vuelve a `API_DOCS_ENABLED=false` y reinicia.

## 8. Activar el deploy automático (3 min)

En GitHub: **repo → Settings → Secrets and variables → Actions → New repository secret**

| Secret | Valor |
|---|---|
| `EC2_HOST` | la Elastic IP |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_KEY` | el contenido completo del `.pem` del paso 3, incluidas las líneas `-----BEGIN…` y `-----END…` |

Desde ahí, cada merge a `main` corre los tests, publica la imagen y reinicia la API en el servidor. Mientras estos secrets no existan, el paso de deploy se salta solo.

## 9. Para el frontend (panel y sitio público)

- **Base URL:** `https://<API_DOMAIN>`
- **Autenticación:** `POST /auth/login` devuelve el access token (15 min) y deja el refresh token en una cookie `HttpOnly` con `Path=/auth`. Se manda `Authorization: Bearer <accessToken>` y se renueva con `POST /auth/refresh` (con `credentials: "include"`).
- **CORS:** por ahora acepta `localhost:3000` y `localhost:5173`. Cuando el panel o el sitio tengan URL de producción, agrégalas en `CORS_ALLOWED_ORIGINS` (separadas por comas) y reinicia con `docker compose up -d`.
- ⚠️ **Cookie del refresh token:** es `SameSite=Strict`, así que el navegador solo la manda si el panel y la API están en el **mismo sitio** (por ejemplo `admin.icar.org` y `api.icar.org`). Con dominios distintos (por ejemplo `vercel.app` y `sslip.io`), el refresh no funciona: usa un proxy desde el frontend (`rewrites` de Next.js / Vercel) para que el navegador vea la API en el mismo dominio, o compra un dominio para ambos.
- **Sitio público:** `GET /public/**` no necesita token.

### Donaciones online (PayPal)

1. En [developer.paypal.com](https://developer.paypal.com) → **Apps & Credentials** → **Sandbox**, crea una app y copia
   el **Client ID** y el **Secret** en `PAYPAL_CLIENT_ID` / `PAYPAL_CLIENT_SECRET`.
2. En esa app, **Add Webhook** con la URL `https://<API_DOMAIN>/public/donations/paypal/webhook` y los eventos
   `Payment capture completed` y `Payment capture denied`. Copia su **Webhook ID** en `PAYPAL_WEBHOOK_ID`.
3. Reinicia con `docker compose up -d`. Al arrancar se crea la categoría de ingreso **Donaciones online**.
4. En el sitio, carga el JS SDK con el `clientId` de `GET /public/donations/paypal/config` (también trae la moneda,
   el mínimo, el máximo y los montos sugeridos) y usa los botones así:
   ```js
   paypal.Buttons({
     createOrder: async () => {
       const res = await fetch(`${API}/public/donations/paypal/orders`, {
         method: "POST", headers: { "Content-Type": "application/json" },
         body: JSON.stringify({ amount, donorName, message }),
       });
       return (await res.json()).orderId;
     },
     onApprove: async ({ orderID }, actions) => {
       const res = await fetch(`${API}/public/donations/paypal/orders/${orderID}/capture`, { method: "POST" });
       if (res.status === 409) return actions.restart(); // tarjeta rechazada: el donante elige otro medio
       const { status } = await res.json(); // COMPLETED, o PENDING si PayPal lo está revisando
     },
   }).render("#paypal-button");
   ```
   Prueba con las cuentas de comprador de **Sandbox accounts**.
5. Para cobrar de verdad, repite los pasos 1 y 2 en **Live** (con la cuenta PayPal Business de la iglesia), cambia
   las tres variables y pon `PAYPAL_BASE_URL=https://api-m.paypal.com`.

Los reembolsos se hacen en PayPal y luego se anula la donación desde el panel (`PATCH /donations/{id}/void`).

## 10. Operación del día a día

```bash
cd ~/icar
docker compose ps                 # estado
docker compose logs --tail 200 api
docker compose pull api && docker compose up -d   # deploy manual de la última imagen
```

Si cambias `docker-compose.yml` o `Caddyfile` en el repo, vuelve a descargarlos en el servidor, porque el deploy automático solo actualiza la imagen.

## 11. Apagar todo (cuando ya no se use)

Para que no se siga cobrando:
1. **RDS:** antes de borrar, guarda un **snapshot final**: son los datos de la iglesia. Después, delete la base (el snapshot cuesta algo de almacenamiento).
2. **EC2:** terminate la instancia y **release** la Elastic IP. Una IP sin usar también se cobra.
3. **Security groups:** puedes borrarlos; no cobran.
