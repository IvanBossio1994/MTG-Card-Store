# API v2 · Configuración

Contrato del módulo Configuración: leer y guardar cada bloque de la pantalla por separado (tienda, Sheet, precios y puntos).
Origen: [HU-003](../../agentes/historias/HU-003-configuracion-backend.md). Flujos: CFG-F01 a CFG-F06 y CFG-A01 a CFG-A09 (`docs/funcional/documento-funcional.md`). Textos: `docs/v2/textos.md`.

## Convenciones del módulo
- Base: `/api/v1/configuracion`. JSON en UTF-8, campos en español. Las convenciones generales y los errores comunes están en [compartido.md](compartido.md).
- **Cada bloque se guarda por separado:** un `PUT` de un bloque nunca cambia los otros. Si hay un error, no se guarda nada de ese bloque.
- **Mismo almacenamiento que la v1:** la tienda y el Sheet se guardan en `<app.storage-dir>/store.properties`; los precios y los puntos, en `<app.storage-dir>/pricing.properties`; el logo, en `<app.storage-dir>/store-logo.<png|jpg|webp>`. Son los mismos archivos y claves que usa la v1.
- **Campos que pueden venir vacíos:** los marcados "puede ser `null`" vienen siempre en la respuesta, con `null` cuando no hay valor.
- **Mensajes:** los `detail` y los mensajes de `errores` son los textos exactos de `textos.md` (se indica la clave).

### Errores propios del módulo
Además de los comunes de `compartido.md`:

| Código | Cuándo | `title` | `detail` | Propiedades extra |
|---|---|---|---|---|
| 400 | Un campo no pasa la validación (vacío, menor o igual a 0, formato) | `Datos inválidos` | `Hay datos para corregir.` | `errores`: campo → mensaje de `textos.md` |
| 401 | Google rechaza la sesión del Gmail conectado (`invalid_grant`) o no hay sesión iniciada | `Sesión de Google vencida` | `Tu sesión de Google venció.` (`general.error.sesion-vencida`) | `accion`: `"volver-a-iniciar-sesion"` · `urlInicioSesion`: `"/configuracion/google/oauth/iniciar"` |
| 403 | El Sheet no está compartido con la cuenta de servicio (o el ID no existe) | `Sin acceso al Sheet` | `La app no tiene acceso a este Sheet. Compartilo con tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com como editor.` (`configuracion.sheet.error.sin-permiso`) | `cuentaServicio`: `"tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com"` |
| 503 | No se pudo hablar con Google (sin internet o Google no responde) | `Sin conexión` | `Revisá tu conexión a internet e intentá de nuevo.` (`general.error.conexion`) | — |
| 500 | Windows no deja leer la carpeta de la sesión de Google (`data/google-oauth-token` a nombre de otro usuario, por ejemplo si la app se abrió como administrador) | `Sesión de Google ilegible` | `La app no puede leer tu sesión de Google en esta PC. Abrila sin permisos de administrador y volvé a iniciar sesión.` (`general.error.sesion-ilegible`) | — |

Ejemplo 400 (validación):
```json
{
  "type": "about:blank",
  "title": "Datos inválidos",
  "status": 400,
  "detail": "Hay datos para corregir.",
  "instance": "/api/v1/configuracion/tienda",
  "errores": { "nombre": "Ingresá el nombre de la tienda." }
}
```

Ejemplo 401:
```json
{
  "type": "about:blank",
  "title": "Sesión de Google vencida",
  "status": 401,
  "detail": "Tu sesión de Google venció.",
  "instance": "/api/v1/configuracion",
  "accion": "volver-a-iniciar-sesion",
  "urlInicioSesion": "/configuracion/google/oauth/iniciar"
}
```

Ejemplo 403:
```json
{
  "type": "about:blank",
  "title": "Sin acceso al Sheet",
  "status": 403,
  "detail": "La app no tiene acceso a este Sheet. Compartilo con tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com como editor.",
  "instance": "/api/v1/configuracion/sheet",
  "cuentaServicio": "tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com"
}
```

### Bloques (forma de las respuestas)
Se repiten en `GET /api/v1/configuracion` y en la respuesta de cada `PUT`.

**`tienda`**
| Campo | Tipo | Descripción |
|---|---|---|
| `nombre` | string | Nombre de la tienda. Si nunca se cargó, `"Inventory Manager"` (el de la v1). |
| `logoUrl` | string, puede ser `null` | Dirección para mostrar el logo (`GET /api/v1/configuracion/tienda/logo?version=<n>`). `version` cambia cada vez que se sube un logo, para que el navegador no muestre el viejo. `null` si no hay logo. |

**`sheet`**
| Campo | Tipo | Descripción |
|---|---|---|
| `sheetId` | string, puede ser `null` | ID del Google Sheet guardado. `null` si no hay ninguno. |
| `link` | string, puede ser `null` | Link para abrir el Sheet (`https://docs.google.com/spreadsheets/d/<sheetId>/edit`). `null` si no hay Sheet. |
| `pestanaInventario` | string | Nombre de la pestaña del inventario. Por defecto `"Inventario"`. |
| `gmail` | string, puede ser `null` | Gmail con el que se inició sesión (para "Conectado como {gmail}"). `null` si Google no lo informa (por ejemplo, sesiones iniciadas antes de esta versión: se completa al volver a iniciar sesión). |
| `aviso` | string, puede ser `null` | Solo en la respuesta de `PUT /sheet`: aviso de que se renombró o se creó la pestaña. En el `GET` siempre es `null`. |

**`precios`**
| Campo | Tipo | Descripción |
|---|---|---|
| `cotizacion` | número | ARS por USD de Card Kingdom. Por defecto 1650. |
| `redondeo` | entero | Múltiplo al que se redondea el precio. Por defecto 500. |
| `bulk` | objeto | Precio bulk por rareza (pesos, sin centavos): `common` (50), `uncommon` (100), `rare` (150), `mythic` (150), `tierraBasica` (200). |
| `carpetaCache` | string, puede ser `null` | Carpeta de la cache de Card Kingdom. `null` si se usa la de siempre (`<app.storage-dir>/cache`). |
| `aviso` | string, puede ser `null` | Solo en la respuesta de `PUT /precios`: aviso si se guardó pero falló la sincronización con Card Kingdom. En el `GET` siempre es `null`. |

**`puntos`**
| Campo | Tipo | Descripción |
|---|---|---|
| `pesosPorPunto` | número | Cuántos pesos tiene que gastar un cliente para sumar un punto. Por defecto 1. |

---

## GET /api/v1/configuracion
**Para qué sirve:** cargar la pantalla de Configuración con los cuatro bloques y sus valores guardados, más el Gmail conectado (CFG-F06).

**Request:** sin parámetros ni cuerpo.

**Respuesta 200:**
```json
{
  "tienda": { "nombre": "Codex Live", "logoUrl": "/api/v1/configuracion/tienda/logo?version=1760550000000" },
  "sheet": {
    "sheetId": "1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ",
    "link": "https://docs.google.com/spreadsheets/d/1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ/edit",
    "pestanaInventario": "Inventario",
    "gmail": "tienda@gmail.com",
    "aviso": null
  },
  "precios": {
    "cotizacion": 1200,
    "redondeo": 500,
    "bulk": { "common": 50, "uncommon": 100, "rare": 150, "mythic": 150, "tierraBasica": 200 },
    "carpetaCache": null,
    "aviso": null
  },
  "puntos": { "pesosPorPunto": 1000 }
}
```

**Errores:** 401 si la sesión de Google venció (CFG-A05) · 500 si Windows no deja leer la carpeta de la sesión · 503 si no hay conexión con Google.

**HU de origen:** HU-003 (CA1, CA10).

---

## PUT /api/v1/configuracion/tienda
**Para qué sirve:** guardar el nombre de la tienda y, si se pide, quitar el logo (CFG-F01). El logo nuevo se sube aparte, con `POST /tienda/logo`.

**Request** (`application/json`):

| Campo | Tipo | Obligatorio | Validación | Mensaje si falla |
|---|---|---|---|---|
| `nombre` | string | sí | No vacío ni solo espacios. Se guarda sin espacios al principio ni al final. | `Ingresá el nombre de la tienda.` (`configuracion.tienda.error.nombre-vacio`) |
| `quitarLogo` | booleano | no (por defecto `false`) | — | — |

```json
{ "nombre": "Codex Live Store", "quitarLogo": false }
```

**Respuesta 200:** el bloque `tienda`.
```json
{ "nombre": "Codex Live Store", "logoUrl": null }
```

**Errores:**
- 400 con `errores.nombre` = `Ingresá el nombre de la tienda.` si el nombre está vacío (CFG-A01). No cambia nada.

**HU de origen:** HU-003 (CA2, CA3).

---

## POST /api/v1/configuracion/tienda/logo
**Para qué sirve:** subir el logo de la tienda; reemplaza al anterior en el momento (CFG-F01).

**Request** (`multipart/form-data`):

| Parte | Tipo | Obligatorio | Validación | Mensaje si falla |
|---|---|---|---|---|
| `archivo` | archivo | sí | PNG, JPG o WEBP (se mira el contenido del archivo, no solo la extensión). Hasta 2 MB (2.097.152 bytes). | Formato: `El archivo no es PNG, JPG ni WEBP.` (`configuracion.tienda.error.logo-formato`) · Peso: `El logo pesa más de 2 MB.` (`configuracion.tienda.error.logo-pesado`) |

**Respuesta 200:**
```json
{ "logoUrl": "/api/v1/configuracion/tienda/logo?version=1760550000000" }
```

**Errores:**
- 400 con `errores.archivo` = `El logo pesa más de 2 MB.` o `El archivo no es PNG, JPG ni WEBP.` (CFG-A02). El logo anterior no cambia.
- 400 `Pedido inválido` si falta la parte `archivo`.

**HU de origen:** HU-003 (CA4).

---

## GET /api/v1/configuracion/tienda/logo
**Para qué sirve:** mostrar el logo guardado (es la dirección que trae `logoUrl`).

**Request:** opcional `?version=<n>` (lo ignora; solo sirve para que el navegador no use una copia vieja).

**Respuesta 200:** la imagen, con `Content-Type` `image/png`, `image/jpeg` o `image/webp`.

**Errores:** 404 `Sin logo` / `La tienda no tiene logo.` si no hay logo guardado.

**HU de origen:** HU-003 (CA4).

---

## PUT /api/v1/configuracion/sheet
**Para qué sirve:** conectar el Google Sheet de la tienda y elegir la pestaña del inventario (CFG-F02, CFG-F03, CFG-A07). La app entra al Sheet con la cuenta de servicio, en el momento:
1. Verifica que la sesión de Google del Gmail siga vigente (401 si no).
2. Verifica que la cuenta de servicio tenga acceso al Sheet (403 si no).
3. Pestaña del inventario:
   - si ya existe, no la toca (sin aviso);
   - si el Sheet tiene **una sola pestaña** con otro nombre, la **renombra** al pedido → aviso `La pestaña «{anterior}» se renombró a «{pestana}».` (`configuracion.sheet.aviso.renombrada`);
   - si tiene **varias** y ninguna se llama como la pedida, **crea** la pestaña nueva y no toca las demás → aviso `Se creó la pestaña «{pestana}».` (`configuracion.sheet.aviso.creada`).
4. Crea las pestañas que falten (`Movimientos`, `Caja`, `Reservas`, `Clientes`) con sus encabezados (los mismos de la v1).
5. Encabezados: solo se escriben en la fila 1 de una pestaña si está **vacía**. Si la fila 1 ya tiene datos, no se toca (el Sheet también se edita a mano).
6. Recién al final guarda el Sheet y la pestaña en la configuración. Si algo falla antes, lo guardado no cambia.

La comparación del nombre de la pestaña es exacta (respeta mayúsculas y espacios internos; se sacan los espacios del principio y del final).

**Request** (`application/json`):

| Campo | Tipo | Obligatorio | Validación | Mensaje si falla |
|---|---|---|---|---|
| `sheet` | string | sí | Link completo (`https://docs.google.com/spreadsheets/d/<ID>/...`) o solo el ID (letras, números, `-` y `_`, al menos 20 caracteres). | Vacío: `Pegá el link o el ID del Sheet.` (`configuracion.sheet.error.vacio`) · Otro formato: `Ese link no es de un Google Sheet.` (`configuracion.sheet.error.invalido`) |
| `pestanaInventario` | string | sí | No vacío ni solo espacios. | `Ingresá el nombre de la pestaña.` (`configuracion.sheet.error.pestana-vacia`) |

```json
{
  "sheet": "https://docs.google.com/spreadsheets/d/1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ/edit#gid=0",
  "pestanaInventario": "Inventario"
}
```

**Respuesta 200:** el bloque `sheet`.
```json
{
  "sheetId": "1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ",
  "link": "https://docs.google.com/spreadsheets/d/1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ/edit",
  "pestanaInventario": "Inventario",
  "gmail": "tienda@gmail.com",
  "aviso": "La pestaña «Hoja 1» se renombró a «Inventario»."
}
```

**Errores:**
- 400 con `errores.sheet` o `errores.pestanaInventario` (CFG-A03, CFG-A07). No cambia lo guardado.
- 401 si la sesión de Google venció (CFG-A05). No cambia lo guardado.
- 403 si el Sheet no está compartido con la cuenta de servicio (CFG-A04). No cambia lo guardado.
- 503 si no hay conexión con Google.

**HU de origen:** HU-003 (CA5 a CA10).

---

## PUT /api/v1/configuracion/precios
**Para qué sirve:** guardar la regla de precio: cotización, redondeo, precios bulk y carpeta de cache (CFG-F04). Después intenta sincronizar los precios con Card Kingdom; si eso falla, lo guardado queda y la respuesta trae un aviso (CFG-A09).

**Request** (`application/json`):

| Campo | Tipo | Obligatorio | Validación | Mensaje si falla |
|---|---|---|---|---|
| `cotizacion` | número | sí | Mayor a 0. | Vacío: `Ingresá la cotización del dólar.` · ≤ 0: `La cotización tiene que ser mayor a 0.` |
| `redondeo` | entero | sí | Mayor a 0. | Vacío: `Ingresá el múltiplo para redondear.` · ≤ 0: `El múltiplo tiene que ser mayor a 0.` |
| `bulk` | objeto | sí | Los cinco precios. | Si falta el objeto: `errores.bulk` = `Ingresá el precio.` |
| `bulk.common` · `bulk.uncommon` · `bulk.rare` · `bulk.mythic` · `bulk.tierraBasica` | número | sí | Mayor a 0 y sin centavos. | Vacío: `Ingresá el precio.` · ≤ 0: `Tiene que ser mayor a 0.` · Con centavos: `Sin centavos.` |
| `carpetaCache` | string | no | Si viene, tiene que ser una carpeta que exista. Vacío o `null` = la de siempre. | `No encontramos esa carpeta.` (`configuracion.precios.error.carpeta`) |

Las claves de `errores` para los bulk son `bulk.common`, `bulk.uncommon`, `bulk.rare`, `bulk.mythic` y `bulk.tierraBasica`.

```json
{
  "cotizacion": 1350,
  "redondeo": 500,
  "bulk": { "common": 50, "uncommon": 100, "rare": 150, "mythic": 150, "tierraBasica": 200 },
  "carpetaCache": null
}
```

**Respuesta 200:** el bloque `precios`.
```json
{
  "cotizacion": 1350,
  "redondeo": 500,
  "bulk": { "common": 50, "uncommon": 100, "rare": 150, "mythic": 150, "tierraBasica": 200 },
  "carpetaCache": null,
  "aviso": null
}
```
Si se guardó pero no se pudo sincronizar con Card Kingdom, igual responde 200, con `"aviso": "Guardado, pero no se pudo sincronizar con Card Kingdom. Probá más tarde."` (`configuracion.precios.aviso.falla-ck`).

**Errores:**
- 400 con `errores` por campo (CFG-A06, CFG-A08). No se guarda nada del bloque.

```json
{
  "type": "about:blank",
  "title": "Datos inválidos",
  "status": 400,
  "detail": "Hay datos para corregir.",
  "instance": "/api/v1/configuracion/precios",
  "errores": { "cotizacion": "La cotización tiene que ser mayor a 0.", "bulk.rare": "Sin centavos." }
}
```

**HU de origen:** HU-003 (CA11).

---

## PUT /api/v1/configuracion/puntos
**Para qué sirve:** guardar cuántos pesos vale un punto de cliente (CFG-F05).

**Request** (`application/json`):

| Campo | Tipo | Obligatorio | Validación | Mensaje si falla |
|---|---|---|---|---|
| `pesosPorPunto` | número | sí | Mayor a 0. | Vacío: `Ingresá cuántos pesos vale un punto.` (`configuracion.puntos.error.vacio`) · ≤ 0: `Tiene que ser mayor a 0.` (`general.error.mayor-a-cero`) |

```json
{ "pesosPorPunto": 500 }
```

**Respuesta 200:** el bloque `puntos`.
```json
{ "pesosPorPunto": 500 }
```

**Errores:** 400 con `errores.pesosPorPunto` (CFG-A06). No cambia lo guardado.

**HU de origen:** HU-003 (CA12).

---

## Modo prueba (perfil `e2e`)
Sin Google: la configuración vive en memoria (arranca con la tienda "Codex Live") y los Sheets son simulados. El Gmail conectado es `prueba@tcg-inventory.local`. Sheets de ejemplo:

| ID | Pestañas al arrancar | Sirve para |
|---|---|---|
| `1PruebaSheetConInventario000000000000000000` | Inventario | CA5 (sin aviso) |
| `1PruebaSheetConPestanaUnica0000000000000000` | Hoja 1 | CA6 (renombra) |
| `1PruebaSheetConVariasPestanas00000000000000` | Hoja 1, Ventas | CA7 (crea) |

Cualquier otro ID con formato válido responde 403 (Sheet no compartido).
