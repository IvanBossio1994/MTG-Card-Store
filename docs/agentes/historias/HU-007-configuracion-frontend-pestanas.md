# HU-007: Configuración v2 · frontend: textos nuevos y bloque del Sheet rediseñado

- **Estado:** aceptada
- **Épica:** EP-001
- **Tipo:** frontend
- **Módulo:** configuracion
- **Rama:** `v2/feature/HU-007-configuracion-frontend-pestanas`
- **Funcional documentado:** reusa los de HU-005
- **Flujos funcionales:** CFG-F02, CFG-F03, CFG-A03, CFG-A07, CFG-A10 a CFG-A14

## Historia
**Como** dueño del comercio
**quiero** ver la pantalla de Configuración con los textos nuevos y el bloque del Sheet rediseñado
**para** conectar mi Sheet, traer las cartas de otra pestaña y renombrar pestañas sin salir de la app

## Descripción
HU-004 dejó la pantalla en `frontend/src/modulos/configuracion/` con los textos viejos (`textos.ts`, `tiposApi.ts`) y el bloque del Sheet de antes. HU-005 aprobó el diseño nuevo y HU-006 dejó la API:
- **Diseño:** `docs/v2/ux/configuracion/spec.md` (2.1, 4, 5, 6 y 7.1) y `prototipo.html`. Textos tal cual `docs/v2/textos.md`.
- **API:** `docs/v2/api/configuracion.md`: `GET /sheet/pestanas[?idOLink]` (`enUso`, `sugerida`, `fija`), `PUT /sheet/pestanas/renombrar` y `PUT /sheet` con `traerCartasDe` (responde `pestanaCreada` y `cartasTraidas`; ya no renombra).
- **Textos:** todos los de la pantalla (tienda, Sheet, precios, puntos y comunes) pasan al tono impersonal y en singular.
- **Lista de pestañas:** se carga al abrir (si hay Sheet guardado), al salir del campo del link (si es válido, con `?idOLink`), después de guardar y de renombrar. Nunca por cada tecla.
- Caja, Movimientos, Reservas y Clientes (`fija: true`) aparecen deshabilitadas en "Pestaña a renombrar".

## Criterios de aceptación
**CA1: Textos en el tono nuevo**
- **Given** visualiza el módulo de "Configuración"
- **When** recorre los cuatro bloques y provoca sus errores
- **Then** todos los títulos, ayudas, botones y mensajes coinciden con `docs/v2/textos.md`

**CA2: Guardar el inventario trayendo cartas**
- **Given** el Sheet guardado tiene "stock viejo" con encabezados de inventario y no tiene "Stock 2026"
- **When** escribe "Stock 2026" en "Pestaña del inventario", deja "stock viejo · sugerida" en "Traer cartas de" y hace clic en "Guardar inventario"
- **Then** visualiza la capa de carga "Trayendo las cartas…"
- **And** visualiza el éxito "Se creó la pestaña «Stock 2026» con N cartas de «stock viejo»." en el lugar reservado de la fila

**CA3: Guardar el inventario sin traer cartas**
- **Given** la pestaña escrita no existe en el Sheet
- **When** elige "No traer cartas" y hace clic en "Guardar inventario"
- **Then** visualiza el aviso "Se creó la pestaña «X»."

**CA4: Estados de "Traer cartas de"**
- **Given** visualiza el bloque del Sheet
- **When** el link está vacío, la lista está cargando, falla al listar o ninguna pestaña es reconocida
- **Then** la lista y su ayuda muestran lo que indica la tabla de estados de `spec.md` 2.1
- **And** si falla al listar, visualiza "Reintentar" y "Guardar inventario" sigue funcionando

**CA5: Renombrar una pestaña**
- **Given** el Sheet guardado tiene la pestaña "Hoja 1"
- **When** la elige en "Pestaña a renombrar", escribe "Stock 2026" y hace clic en "Renombrar pestaña"
- **Then** visualiza "La pestaña «Hoja 1» se renombró a «Stock 2026»."
- **And** el nombre nuevo se limpia y la lista se recarga

**CA6: Renombrar la pestaña del inventario**
- **Given** la pestaña del inventario es "Inventario"
- **When** la renombra a "Stock 2026"
- **Then** visualiza el éxito con "También se actualizó la pestaña del inventario."
- **And** "Pestaña del inventario" muestra "Stock 2026" sin quedar como cambio sin guardar

**CA7: Errores de renombrar**
- **Given** visualiza la fila de renombrar
- **When** intenta renombrar sin elegir pestaña, con nombre vacío, de más de 100 caracteres, igual al actual o repetido, o la pestaña fue borrada en el Sheet
- **Then** visualiza el error en el lugar y con el texto de la tabla "Errores de renombrar" de `spec.md` 2.1

**CA8: Pestañas fijas**
- **Given** el Sheet tiene "Caja" y "Movimientos"
- **When** abre la lista "Pestaña a renombrar"
- **Then** esas opciones aparecen deshabilitadas

**CA9: Origen borrado al guardar**
- **Given** eligió una pestaña de origen que alguien borró en el Sheet
- **When** hace clic en "Guardar inventario"
- **Then** visualiza "La pestaña de origen ya no existe en el Sheet. Se actualizó la lista." y la lista se recarga

## Locators (`data-testid`)
Los de HU-004, más los de HU-005 en `docs/v2/locators.md` (filas de `configuracion-selector-pestana-a-renombrar` a `configuracion-boton-reintentar-pestanas-origen`).

## Fuera de alcance
- Cambios de API (HU-006 ya aceptada) y el QA E2E (HU-008).
- Cambios de diseño fuera de lo aprobado en HU-005.
- La v1.

## Tareas (en orden)
| # | Agente | Tarea | Listo cuando |
|---|---|---|---|
| 1 | `frontend` | Textos nuevos en toda la pantalla; `configuracionApi.ts` con los 3 endpoints; bloque del Sheet según `spec.md` 2.1 (dos filas, "Traer cartas de", renombrar, estados y errores) con sus locators; tests Vitest | `mvn -q test` OK (back y Vitest) y locators puestos |

## Consumo
| Agente | Llamada | Tokens |
|---|---|---|
| `frontend` | Tarea 1: textos, API y bloque del Sheet | 232k |
| **Total** | | 232k |

## Registro
| Fecha | Paso | Resultado |
|---|---|---|
| 2026-10-15 | Brief aprobado | Se hace ahora para medir el costo de `frontend` (ventana 39%, semana 63%). Estimación: 15 a 18 puntos de ventana |
| 2026-10-15 | Tarea 1 (`frontend`) | Textos nuevos en toda la pantalla; `configuracionApi.ts` con listar, renombrar y `traerCartasDe`; `tiposApi.ts` regenerado; bloque del Sheet en dos filas con estados y errores; `compartido/ListaDesplegable.tsx` (select nativo). 50 tests Vitest (23 nuevos). `mvn -q test` OK. Abierto: un link inválido al salir del campo no recarga la lista |
| 2026-10-15 | Verificación del líder (app en 8765, Chrome, Sheet de pruebas) | Bloque en dos filas OK; lista "Traer cartas de" con "hoja 1 · sugerida" preseleccionada; errores de renombrar sin pestaña y sin nombre OK. Corregido por el líder: el ícono de "Guardar inventario" se achicaba a un punto (`Boton.tsx`, `shrink-0`). No se guardó nada en el Sheet. `mvn -q test` OK |
| 2026-10-15 | **Aceptada** | La dueña la acepta. Flujos CFG a `vigente` (documento funcional 0.5.0). Se descarta mover "Carpeta de cache CK" a Tienda: se guarda con los precios y queda donde está |
