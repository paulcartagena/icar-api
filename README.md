# ICAR API

API REST de la iglesia ICAR: panel de administración (finanzas, membresía, contenido del sitio) y endpoints públicos
para el sitio web.

## Qué hace

- Login con JWT y refresh token en cookie; permisos por rol (admin, pastor, tesorero).
- Finanzas: categorías, gastos, ingresos, donaciones y presupuestos (planificado vs. real).
- Membresía: miembros, familias, ministerios y horarios de servicio.
- Contenido: eventos con fotos, "sobre nosotros" y contacto, con lectura pública en `/public/**`.

## Stack

Java 21 · Spring Boot 4 · PostgreSQL · Flyway · Docker · GitHub Actions

## Correr en local

Requiere Java 21 y PostgreSQL.

```bash
export SPRING_PROFILES_ACTIVE=dev
export LOCAL_URL_DB=jdbc:postgresql://localhost:5432/icar_db
export LOCAL_USERNAME_DB=postgres LOCAL_PASSWORD_DB=postgres
export JWT_SECRET=$(openssl rand -base64 48)
# Solo la primera vez, con la BD vacía: crea el primer admin
export ADMIN_USERNAME=admin ADMIN_PASSWORD=una-clave-de-12-o-mas

./mvnw spring-boot:run
```

La API queda en http://localhost:8080, con la documentación en `/scalar` y el health check en `/actuator/health`.

## Variables de entorno

| Variable | Uso |
|---|---|
| `LOCAL_*_DB` / `PROD_*_DB` | URL, usuario y contraseña de la BD según el perfil (`dev` / `prod`) |
| `JWT_SECRET` | Firma de los access tokens (32 caracteres o más) |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `ADMIN_FULL_NAME` | Primer admin, solo si no existe ninguno |
| `CORS_ALLOWED_ORIGINS` | Frontends permitidos, separados por coma (por defecto `localhost:3000` y `5173`) |
| `PORT` | Puerto del servidor (por defecto `8080`) |
| `API_DOCS_ENABLED` | Abre `/scalar` y `/v3/api-docs` (por defecto abiertos en `dev` y cerrados en `prod`) |
| `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET` | Credenciales de la app de PayPal para las donaciones online; sin ellas esos endpoints responden 503 |
| `PAYPAL_WEBHOOK_ID` | Id del webhook de esa app, para verificar las notificaciones de PayPal |
| `PAYPAL_BASE_URL` | `https://api-m.sandbox.paypal.com` (por defecto) o `https://api-m.paypal.com` en producción |

## Tests

```bash
./mvnw test
```

`contextLoads` arranca la app completa, así que necesita una BD y las variables de arriba.

## CI y deploy

Cada PR y cada push a `main` corre los tests contra un Postgres de GitHub Actions y construye la imagen Docker;
en `main` además la publica en `ghcr.io/paulcartagena/icar-api` y la despliega en AWS (EC2 + RDS). La guía para
montar el servidor está en [`deploy/README.md`](deploy/README.md).
