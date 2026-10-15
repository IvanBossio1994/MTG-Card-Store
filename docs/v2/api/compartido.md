# API v2 · Compartido

Contrato de lo que es común a toda la API v2: estado de la app, errores estándar, documentación y cómo se sirve el frontend.
Origen: [HU-002](../../agentes/historias/HU-002-base-tecnica-v2.md).

## Convenciones generales
- Base: `/api/v1`. JSON en UTF-8, campos en español.
- **Errores:** todas las respuestas de error de `/api/v1/**` son `ProblemDetail` (RFC 9457, `Content-Type: application/problem+json`) con `title` y `detail` en español, pensados para mostrarse al usuario.

  | Campo | Tipo | Descripción |
  |---|---|---|
  | `type` | string | Siempre `about:blank` por ahora. |
  | `title` | string | Resumen corto en español (por ejemplo `Recurso no encontrado`). |
  | `status` | número | Código HTTP. |
  | `detail` | string | Mensaje en español, claro para el usuario. |
  | `instance` | string | Ruta pedida (por ejemplo `/api/v1/no-existe`). |
  | `errores` | objeto | Solo en errores de validación (400): campo → mensaje. |

- **Códigos que puede devolver cualquier endpoint:**

  | Código | Cuándo | `title` | `detail` (ejemplo) |
  |---|---|---|---|
  | 400 | JSON mal formado o falta un dato obligatorio | `Pedido inválido` | `Revisá los datos enviados: el pedido no se pudo leer.` |
  | 400 | Falla la validación de un DTO (`@Valid`) | `Datos inválidos` | `Hay datos para corregir.` + `errores` |
  | 404 | La ruta no existe bajo `/api/v1` | `Recurso no encontrado` | `No existe la dirección pedida: GET /api/v1/no-existe.` |
  | 405 | La ruta existe pero no acepta ese método | `Método no permitido` | `La dirección /api/v1/estado-app no acepta el método POST.` |
  | 415 | `Content-Type` no soportado | `Formato no soportado` | `El formato del pedido no es válido. Enviá los datos como JSON.` |
  | 500 | Error inesperado del servidor | `Error interno` | `Ocurrió un error inesperado. Probá de nuevo en unos minutos.` |

- Las rutas fuera de `/api/` (v1 en `/`, frontend en `/app`) no usan `ProblemDetail`: siguen con su manejo de errores propio.

---

## GET /api/v1/estado-app
**Para qué sirve:** comprobar que la app está levantada y saber con qué versión y en qué modo corre (prueba o normal). Lo usan QA y el frontend al arrancar.

**Request:** sin parámetros ni cuerpo.

**Respuesta 200** (`application/json`):

| Campo | Tipo | Siempre presente | Descripción |
|---|---|---|---|
| `estado` | string | sí | Siempre `"ok"` si la API responde. |
| `version` | string | sí | Versión del build (`project.version` del `pom.xml`, por ejemplo `0.0.1-SNAPSHOT`). Si no se conoce, `"desconocida"`. |
| `modo` | string | sí | `"prueba"`: modo de prueba, sin Google y con datos en memoria (perfil técnico `e2e`). `"normal"`: con el Sheet real. |

```json
{ "estado": "ok", "version": "0.0.1-SNAPSHOT", "modo": "prueba" }
```

**Errores:** ninguno propio. Con otro método (por ejemplo `POST`) devuelve 405:

```json
{
  "type": "about:blank",
  "title": "Método no permitido",
  "status": 405,
  "detail": "La dirección /api/v1/estado-app no acepta el método POST.",
  "instance": "/api/v1/estado-app"
}
```

**HU de origen:** HU-002 (CA4).

---

## (cualquier método) ruta inexistente bajo /api/v1
**Para qué sirve:** que el front y QA reciban siempre un error estándar en español, aunque la ruta no exista.

**Ejemplo:** `GET /api/v1/no-existe`

**Respuesta 404** (`application/problem+json`):

```json
{
  "type": "about:blank",
  "title": "Recurso no encontrado",
  "status": 404,
  "detail": "No existe la dirección pedida: GET /api/v1/no-existe.",
  "instance": "/api/v1/no-existe"
}
```

**HU de origen:** HU-002 (CA5).

---

## Documentación de la API
- `GET /swagger-ui` → redirige a la pantalla de Swagger con todos los endpoints de `/api/v1/**` (la v1 no aparece). HU-002 (CA6).
- `GET /v3/api-docs` → contrato OpenAPI en JSON. Es el que usa el front para generar sus tipos (`openapi-typescript`).

## Frontend en /app
- `GET /app`, `GET /app/` y cualquier `GET /app/<ruta>` que no sea un archivo existente → devuelve `index.html` del frontend (las rutas las resuelve React).
- `GET /app/<archivo>` existente (por ejemplo `/app/assets/index-abc.js`) → devuelve el archivo.
- Ubicación del build: `classpath:/static/app/` (el build de Vite tiene que quedar en `target/classes/static/app/` con `base: '/app/'`).
- Si el frontend todavía no está compilado, `/app` responde 404.

## Perfil `e2e`
- Se activa con `--spring.profiles.active=e2e` (los tests con `@ActiveProfiles("e2e")` lo activan solos).
- No se conecta a Google ni descarga la lista de precios de Card Kingdom: en este perfil **no se cargan los beans de la v1 (`com.tcg.bot.legacy`) ni `com.tcg.bot.google`**, así que `/` (v1) no está disponible. Tampoco corre la sincronización programada.
- Los datos locales van a `target/e2e-data` (nunca a `data/`).
- Cada módulo v2 usa su `XEnMemoriaRepository` (`@Profile("e2e")`) con datos de ejemplo.
