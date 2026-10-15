# HU-003: Configuración v2 · backend

- **Estado:** aceptada
- **Épica:** [EP-001](../epicas/EP-001-configuracion-v2.md)
- **Tipo:** backend
- **Módulo:** configuracion (y la base de acceso al Sheet en `google`)
- **Rama:** `v2/feature/HU-003-configuracion-backend`
- **Funcional documentado:** reusa los de HU-001
- **Flujos funcionales:** CFG-F01, CFG-F02, CFG-F03, CFG-F04, CFG-F05, CFG-F06, CFG-A01 a CFG-A09

## Historia
**Como** dueño del comercio
**quiero** que la app tenga una API para leer y guardar cada bloque de Configuración por separado
**para** que la pantalla nueva (HU-004) guarde sin recargar, con mensajes claros, y que la pestaña del inventario quede con el nombre que pedí

## Descripción
Hoy la configuración se guarda desde la v1 (`legacy`, `POST /configuracion…`) con un solo botón que guarda todo y recarga la pantalla. Además, si el Sheet tiene una sola pestaña, la v1 la adopta y pisa el nombre guardado (bug). Esta HU arma, en el paquete `configuracion`:
- **Contrato primero:** `docs/v2/api/configuracion.md`, antes de programar.
- **`ConfiguracionController`** (`/api/v1/configuracion`), con un endpoint por bloque.
- **Mismo almacenamiento que la v1:** lo que se guarda en la v2 se ve en la v1 y al revés. La v1 no se modifica (solo se lee como referencia).
- **Base de acceso al Sheet con la cuenta de servicio** (en `google/`, según `estado.md`, Google, punto 7), con una versión en memoria para los tests y el perfil `e2e`. Por ahora la usa Configuración: verificar el acceso, crear o renombrar la pestaña y crear las pestañas y encabezados que falten. La clave se toma de `config/google-service-account.json`, sin leer su contenido ni mostrarlo.
- **Arreglo de la pestaña:** con una sola pestaña distinta de la pedida, se renombra. Con varias y sin la pedida, se crea con sus encabezados. En los dos casos la respuesta lo avisa.
- **Errores:** `ProblemDetail` en español (`ManejadorErroresApi`), con los textos de `docs/v2/textos.md`. 400 para datos inválidos, 403 si el Sheet no está compartido con la cuenta de servicio y 401 si venció la sesión de Google del Gmail (`invalid_grant`).

## Criterios de aceptación
**CA1: Ver la configuración guardada (CFG-F06)**
- **Given** la tienda tiene configurados el nombre, el Sheet, la pestaña, los precios y los puntos
- **When** se consulta `GET /api/v1/configuracion`
- **Then** recibe 200 con los cuatro bloques (tienda, sheet, precios, puntos) y sus valores guardados
- **And** el bloque sheet incluye el Gmail conectado

**CA2: Guardar la tienda (CFG-F01)**
- **Given** la tienda se llama "Codex Live"
- **When** se envía `PUT /api/v1/configuracion/tienda` con el nombre "Codex Live Store"
- **Then** recibe 200 con el nombre "Codex Live Store"
- **And** la v1 muestra el nombre nuevo

**CA3: Nombre de tienda vacío (CFG-A01)**
- **Given** la tienda se llama "Codex Live"
- **When** se envía `PUT /api/v1/configuracion/tienda` con el nombre vacío
- **Then** recibe 400 con el mensaje "Ingresá el nombre de la tienda." para el campo `nombre`
- **And** el nombre guardado sigue siendo "Codex Live"

**CA4: Subir un logo válido o inválido (CFG-F01, CFG-A02)**
- **Given** la tienda no tiene logo
- **When** se envía `POST /api/v1/configuracion/tienda/logo` con un PNG de 500 KB
- **Then** recibe 200 con la dirección del logo nuevo
- **And** si el archivo pesa más de 2 MB, recibe 400 con "El logo pesa más de 2 MB."
- **And** si no es PNG, JPG ni WEBP, recibe 400 con "El archivo no es PNG, JPG ni WEBP."

**CA5: Conectar un Sheet con la pestaña existente (CFG-F02)**
- **Given** el Sheet compartido con la cuenta de servicio tiene la pestaña "Inventario"
- **When** se envía `PUT /api/v1/configuracion/sheet` con el link completo del Sheet y la pestaña "Inventario"
- **Then** recibe 200 con el ID del Sheet y la pestaña "Inventario", sin aviso
- **And** se crean las pestañas que falten (Movimientos, Caja, Reservas, Clientes) con sus encabezados

**CA6: Renombrar la pestaña única (CFG-F03, bug)**
- **Given** el Sheet tiene una sola pestaña, "Hoja 1"
- **When** se envía `PUT /api/v1/configuracion/sheet` con la pestaña "Inventario"
- **Then** la pestaña del Sheet pasa a llamarse "Inventario"
- **And** la respuesta trae el aviso "La pestaña «Hoja 1» se renombró a «Inventario»."
- **And** el nombre guardado en la configuración es "Inventario"

**CA7: Crear la pestaña si hay varias y falta la pedida (CFG-A07)**
- **Given** el Sheet tiene las pestañas "Hoja 1" y "Ventas"
- **When** se envía `PUT /api/v1/configuracion/sheet` con la pestaña "Inventario"
- **Then** se crea la pestaña "Inventario" con sus encabezados
- **And** "Hoja 1" y "Ventas" no cambian
- **And** la respuesta trae el aviso "Se creó la pestaña «Inventario»."

**CA8: Sheet o pestaña inválidos (CFG-A03, CFG-A07)**
- **Given** la configuración tiene un Sheet guardado
- **When** se envía `PUT /api/v1/configuracion/sheet` con un link vacío, un link que no es de Google Sheets o la pestaña vacía
- **Then** recibe 400 con "Pegá el link o el ID del Sheet.", "Ese link no es de un Google Sheet." o "Ingresá el nombre de la pestaña.", según el caso
- **And** no cambia lo guardado

**CA9: Sheet sin permiso (CFG-A04)**
- **Given** el Sheet no está compartido con la cuenta de servicio
- **When** se envía `PUT /api/v1/configuracion/sheet` con ese Sheet
- **Then** recibe 403 con el mensaje que pide compartirlo con `tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com` como editor
- **And** no cambia lo guardado

**CA10: Sesión de Google vencida (CFG-A05)**
- **Given** Google rechaza la sesión del Gmail conectado (`invalid_grant`)
- **When** se llama a cualquier endpoint de Configuración que necesita la sesión
- **Then** recibe 401 con el mensaje "Tu sesión de Google venció."
- **And** la respuesta indica que hay que volver a iniciar sesión

**CA11: Guardar precios (CFG-F04, CFG-A06, CFG-A08, CFG-A09)**
- **Given** la cotización guardada es 1200
- **When** se envía `PUT /api/v1/configuracion/precios` con la cotización 1350, el múltiplo, los cinco precios bulk y sin carpeta de cache
- **Then** recibe 200 con los valores nuevos
- **And** si algún número está vacío o es menor o igual a 0, recibe 400 con el mensaje del campo y no guarda nada del bloque
- **And** si la carpeta de cache no existe, recibe 400 con "No encontramos esa carpeta."
- **And** si guarda pero falla la sincronización con Card Kingdom, recibe 200 con el aviso "Guardado, pero no se pudo sincronizar con Card Kingdom. Probá más tarde."

**CA12: Guardar puntos (CFG-F05, CFG-A06)**
- **Given** los pesos por punto guardados son 1000
- **When** se envía `PUT /api/v1/configuracion/puntos` con 500
- **Then** recibe 200 con 500
- **And** si el valor está vacío o es menor o igual a 0, recibe 400 con el mensaje del campo

## Contrato de API — HU de backend
Resumen; el contrato completo (campos exactos y ejemplos) lo escribe `backend-java` en `docs/v2/api/configuracion.md` antes de programar.

| Método | Ruta | Request | Respuesta OK | Errores |
|---|---|---|---|---|
| GET | `/api/v1/configuracion` | — | 200 `{ tienda, sheet, precios, puntos }` | 401 |
| PUT | `/api/v1/configuracion/tienda` | `{ nombre, quitarLogo? }` | 200 `{ nombre, logoUrl }` | 400 |
| POST | `/api/v1/configuracion/tienda/logo` | multipart `archivo` | 200 `{ logoUrl }` | 400 |
| PUT | `/api/v1/configuracion/sheet` | `{ sheet, pestanaInventario }` | 200 `{ sheetId, pestanaInventario, aviso? }` | 400, 401, 403 |
| PUT | `/api/v1/configuracion/precios` | `{ cotizacion, redondeo, bulk: {common, uncommon, rare, mythic, tierraBasica}, carpetaCache? }` | 200 `{ ..., aviso? }` | 400 |
| PUT | `/api/v1/configuracion/puntos` | `{ pesosPorPunto }` | 200 `{ pesosPorPunto }` | 400 |

Errores con `ProblemDetail`: `detail` con el texto de `textos.md` y, en los 400, la lista de campos con su mensaje.

## Fuera de alcance
- La pantalla (HU-004) y el QA E2E (HU-005).
- Cambios en la v1 o migración de sus datos a otro formato.
- Escritura por fila del inventario: se resuelve en la épica de Inventario (queda en el backlog).
- Cache de Card Kingdom (fase 3).
- Tutorial.

## Tareas (en orden)
| # | Agente | Tarea | Listo cuando |
|---|---|---|---|
| 1 | backend-java | Contrato en `docs/v2/api/configuracion.md`; base de acceso al Sheet con la cuenta de servicio (y en memoria); `ConfiguracionController` y su servicio; arreglo de la pestaña; errores 400, 401 y 403; tests unitarios y de API (MockMvc) de CA1 a CA12 | Contrato escrito, `mvn.cmd -q test` pasa |

## Consumo
| Agente | Llamada | Tokens |
|---|---|---|
| backend-java | 1 (frenada sin querer, sin archivos) | sin dato |
| backend-java | 2 (tarea 1 completa, 78 herramientas, ~20 min) | 197.307 |
| **Total** | | **197.307** (+ la llamada frenada) |

## Registro
| Fecha | Paso | Resultado |
|---|---|---|
| 2026-10-15 | Brief aprobado | Historia armada desde EP-001; reusa los flujos CFG de HU-001 |
| 2026-10-15 | Inicio de desarrollo | Rama `v2/feature/HU-003-configuracion-backend` creada desde `v2/dev`; tarea 1 a `backend-java` |
| 2026-10-15 | Reinicio de la tarea 1 | La primera llamada a `backend-java` se frenó sin querer y sin crear archivos; se lanzó una nueva desde cero |
| 2026-10-15 | Tarea 1 entregada | Contrato `docs/v2/api/configuracion.md`, API, base `AccesoSheet` (cuenta de servicio y memoria), 54 tests nuevos. El líder verificó `mvn -q test` (exit 0). Riesgo: la v1 guarda la configuración en memoria y puede pisar lo de la v2 |
| 2026-10-15 | Decisión de la dueña | La v1 queda solo como referencia: no se toca. Si se cambia la configuración en la v2, la v1 la ve al reiniciar la app |
| 2026-10-15 | Verificación con la app levantada | Bloqueada: la app levantada por el líder no puede leer la sesión de Google (`AccessDeniedException` en `data/`, por el sandbox), y responde 503 "Revisá tu conexión…". Defecto chico a corregir: ese error no es de internet y el mensaje confunde. Siguiente: la levanta la dueña con `!` |
| 2026-10-15 | Permisos de `data/google-oauth-token` | La carpeta estaba a nombre de otro usuario de Windows; la dueña la recuperó con `takeown` + `icacls /reset` (como administrador). La app se levanta desde la terminal del IDE, no como administrador |
| 2026-10-15 | Prueba con la app (dueña conectada) | CA10 OK (401 con sesión vencida). CA1 OK (GET con Gmail). El Sheet configurado antes era `1uLN5YrQknRUwveuztwc_MnEOCau8Q14msD1k56VYQcQ` (anotado para poder volver); la dueña pidió pasar a su Sheet de pruebas `1vSk7…` |
| 2026-10-15 | Pruebas con pedidos HTTP (líder) | CA1 a CA12 OK contra el Sheet de pruebas: renombró «Hoja 1» → «Inventario» con sus columnas, creó Movimientos/Caja/Reservas/Clientes, creó «Stock» con varias pestañas, errores 400/401/403 con los textos de `textos.md`, logo válido/pesado/formato. Se restauraron el nombre de tienda y el logo; la carpeta de cache CK quedó vacía (antes `D:\TCG-inventory\data`, que no existe en esta PC). Pendiente: el `AccessDeniedException` de la carpeta del token se informa como "Revisá tu conexión a internet" |
| 2026-10-15 | Arreglo de pendientes (líder, pedido de la dueña) | 1) `AccessDeniedException` en la carpeta de la sesión → 500 "La app no puede leer tu sesión de Google en esta PC…" (`SesionGoogle.sinPermisoEnCarpeta`, texto `general.error.sesion-ilegible`, contrato y test MockMvc). 2) La v2 ya no usa `D:/TCG-inventory/data` como carpeta por defecto: pasa a `./data` (`GoogleOAuthService`, `ConfiguracionArchivosRepository`); la v1 sigue igual. 3) Cache de Card Kingdom en `C:\TCG-inventory\cache` (pedido de la dueña), guardada con `PUT /precios`; la v1 la toma al reiniciar. `mvn -q test` OK |
| 2026-10-15 | Puerto fijo | `server.port=8765` en `application.properties`: `TcgBotApplication` se levanta con Run del IDE en el puerto que usa el líder (DesktopLauncher elige el suyo) |
| 2026-10-15 | **Aceptada** | La dueña acepta la HU-003. Los flujos CFG siguen `diseñado · HU-001 → HU-004`: pasan a `vigente` cuando la pantalla (HU-004) los deje usables |
