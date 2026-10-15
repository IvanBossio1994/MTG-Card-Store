# API v2 · Configuración

Contrato del módulo Configuración: leer y guardar cada bloque de la pantalla por separado (tienda, Sheet, precios y puntos).
Origen: [HU-003](../../agentes/historias/HU-003-configuracion-backend.md); pestañas del Sheet y "Traer cartas de": [HU-006](../../agentes/historias/HU-006-configuracion-backend-pestanas.md). Flujos: CFG-F01 a CFG-F06, CFG-A01 a CFG-A14 (`docs/funcional/documento-funcional.md`). Textos: `docs/v2/textos.md`.

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
| 400 | Un campo no pasa la validación (vacío, menor o igual a 0, formato) | `Datos inválidos` | `Hay datos para corregir.` | `errores`: campo → mensaje de `textos.md`. En los endpoints de pestañas (HU-006) suma `campo` y `codigo` (ver abajo) |
| 401 | Google rechaza la sesión del Gmail conectado (`invalid_grant`) o no hay sesión iniciada | `Sesión de Google vencida` | `La sesión de Google venció.` (`general.error.sesion-vencida`, tono HU-005) | `accion`: `"volver-a-iniciar-sesion"` · `urlInicioSesion`: `"/configuracion/google/oauth/iniciar"` |
| 403 | El Sheet no está compartido con la cuenta de servicio (o el ID no existe) | `Sin acceso al Sheet` | `La app no tiene acceso a este Sheet. Compartirlo con tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com como editor.` (`configuracion.sheet.error.sin-permiso`, tono HU-005) | `cuentaServicio`: `"tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com"` |
| 503 | No se pudo hablar con Google (sin internet o Google no responde) | `Sin conexión` | `No hay conexión a internet. Se puede reintentar cuando se restablezca.` (`general.error.conexion`, tono HU-005) | — |
| 500 | Windows no deja leer la carpeta de la sesión de Google (`data/google-oauth-token` a nombre de otro usuario, por ejemplo si la app se abrió como administrador) | `Sesión de Google ilegible` | `La app no puede leer la sesión de Google en esta PC. Abrirla sin permisos de administrador e iniciar sesión de nuevo.` (`general.error.sesion-ilegible`, tono HU-005) | — |

**Errores con `campo` y `codigo` (HU-006).** Los 400 y 404 de los endpoints de pestañas y el 400 `ORIGEN_INEXISTENTE` de `PUT /sheet` traen, además de `errores`, dos propiedades para que el front marque el campo sin comparar textos:
- `campo`: el campo de la pantalla que falló, en minúscula con guiones (`pestana-a-renombrar`, `nombre-nuevo`, `traer-cartas-de`, `link-sheet`). En los errores de bloque (404) es `null`.
- `codigo`: el motivo, en mayúsculas (`PESTANA_VACIA`, `NOMBRE_VACIO`, …). Fijo: el front puede depender de él.

Estas validaciones las hace el servicio (no `@Valid`), para que todas traigan `codigo`. `errores` sigue con la clave del campo del JSON (`nombreActual`, `nombreNuevo`, `traerCartasDe`, `idOLink`), igual que el resto del módulo.

Ejemplo 400 (validación):
```json
{
  "type": "about:blank",
  "title": "Datos inválidos",
  "status": 400,
  "detail": "Hay datos para corregir.",
  "instance": "/api/v1/configuracion/tienda",
  "errores": { "nombre": "El nombre de la tienda es obligatorio." }
}
```

Ejemplo 401:
```json
{
  "type": "about:blank",
  "title": "Sesión de Google vencida",
  "status": 401,
  "detail": "La sesión de Google venció.",
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
  "detail": "La app no tiene acceso a este Sheet. Compartirlo con tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com como editor.",
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
| `aviso` | string, puede ser `null` | Solo en la respuesta de `PUT /sheet`: `Se creó la pestaña «{pestana}».` (`configuracion.sheet.aviso.creada`) cuando creó la pestaña del inventario **sin** traer cartas. En los demás casos y en el `GET`, `null`. (HU-006: ya no hay aviso de "renombrada" acá.) |
| `pestanaCreada` | booleano | Solo en `PUT /sheet`: `true` si la pestaña del inventario no existía y se creó (con o sin cartas, o usando la "Hoja 1" vacía de un Sheet nuevo). En el `GET`, `false`. (HU-006) |
| `cartasTraidas` | objeto, puede ser `null` | Solo en `PUT /sheet` cuando se creó la pestaña trayendo cartas: `{ "origen": "stock viejo", "cantidad": 128, "hayColumnasExtra": true }`. Con eso el front arma `configuracion.sheet.traer.exito` (y `traer.exito-columnas-extra` si `hayColumnasExtra`). En los demás casos y en el `GET`, `null`. (HU-006) |

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
    "aviso": null,
    "pestanaCreada": false,
    "cartasTraidas": null
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
| `nombre` | string | sí | No vacío ni solo espacios. Se guarda sin espacios al principio ni al final. | `El nombre de la tienda es obligatorio.` (`configuracion.tienda.error.nombre-vacio`) |
| `quitarLogo` | booleano | no (por defecto `false`) | — | — |

```json
{ "nombre": "Codex Live Store", "quitarLogo": false }
```

**Respuesta 200:** el bloque `tienda`.
```json
{ "nombre": "Codex Live Store", "logoUrl": null }
```

**Errores:**
- 400 con `errores.nombre` = `El nombre de la tienda es obligatorio.` si el nombre está vacío (CFG-A01). No cambia nada.

**HU de origen:** HU-003 (CA2, CA3).

---

## POST /api/v1/configuracion/tienda/logo
**Para qué sirve:** subir el logo de la tienda; reemplaza al anterior en el momento (CFG-F01).

**Request** (`multipart/form-data`):

| Parte | Tipo | Obligatorio | Validación | Mensaje si falla |
|---|---|---|---|---|
| `archivo` | archivo | sí | PNG, JPG o WEBP (se mira el contenido del archivo, no solo la extensión). Hasta 2 MB (2.097.152 bytes). | Formato: `El logo debe ser PNG, JPG o WEBP.` (`configuracion.tienda.error.logo-formato`) · Peso: `El logo no debe superar los 2 MB.` (`configuracion.tienda.error.logo-pesado`) |

**Respuesta 200:**
```json
{ "logoUrl": "/api/v1/configuracion/tienda/logo?version=1760550000000" }
```

**Errores:**
- 400 con `errores.archivo` = `El logo no debe superar los 2 MB.` o `El logo debe ser PNG, JPG o WEBP.` (CFG-A02). El logo anterior no cambia.
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
**Para qué sirve:** conectar el Google Sheet de la tienda, elegir la pestaña del inventario y, si es nueva, traerle las cartas de otra pestaña (CFG-F02, CFG-A03, CFG-A07). **Cambia en HU-006: "Guardar inventario" ya no renombra pestañas** (para eso está `PUT /sheet/pestanas/renombrar`). La app entra al Sheet con la cuenta de servicio, en el momento:
1. Verifica que la sesión de Google del Gmail siga vigente (401 si no).
2. Verifica que la cuenta de servicio tenga acceso al Sheet (403 si no).
3. Pestaña del inventario:
   - **ya existe** → la usa tal cual, sin copiar ni pisar nada. `traerCartasDe` se ignora (no da error, aunque el origen no exista). `pestanaCreada: false`, sin aviso;
   - **no existe y viene `traerCartasDe`** → si la pestaña de origen no existe, 400 `ORIGEN_INEXISTENTE` y no se escribe nada. Si existe, crea la pestaña con las cartas de la de origen en el formato de la app (ver "Traer cartas" abajo). La de origen no se modifica. `pestanaCreada: true`, `cartasTraidas` con los datos, `aviso: null`;
   - **no existe, sin `traerCartasDe`, y el Sheet es nuevo** (tiene **una sola pestaña y está completamente vacía**, la "Hoja 1" que crea Google, se llame como se llame según el idioma) → le pone el nombre pedido en lugar de crear otra. `pestanaCreada: true`, aviso `Se creó la pestaña «{pestana}».`;
   - **no existe, sin `traerCartasDe`, en cualquier otro caso** → crea la pestaña nueva con los encabezados y no toca las demás (aunque haya una sola con datos). `pestanaCreada: true`, mismo aviso.
4. Crea las pestañas que falten (`Movimientos`, `Caja`, `Reservas`, `Clientes`) con sus encabezados (los mismos de la v1).
5. Encabezados: solo se escriben en la fila 1 de una pestaña si está **vacía**. Si la fila 1 ya tiene datos, no se toca (el Sheet también se edita a mano).
6. Recién al final guarda el Sheet y la pestaña en la configuración. Si algo falla antes, lo guardado no cambia.

La comparación del nombre de la pestaña es exacta (respeta mayúsculas y espacios internos; se sacan los espacios del principio y del final). Lo mismo para `traerCartasDe`.

**Traer cartas (HU-006):**
- Se lee la pestaña de origen entera en **una** llamada. La fila 1 son los encabezados; cada columna se reconoce por los **alias** de la v1 (sin distinguir mayúsculas, tildes ni signos: `Cantidad`/`Stock`/`Qty`, `Nombre`/`Carta`/`Card`, `Precio`/`ARS`, etc.).
- La pestaña nueva queda con las 11 columnas de la app en su orden y con sus nombres (`Cantidad`, `Nombre`, `Codigo de set`, `Nombre del set`, `Numero de carta`, `Condicion`, `Printing (foil/no foil)`, `Idioma`, `Precio Local`, `Precio CK USD`, `Accion`). Las columnas de la app que el origen no tiene quedan vacías.
- Las columnas que no se reconocen pasan igual, **al final**, con su encabezado original y en el orden en que estaban → `hayColumnasExtra: true`. Si dos columnas del origen corresponden al mismo dato, se usa la primera y la otra pasa como extra. Las columnas sin encabezado y sin datos se descartan.
- Las **filas completamente vacías** se saltean (no se copian ni se cuentan). `cantidad` = filas copiadas (una carta por fila, sin mirar la columna Cantidad). Origen con solo encabezados → se crea la pestaña igual, con `cantidad: 0`.
- Se copian los **valores**, no las fórmulas (una fórmula pasa como su resultado). Los números siguen siendo números (sin el formato de moneda del origen), las fechas pasan como se ven y el resto, como texto.
- Crear la pestaña y escribir las cartas va en **una sola llamada** a Google (todo o nada): si falla, no queda una pestaña vacía a medias.
- `traerCartasDe` puede ser cualquier pestaña del Sheet (la lista marca como `sugerida` las que tienen encabezados de inventario, pero no es obligatorio).

**Request** (`application/json`):

| Campo | Tipo | Obligatorio | Validación | Mensaje si falla |
|---|---|---|---|---|
| `sheet` | string | sí | Link completo (`https://docs.google.com/spreadsheets/d/<ID>/...`) o solo el ID (letras, números, `-` y `_`, al menos 20 caracteres). | Vacío: `El ID o la URL del Sheet es obligatorio.` (`configuracion.sheet.error.vacio`) · Otro formato: `Debe ser una URL de Google Sheets o solo el ID.` (`configuracion.sheet.error.invalido`) |
| `pestanaInventario` | string | sí | No vacío ni solo espacios. | `El nombre de la pestaña es obligatorio.` (`configuracion.sheet.error.pestana-vacia`) |
| `traerCartasDe` | string | no (HU-006) | Nombre de la pestaña de origen. `null`, ausente o vacío = "No traer cartas". Solo cuenta si la pestaña del inventario no existe. | Si no existe en el Sheet: `La pestaña de origen ya no existe en el Sheet. Se actualizó la lista.` (`configuracion.sheet.traer.error.origen-inexistente`) |

Se mantienen los nombres `sheet` y `pestanaInventario` que ya usa el front (la propuesta de la spec UX decía `idOLink` y `nombrePestanaInventario`; no se renombran para no romper HU-004).

```json
{
  "sheet": "https://docs.google.com/spreadsheets/d/1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ/edit#gid=0",
  "pestanaInventario": "Stock 2026",
  "traerCartasDe": "stock viejo"
}
```

**Respuesta 200:** el bloque `sheet`.

Trayendo cartas:
```json
{
  "sheetId": "1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ",
  "link": "https://docs.google.com/spreadsheets/d/1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ/edit",
  "pestanaInventario": "Stock 2026",
  "gmail": "tienda@gmail.com",
  "aviso": null,
  "pestanaCreada": true,
  "cartasTraidas": { "origen": "stock viejo", "cantidad": 128, "hayColumnasExtra": true }
}
```
El front muestra: "Se creó la pestaña «Stock 2026» con 128 cartas de «stock viejo». Las columnas que la app no usa quedaron al final." (`configuracion.sheet.traer.exito` + `traer.exito-columnas-extra`).

Creando sin cartas: igual, con `"aviso": "Se creó la pestaña «Stock 2026»."`, `"pestanaCreada": true` y `"cartasTraidas": null`.
Pestaña existente: `"aviso": null`, `"pestanaCreada": false`, `"cartasTraidas": null` (el front muestra `configuracion.sheet.exito`).

**Errores:**
- 400 con `errores.sheet` o `errores.pestanaInventario` (CFG-A03, CFG-A07). No cambia lo guardado.
- 400 `ORIGEN_INEXISTENTE`: la pestaña de `traerCartasDe` no está en el Sheet (alguien la borró o renombró). No se crea nada ni cambia lo guardado.
  ```json
  {
    "type": "about:blank",
    "title": "Datos inválidos",
    "status": 400,
    "detail": "Hay datos para corregir.",
    "instance": "/api/v1/configuracion/sheet",
    "errores": { "traerCartasDe": "La pestaña de origen ya no existe en el Sheet. Se actualizó la lista." },
    "campo": "traer-cartas-de",
    "codigo": "ORIGEN_INEXISTENTE"
  }
  ```
- 401 si la sesión de Google venció (CFG-A05). No cambia lo guardado.
- 403 si el Sheet no está compartido con la cuenta de servicio (CFG-A04). No cambia lo guardado.
- 503 si no hay conexión con Google.

**Ya no existe:** el renombrado automático (de la pestaña del inventario anterior o de la única pestaña con datos) y su aviso `configuracion.sheet.aviso.renombrada`, que pasa a `PUT /sheet/pestanas/renombrar`. Nunca hubo un campo `pestanaRenombradaDesde` en la respuesta implementada; la propuesta de la spec UX lo nombraba y queda descartado.

**HU de origen:** HU-003 (CA5 a CA10); cambios de HU-006 (CA7 a CA10).

---

## GET /api/v1/configuracion/sheet/pestanas
**Para qué sirve:** listar las pestañas de un Sheet, para las listas "Pestaña a renombrar" y "Traer cartas de" (CFG-F02, CFG-F03). Solo lee; no cambia nada.

**Request:** sin cuerpo. Parámetro opcional:

| Parámetro | Tipo | Obligatorio | Validación | Mensaje si falla |
|---|---|---|---|---|
| `idOLink` | string (query) | no | Mismo formato que `sheet` de `PUT /sheet`. Si viene (y no está vacío), lista las pestañas de **ese** Sheet, aunque no esté guardado (lo usa "Traer cartas de"). Si no viene, usa el Sheet guardado. | `Debe ser una URL de Google Sheets o solo el ID.` (`configuracion.sheet.error.invalido`) |

Ejemplos: `GET /api/v1/configuracion/sheet/pestanas` · `GET /api/v1/configuracion/sheet/pestanas?idOLink=https%3A%2F%2Fdocs.google.com%2Fspreadsheets%2Fd%2F1vSk…%2Fedit`

**Respuesta 200:**

| Campo | Tipo | Descripción |
|---|---|---|
| `sheetGuardado` | booleano | `true` si hay un Sheet para listar (el guardado o el de `idOLink`). `false` solo si no vino `idOLink` y no hay Sheet guardado. |
| `pestanas` | lista | En el orden del Sheet. Vacía si `sheetGuardado` es `false`. |
| `pestanas[].nombre` | string | Nombre de la pestaña, tal cual. |
| `pestanas[].enUso` | booleano | `true` si es la pestaña del inventario guardada **y** el Sheet listado es el guardado. Con `idOLink` de otro Sheet, siempre `false`. |
| `pestanas[].sugerida` | booleano | `true` si su fila 1 tiene encabezados de inventario: se reconoce por alias la columna **Nombre** y al menos otra columna de la app. Independiente de `enUso` (la del inventario también suele ser sugerida). Las `fija` nunca son sugeridas (Movimientos, Caja y Reservas tienen columnas parecidas). |
| `pestanas[].fija` | booleano | `true` si es una de las pestañas que la app busca por nombre (`Movimientos`, `Caja`, `Reservas`, `Clientes`, nombre exacto): no se pueden renombrar (D1). El front las muestra deshabilitadas en "Pestaña a renombrar". |

Primero valida `idOLink` (400), después verifica la sesión de Google (401, D3: también con `idOLink`), y recién ahí lee el Sheet: una llamada para la lista y otra (`batchGet`) para la fila 1 de todas las pestañas.

```json
{
  "sheetGuardado": true,
  "pestanas": [
    { "nombre": "Inventario", "enUso": true, "sugerida": true, "fija": false },
    { "nombre": "stock viejo", "enUso": false, "sugerida": true, "fija": false },
    { "nombre": "Caja", "enUso": false, "sugerida": false, "fija": true }
  ]
}
```

Sin Sheet guardado y sin `idOLink` (no es error; no llama a Google):
```json
{ "sheetGuardado": false, "pestanas": [] }
```

**Errores:**
- 400 si `idOLink` no tiene formato válido: `errores.idOLink`, `campo: link-sheet`, `codigo: LINK_INVALIDO`.
- 401 si la sesión de Google venció.
- 403 si el Sheet no está compartido con la cuenta de servicio (también si el ID no existe).
- 503 si no hay conexión con Google.
- 500 inesperado.

En todos los casos el front muestra `configuracion.sheet.renombrar.error.lista` / `traer.error.lista` ("No se pudieron cargar las pestañas del Sheet.") con Reintentar.

**HU de origen:** HU-006 (CA1 a CA3, CA10).

---

## PUT /api/v1/configuracion/sheet/pestanas/renombrar
**Para qué sirve:** cambiarle el nombre a una pestaña del **Sheet guardado** (CFG-F03). Si es la pestaña del inventario, también guarda el nombre nuevo en la configuración, en el momento (lo que lee la v1 también). Escribe en el Sheet en el momento.

Orden: valida el pedido (400, incluye `PESTANA_FIJA`) → sin Sheet guardado (400) → sesión (401) → acceso (403) → lee las pestañas: si `nombreActual` no está, 404; si `nombreNuevo` choca con otra, 400 → renombra → si era la del inventario, guarda la configuración (si eso falla, vuelve a poner el nombre anterior en el Sheet y responde 500, D4) → responde con la lista actualizada.

**Request** (`application/json`). Los nombres se comparan sin los espacios del principio y del final.

| Campo | Tipo | Obligatorio | Validación | `campo` / `codigo` | Mensaje si falla |
|---|---|---|---|---|---|
| `nombreActual` | string | sí | No vacío. | `pestana-a-renombrar` / `PESTANA_VACIA` | `La pestaña a renombrar es obligatoria.` (`configuracion.sheet.renombrar.error.sin-pestana`) |
| | | | No es `Movimientos`, `Caja`, `Reservas` ni `Clientes` (nombre exacto; D1). | `pestana-a-renombrar` / `PESTANA_FIJA` | `Esa pestaña la usa la app y no se puede renombrar.` (`configuracion.sheet.renombrar.error.fija`) |
| `nombreNuevo` | string | sí | No vacío. | `nombre-nuevo` / `NOMBRE_VACIO` | `El nombre nuevo es obligatorio.` (`…renombrar.error.nombre-vacio`) |
| | | | Hasta 100 caracteres (límite de Google). | `nombre-nuevo` / `NOMBRE_LARGO` | `El nombre no debe superar los 100 caracteres.` (`…renombrar.error.nombre-largo`) |
| | | | Distinto del actual (comparación exacta: cambiar solo mayúsculas sí se permite). | `nombre-nuevo` / `NOMBRE_IGUAL` | `El nombre nuevo es igual al actual.` (`…renombrar.error.nombre-igual`) |
| | | | Que no exista **otra** pestaña con ese nombre, sin distinguir mayúsculas (Google tampoco lo permite). | `nombre-nuevo` / `NOMBRE_REPETIDO` | `Ya existe una pestaña con ese nombre.` (`…renombrar.error.nombre-repetido`) |

Si fallan los dos campos, responde con el primero (`nombreActual`), pero `errores` trae los dos.

```json
{ "nombreActual": "Inventario", "nombreNuevo": "Stock 2026" }
```

**Respuesta 200:**

| Campo | Tipo | Descripción |
|---|---|---|
| `nombreAnterior` | string | Nombre que tenía. |
| `nombreNuevo` | string | Nombre que tiene ahora (sin espacios al principio ni al final). |
| `eraPestanaInventario` | booleano | `true` si era la pestaña del inventario: la configuración **ya quedó guardada** con el nombre nuevo. |
| `nombrePestanaInventario` | string | Pestaña del inventario después del cambio (para actualizar el campo de la pantalla). |
| `pestanas` | lista | La lista actualizada, con la misma forma que en `GET /sheet/pestanas`. |

```json
{
  "nombreAnterior": "Inventario",
  "nombreNuevo": "Stock 2026",
  "eraPestanaInventario": true,
  "nombrePestanaInventario": "Stock 2026",
  "pestanas": [
    { "nombre": "Stock 2026", "enUso": true, "sugerida": true, "fija": false },
    { "nombre": "Caja", "enUso": false, "sugerida": false, "fija": true }
  ]
}
```
El front muestra `configuracion.sheet.renombrar.exito` ("La pestaña «{anterior}» se renombró a «{pestana}».") o, si `eraPestanaInventario` (como en el ejemplo), `configuracion.sheet.aviso.renombrada` ("La pestaña «Inventario» se renombró a «Stock 2026». También se actualizó la pestaña del inventario.").

**Errores:**
- 400 de validación, con `errores`, `campo` y `codigo` (tabla de arriba). Ejemplo:
  ```json
  {
    "type": "about:blank",
    "title": "Datos inválidos",
    "status": 400,
    "detail": "Hay datos para corregir.",
    "instance": "/api/v1/configuracion/sheet/pestanas/renombrar",
    "errores": { "nombreNuevo": "Ya existe una pestaña con ese nombre." },
    "campo": "nombre-nuevo",
    "codigo": "NOMBRE_REPETIDO"
  }
  ```
- 400 `SIN_SHEET_GUARDADO`: no hay Sheet guardado (el front no deja llegar). `title` `Sin Sheet guardado`, `detail` `Disponible cuando haya un Sheet guardado.` (`configuracion.sheet.renombrar.ayuda.sin-sheet`), `campo: null`.
- 404 `PESTANA_INEXISTENTE`: `nombreActual` ya no está en el Sheet (la borraron o renombraron a mano). No cambia nada.
  ```json
  {
    "type": "about:blank",
    "title": "Pestaña inexistente",
    "status": 404,
    "detail": "La pestaña ya no existe en el Sheet. Se actualizó la lista.",
    "instance": "/api/v1/configuracion/sheet/pestanas/renombrar",
    "campo": null,
    "codigo": "PESTANA_INEXISTENTE"
  }
  ```
- 401 si la sesión de Google venció · 403 sin acceso al Sheet · 503 sin conexión · 500 inesperado (el front muestra `general.error.inesperado`). En todos, no cambia nada.
- Si Google renombra pero falla guardar la configuración (solo cuando `eraPestanaInventario`): la app vuelve a ponerle el nombre anterior a la pestaña en el Sheet (compensación, D4) y responde 500 `Error interno` / `No se pudo guardar por un error inesperado. Se puede reintentar en un momento.` (`general.error.inesperado`). Así el Sheet y la configuración siguen de acuerdo.

**HU de origen:** HU-006 (CA4 a CA6, CA10).

---

## PUT /api/v1/configuracion/precios
**Para qué sirve:** guardar la regla de precio: cotización, redondeo, precios bulk y carpeta de cache (CFG-F04). Después intenta sincronizar los precios con Card Kingdom; si eso falla, lo guardado queda y la respuesta trae un aviso (CFG-A09).

**Request** (`application/json`):

| Campo | Tipo | Obligatorio | Validación | Mensaje si falla |
|---|---|---|---|---|
| `cotizacion` | número | sí | Mayor a 0. | Vacío: `La cotización del dólar es obligatoria.` (`configuracion.precios.error.cotizacion-vacia`) · ≤ 0: `La cotización debe ser mayor a 0.` (`…cotizacion-menor`) |
| `redondeo` | entero | sí | Mayor a 0. | Vacío: `El múltiplo para redondear es obligatorio.` (`…redondeo-vacio`) · ≤ 0: `El múltiplo debe ser mayor a 0.` (`…redondeo-menor`) |
| `bulk` | objeto | sí | Los cinco precios. | Si falta el objeto: `errores.bulk` = `El precio es obligatorio.` |
| `bulk.common` · `bulk.uncommon` · `bulk.rare` · `bulk.mythic` · `bulk.tierraBasica` | número | sí | Mayor a 0 y sin centavos. | Vacío: `El precio es obligatorio.` (`…bulk-vacio`) · ≤ 0: `Debe ser mayor a 0.` (`general.error.mayor-a-cero`) · Con centavos: `El precio debe ser un número entero, sin centavos.` (`general.error.sin-centavos`) |
| `carpetaCache` | string | no | Si viene, tiene que ser una carpeta que exista. Vacío o `null` = la de siempre. | `La carpeta no existe. Se puede indicar otra o dejar el campo vacío.` (`configuracion.precios.error.carpeta`) |

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
Si se guardó pero no se pudo sincronizar con Card Kingdom, igual responde 200, con `"aviso": "Guardado, pero no se pudo sincronizar con Card Kingdom. Se puede reintentar más tarde."` (`configuracion.precios.aviso.falla-ck`).

**Errores:**
- 400 con `errores` por campo (CFG-A06, CFG-A08). No se guarda nada del bloque.

```json
{
  "type": "about:blank",
  "title": "Datos inválidos",
  "status": 400,
  "detail": "Hay datos para corregir.",
  "instance": "/api/v1/configuracion/precios",
  "errores": { "cotizacion": "La cotización debe ser mayor a 0.", "bulk.rare": "El precio debe ser un número entero, sin centavos." }
}
```

**HU de origen:** HU-003 (CA11).

---

## PUT /api/v1/configuracion/puntos
**Para qué sirve:** guardar cuántos pesos vale un punto de cliente (CFG-F05).

**Request** (`application/json`):

| Campo | Tipo | Obligatorio | Validación | Mensaje si falla |
|---|---|---|---|---|
| `pesosPorPunto` | número | sí | Mayor a 0. | Vacío: `Los pesos por punto son obligatorios.` (`configuracion.puntos.error.vacio`) · ≤ 0: `Debe ser mayor a 0.` (`general.error.mayor-a-cero`) |

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
| `1PruebaSheetConInventario000000000000000000` | Inventario (con encabezados) | HU-003 CA5 (sin aviso); HU-006 listar y renombrar la del inventario |
| `1PruebaSheetConPestanaUnica0000000000000000` | Hoja 1 (vacía) | HU-006: Sheet nuevo, usa la "Hoja 1" (antes: renombraba) |
| `1PruebaSheetConVariasPestanas00000000000000` | Hoja 1, Ventas | Crea la pestaña |
| `1PruebaSheetConStockViejo00000000000000000` | Inventario, stock viejo (3 cartas, con una columna extra "Notas" y una fila vacía en el medio) | HU-006: "Traer cartas de" (`cantidad: 3`, `hayColumnasExtra: true`) y `sugerida` |

Cualquier otro ID con formato válido responde 403 (Sheet no compartido).
