# HU-004: Configuración v2 · frontend

- **Estado:** aceptada
- **Épica:** [EP-001](../epicas/EP-001-configuracion-v2.md)
- **Tipo:** frontend
- **Módulo:** configuracion
- **Rama:** `v2/feature/HU-004-configuracion-frontend`
- **Funcional documentado:** reusa los de HU-001
- **Flujos funcionales:** CFG-F01 a CFG-F06, CFG-A01 a CFG-A09

## Historia
**Como** dueño del comercio
**quiero** la pantalla nueva de Configuración, con cada bloque que se guarda por separado y sin recargar
**para** cambiar los datos de la tienda, el Sheet, los precios y los puntos, y ver enseguida si salió bien o qué corregir

## Descripción
La pantalla se programa en `frontend/src/modulos/configuracion/`, dentro del `MarcoApp` (HU-002), y se ve en `/app`. Se implementa **exactamente** lo que dicen:
- **Diseño:** `docs/v2/ux/configuracion/spec.md` y `prototipo.html` (estados, interacción, foco y teclado), y `docs/v2/ux/sistema-diseno.md`.
- **API:** `docs/v2/api/configuracion.md` (HU-003), consumida desde un único `configuracionApi.ts`. `aviso`, `gmail` y `logoUrl` vienen siempre (en `null` si no hay); el logo se ve con `GET /api/v1/configuracion/tienda/logo`.
- **Textos:** `docs/v2/textos.md`, tal cual (con la misma clave en `frontend/src/compartido/textos/textos.ts`).
- **Locators:** `docs/v2/locators.md` (pantalla `configuracion`, Desde = HU-001).

Casos de Google: 401 → "Tu sesión de Google venció." con el enlace "Volver a iniciar sesión" a `urlInicioSesion` (hoy, el login de la v1); 403 → mensaje del Sheet sin permiso; 500 "Sesión de Google ilegible" y 503 → su mensaje como error del bloque (nunca un genérico).

**Textos cambiados al armar la HU (pedido de la dueña: que el error diga cómo va el campo, corto):** "Usá un entero, sin centavos.", "Usá un logo de hasta 2 MB.", "Usá un PNG, JPG o WEBP.", "Pegá una URL de Google Sheets o solo el ID." y "Esa carpeta no existe. Elegí otra o dejala vacía.". Ya están en la biblioteca, el backend, sus tests, el contrato, el prototipo y el documento funcional. Regla para textos nuevos en `textos.md`.

## Criterios de aceptación
**CA1: Ver la configuración (CFG-F06)**
- **Given** la tienda tiene la configuración guardada
- **When** abre el módulo de "Configuración"
- **Then** visualiza "Cargando la configuración…"
- **And** después visualiza los bloques "Tu tienda", "Inventario en Google Sheets", "Regla de precio" y "Puntos de clientes" con los valores guardados
- **And** visualiza "Conectado como tcgstorehelper@gmail.com"

**CA2: Falla al cargar (CFG-F06)**
- **Given** la API no responde
- **When** abre el módulo de "Configuración"
- **Then** visualiza "No pudimos cargar la configuración" con el botón "Reintentar"

**CA3: Guardar la tienda (CFG-F01, CFG-A01, CFG-A02)**
- **Given** visualiza el módulo de "Configuración"
- **When** escribe "Codex Live Store" en "Nombre de la tienda" y hace clic en "Guardar tienda"
- **Then** el botón muestra "Guardando…" hasta que responde
- **And** visualiza el mensaje "Tienda guardada."
- **And** con el nombre vacío visualiza "Ingresá el nombre de la tienda." debajo del campo, con el foco en el campo
- **And** con un logo de más de 2 MB o que no es PNG, JPG ni WEBP visualiza el error del logo y el logo no cambia

**CA4: Guardar el Sheet con aviso de pestaña (CFG-F02, CFG-F03, CFG-A07)**
- **Given** el Sheet tiene una sola pestaña "Hoja 1"
- **When** pega el link del Sheet, escribe "Inventario" en "Pestaña del inventario" y hace clic en "Guardar inventario"
- **Then** visualiza el aviso "La pestaña «Hoja 1» se renombró a «Inventario»."
- **And** si el Sheet tiene varias pestañas y falta la pedida, visualiza "Se creó la pestaña «Inventario»."

**CA5: Errores del Sheet (CFG-A03, CFG-A04, CFG-A05)**
- **Given** visualiza el módulo de "Configuración"
- **When** guarda el bloque Inventario con un link inválido, un Sheet sin compartir o la sesión de Google vencida
- **Then** visualiza el mensaje que corresponde: el error del campo, el de compartir con `tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com` o "Tu sesión de Google venció." con "Volver a iniciar sesión"
- **And** lo escrito en los otros bloques no se pierde

**CA6: Guardar precios y puntos (CFG-F04, CFG-F05, CFG-A06, CFG-A08, CFG-A09)**
- **Given** visualiza el módulo de "Configuración"
- **When** cambia la cotización y hace clic en "Guardar precios"
- **Then** visualiza "Precios guardados. Se recalculan."
- **And** con un bulk con centavos visualiza "Usá un entero, sin centavos." debajo de ese campo
- **And** si falla Card Kingdom visualiza el aviso "Guardado, pero no se pudo sincronizar con Card Kingdom. Probá más tarde."
- **And** al guardar 500 en "Pesos por punto" visualiza "Puntos guardados."

**CA7: Salir con cambios sin guardar**
- **Given** cambió un campo de un bloque sin guardarlo
- **When** intenta ir a otra pantalla
- **Then** visualiza "Tenés cambios sin guardar" con "Seguir editando" y "Salir igual"
- **And** con "Seguir editando" (o Esc) se queda en la pantalla, con lo escrito intacto

## Locators (`data-testid`)
Los de la pantalla `configuracion` en `docs/v2/locators.md` (Desde = HU-001), sin agregar ni cambiar ninguno. Si falta alguno, se suma al catálogo antes de usarlo.

## Fuera de alcance
- La API (HU-003, ya hecha) y el QA E2E (HU-005).
- La pantalla de login de la v2 (y sacar el cartel "Google conectado", que queda en el backlog).
- Las demás pantallas del menú y el tutorial.

## Tareas (en orden)
Cada tarea, un agente nuevo (regla de HU-003: tareas acotadas).

| # | Agente | Tarea | Listo cuando |
|---|---|---|---|
| 1 | frontend | `configuracionApi.ts` (todo el contrato), la pantalla con carga y falla al cargar, y los bloques Tienda (con logo) e Inventario, con sus estados y errores (CA1 a CA5) | Build, Vitest y locators de esos bloques |
| 2 | frontend | Bloques Regla de precio y Puntos, y el modal de salir con cambios sin guardar (CA6, CA7), sobre lo que entregó la tarea 1 | Build, Vitest y todos los locators de la pantalla |

## Consumo
| Agente | Llamada | Tokens |
|---|---|---|
| frontend | 1 (tarea 1, 62 herramientas, ~11 min) | 170.696 |
| frontend | 2 (tarea 2, agente nuevo, 58 herramientas, ~7 min) | 134.105 |
| **Total** | | **304.801** |

## Registro
| Fecha | Paso | Resultado |
|---|---|---|
| 2026-10-15 | Brief aprobado | Historia armada desde EP-001; reusa los flujos CFG. Texto `general.error.sin-centavos` cambiado a "Usá un entero, sin centavos." (pedido de la dueña) |
| 2026-10-15 | Inicio de desarrollo | Rama `v2/feature/HU-004-configuracion-frontend` creada desde `v2/dev`; tarea 1 a `frontend` |
| 2026-10-15 | Tarea 1 entregada | `configuracionApi.ts`, `ConfiguracionPagina`, `BloqueTienda`, `BloqueSheet` y piezas en `compartido/` (Tarjeta, Campo, Boton, MensajeBloque, CapaCarga, `useGuardarBloque`, `clienteApi`, tipos generados de OpenAPI). `mvn -q test` OK (Vitest 21). El logo se sube al apretar "Guardar tienda" |
| 2026-10-15 | Locator duplicado (líder) | `configuracion-mensaje-error-sheet` servía para el bloque y para el campo: el del campo pasa a `configuracion-mensaje-error-link-sheet` (`locators.md`, `textos.md`). Lo ajusta la tarea 2 |
| 2026-10-15 | Tarea 2 entregada | `BloquePrecios`, `BloquePuntos`, `AvisoSalirSinGuardar` (compartido); locator del link ajustado. `mvn -q test` OK (Vitest 31). Dudas: placeholder de la carpeta y título "Ejemplo" de Puntos no están en `textos.md`; el redondeo con decimales lo rechaza solo la API |
| 2026-10-15 | Revisión del líder en Chrome | Modo prueba: pantalla igual al prototipo, error de campo y modal de salir OK. Con la app real: datos reales, Gmail, `/app` y error de bulk OK |
| 2026-10-15 | Ajustes (líder, con OK de la dueña) | `/app` abre Configuración; ejemplo "Ej.: C:\TCG-inventory\cache" en la carpeta de cache (clave nueva `configuracion.precios.ejemplo.cache`, tipo `ejemplo`); el error de bulk se cortaba ("Usá un entero, si…"): los bulk reservan 2 líneas de error (`Campo` → `lineasError`). `mvn -q test` OK. App real levantada para la dueña |
| 2026-10-15 | Pedidos de la dueña al probar (líder) | 1) Campo del Sheet: "ID o URL del Sheet", ayuda "Pegá la URL o solo el ID." y errores con "URL". 2) Ejemplo (placeholder "Ej.: …") en todos los campos; regla nueva en `textos.md`. 3) **Regla de la pestaña:** si la pedida no existe, se renombra la del inventario guardada antes en ese Sheet (antes se creaba una vacía y se perdía de vista el inventario); si no, la única; si no, se crea. Back (`ConfiguracionService.pestanaARenombrar` + 2 tests), contrato, documento funcional (CFG-F03, CFG-A07) y ayuda del campo. `mvn -q test` OK; probado en el Sheet de pruebas («Inventario TEST» → «Inventario Renombre» → «Inventario TEST») |
| 2026-10-15 | Letra de los errores (líder) | Errores de campo 11 px, peso normal, rojo al 80 %; mensaje de bloque 12,5 px sin negrita (`sistema-diseno.md`) |
| 2026-10-15 | **Aceptada** | La dueña la acepta como está. Sus pedidos nuevos (tono impersonal de todos los textos y bloque del Sheet con "crear" y "renombrar pestaña" por separado) van en HU-005 (diseño), HU-006 (backend) y HU-007 (frontend); el QA pasa a HU-008. Flujos CFG → `vigente` (documento funcional 0.4.0) |
