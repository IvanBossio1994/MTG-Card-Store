# HU-006: Configuración v2 · backend: pestañas del Sheet y "Traer cartas de"

- **Estado:** aceptada
- **Épica:** EP-001
- **Tipo:** backend
- **Módulo:** configuracion
- **Rama:** `v2/feature/HU-006-configuracion-backend-pestanas`
- **Funcional documentado:** reusa los de HU-005
- **Flujos funcionales:** CFG-F02, CFG-F03, CFG-A03, CFG-A07, CFG-A10 a CFG-A14

## Historia
**Como** dueño del comercio
**quiero** ver las pestañas de mi Sheet, renombrar la que elija y, al crear la pestaña del inventario, traer las cartas de otra pestaña
**para** ordenar mi Sheet desde la app y empezar a usarla sin cargar el stock de nuevo

## Descripción
HU-003 dejó `PUT /api/v1/configuracion/sheet`, que hoy renombra la pestaña única o crea la pedida. HU-005 rediseñó el bloque (`docs/v2/ux/configuracion/spec.md`, 2.1 y 7/7.1):
- **Listar pestañas:** `GET .../sheet/pestanas[?idOLink=]`, con `enUso` y `sugerida`. **Decidido (2026-10-15):** `idOLink` opcional entra, para que "Traer cartas de" funcione con un Sheet escrito y todavía no guardado.
- **Renombrar una pestaña:** `PUT .../sheet/pestanas/renombrar`. Si era la del inventario, se guarda el nombre nuevo en la configuración.
- **`PUT /sheet`:** deja de renombrar (salvo la "Hoja 1" vacía de un Sheet nuevo). Suma `traerCartasDe`: si la pestaña del inventario no existe, la crea con las cartas de la de origen en el formato de la app (columnas por alias, desconocidas al final, de una sola escritura). La de origen no se toca. Responde `cartasTraidas`.
- **Textos del back** en el tono nuevo, tal cual `docs/v2/textos.md`.
- Los alias de columnas viven hoy en la v1 (`GoogleSheetsService` ~2524): se copian a la v2, sin depender de `legacy`.

## Criterios de aceptación
**CA1: Listar las pestañas del Sheet guardado**
- **Given** hay un Sheet guardado con las pestañas "Inventario" y "stock viejo" (esta con encabezados de inventario)
- **When** consulta `GET /api/v1/configuracion/sheet/pestanas`
- **Then** recibe 200 con las dos pestañas en el orden del Sheet
- **And** "Inventario" viene con `enUso: true` y "stock viejo" con `sugerida: true`

**CA2: Listar las pestañas de un Sheet no guardado**
- **Given** escribió el link de otro Sheet, compartido con la cuenta de servicio
- **When** consulta `GET .../sheet/pestanas?idOLink=<link>`
- **Then** recibe 200 con las pestañas de ese Sheet

**CA3: Sin Sheet guardado**
- **Given** no hay Sheet guardado
- **When** consulta `GET .../sheet/pestanas`
- **Then** recibe 200 con `sheetGuardado: false` y la lista vacía

**CA4: Renombrar una pestaña**
- **Given** el Sheet guardado tiene la pestaña "Hoja 1"
- **When** pide renombrarla a "Stock 2026"
- **Then** recibe 200 con `nombreAnterior: "Hoja 1"`, `nombreNuevo: "Stock 2026"` y la lista actualizada
- **And** la pestaña se llama "Stock 2026" en el Sheet

**CA5: Renombrar la pestaña del inventario**
- **Given** la pestaña del inventario es "Inventario"
- **When** pide renombrarla a "Stock 2026"
- **Then** recibe 200 con `eraPestanaInventario: true`
- **And** la configuración guarda "Stock 2026" como pestaña del inventario

**CA6: Errores al renombrar**
- **Given** el Sheet guardado tiene "Inventario" y "Hoja 1"
- **When** pide renombrar sin pestaña, con nombre vacío, de más de 100 caracteres, igual al actual o igual a otra (sin distinguir mayúsculas)
- **Then** recibe 400 con el `campo` y el `codigo` que corresponde (`PESTANA_VACIA`, `NOMBRE_VACIO`, `NOMBRE_LARGO`, `NOMBRE_IGUAL`, `NOMBRE_REPETIDO`) y el texto de `textos.md`
- **And** si la pestaña ya no existe, recibe 404 `PESTANA_INEXISTENTE`; sin Sheet guardado, 400 `SIN_SHEET_GUARDADO`

**CA7: Guardar el inventario trayendo cartas**
- **Given** el Sheet tiene "stock viejo" con 128 cartas y no tiene "Stock 2026"
- **When** guarda el Sheet con la pestaña "Stock 2026" y `traerCartasDe: "stock viejo"`
- **Then** se crea "Stock 2026" con las columnas de la app en su orden y las 128 cartas, y las columnas desconocidas al final
- **And** responde `cartasTraidas: { origen: "stock viejo", cantidad: 128, hayColumnasExtra }`
- **And** "stock viejo" no cambia

**CA8: Guardar el inventario sin renombrar**
- **Given** el Sheet tiene una sola pestaña con datos, que no se llama como la pedida
- **When** guarda el Sheet sin `traerCartasDe`
- **Then** crea la pestaña pedida con los encabezados y responde `pestanaCreada`
- **And** no renombra la existente; solo usa la "Hoja 1" vacía de un Sheet nuevo

**CA9: Pestaña existente u origen borrado**
- **Given** la pestaña del inventario ya existe
- **When** guarda con `traerCartasDe`
- **Then** la usa tal cual e ignora `traerCartasDe`
- **And** si el origen no existe, recibe 400 `campo: traer-cartas-de` / `ORIGEN_INEXISTENTE`

**CA10: Errores de Google**
- **Given** el Sheet no está compartido con la cuenta de servicio o la sesión venció
- **When** lista, renombra o guarda
- **Then** recibe 403 o 401 con los textos de siempre

## Contrato de API — HU de backend
Resumen; el contrato completo va en `docs/v2/api/configuracion.md` (tarea 1).

| Método | Ruta | Request | Respuesta OK | Errores |
|---|---|---|---|---|
| GET | `/api/v1/configuracion/sheet/pestanas[?idOLink=]` | — | 200 `{ sheetGuardado, pestanas: [{ nombre, enUso, sugerida }] }` | 400 link inválido, 401, 403, 500/502 |
| PUT | `/api/v1/configuracion/sheet/pestanas/renombrar` | `{ nombreActual, nombreNuevo }` | 200 `{ nombreAnterior, nombreNuevo, eraPestanaInventario, nombrePestanaInventario, pestanas }` | 400 (campo y código), 404, 401, 403, 500 |
| PUT | `/api/v1/configuracion/sheet` (cambia) | `{ idOLink, nombrePestanaInventario, traerCartasDe? }` | 200 `{ …, pestanaCreada?, cartasTraidas? }`; sin `pestanaRenombradaDesde` | 400 (+ `ORIGEN_INEXISTENTE`), 401, 403 |

## Fuera de alcance
- La pantalla (HU-007) y el QA E2E (HU-008).
- Cambios en la v1.

## Tareas (en orden)
| # | Agente | Tarea | Listo cuando |
|---|---|---|---|
| 1 | `backend-java` | Contrato en `docs/v2/api/configuracion.md` (3 endpoints) y análisis corto de riesgos (v1, Sheet, Google). **Sin programar.** | Contrato escrito; la dueña lo revisa |
| 2 | `backend-java` (agente nuevo) | Ajustar el contrato con D1 y D4; endpoints, alias en la v2, "Traer cartas de", `PUT /sheet` sin renombrar, textos nuevos de todo el back (incluye tienda, precios y puntos), tests unitarios y de API | Compila y pasan los tests |

## Consumo
| Agente | Llamada | Tokens |
|---|---|---|
| `backend-java` | Tarea 1: contrato y riesgos | 78k |
| `backend-java` | Tarea 2: endpoints, textos y tests | 226k |
| **Total** | | 304k |

## Registro
| Fecha | Paso | Resultado |
|---|---|---|
| 2026-10-15 | Brief aprobado | Con `?idOLink` opcional. Se mide el costo de `backend-java` (`docs/consumo-tokens.md`) |
| 2026-10-15 | Tarea 1 (`backend-java`) | Contrato en `docs/v2/api/configuracion.md` (GET pestanas, PUT renombrar, cambios de PUT /sheet, textos en tono HU-005) y "Riesgos (tarea 1)". Decisiones abiertas D1 a D4 para la dueña |
| 2026-10-15 | Tarea 2 (`backend-java`) | Contrato ajustado (D1 `fija`/`PESTANA_FIJA`, D4 compensación, textos de tienda, precios, puntos y `compartido.md`); `GET /sheet/pestanas`, `PUT /sheet/pestanas/renombrar`, `PUT /sheet` con `traerCartasDe` y sin renombrar; alias en `configuracion/ColumnasInventario`; `AccesoSheet` lee valores y crea pestaña con datos en un `batchUpdate`. `mvn test` OK |
| 2026-10-15 | Verificación del líder contra el Sheet de pruebas (app en 8765) | OK: listar, traer cartas (17 de «Inventario TEST» a «Prueba HU-006»), renombrar la del inventario (config actualizada), errores `PESTANA_FIJA`, `NOMBRE_REPETIDO`, 404 y origen inexistente; config vuelta a «Inventario TEST». Corregido por el líder: Movimientos, Caja y Reservas salían `sugerida` → las fijas nunca son sugeridas. `mvn test` OK |
| 2026-10-15 | **Aceptada** | La dueña la acepta. Los flujos siguen `propuesto · HU-005`: pasan a `vigente` con HU-007 (pantalla) |

## Riesgos (tarea 1)
**v1**
- Los alias de columnas están en `legacy/service/GoogleSheetsService` (`SheetColumns.aliases`, ~2892, y `normalizeHeader`, ~2932). Se **copian** a un componente v2 (p. ej. `configuracion/ColumnasInventario`) junto con la normalización (sin tildes, minúsculas, solo letras y números); nada importa de `legacy`. Si después cambian en un lado, hay que cambiarlos en el otro hasta el corte.
- La v1 cubre 11 columnas (A:K) y la v2 escribe las mismas 11 (`ENCABEZADOS_INVENTARIO`); la regla dice Inventario A:L: no hay alias para una 12.ª columna, así que una L del origen pasa como "extra".
- Alias ambiguos de la v1: `Estado` es de `Accion` y `Estado carta` de `Condicion`; `Set` es nombre del set; `N`/`No` son número de carta. Una planilla ajena puede caer mal (por ejemplo "Precio" en USD tomado como Precio Local). Se acepta: es lo que ya hace la v1.
- Renombrar la pestaña del inventario guarda en `store.properties`, que comparte con la v1: la v1 sigue a la pestaña nueva (bien). Renombrar `Caja`, `Movimientos`, `Reservas` o `Clientes` rompe la v1 y la v2 (las buscan por nombre) y en el próximo "Guardar inventario" se crearían vacías de nuevo → **D1**.
- Quitar el renombrado de `PUT /sheet` cambia lo de HU-004: si cambian el nombre de la pestaña sin "Traer cartas de", la app pasa a apuntar a una pestaña **vacía** (los datos siguen en la vieja). El front lo mitiga preseleccionando la sugerida.

**Sheet**
- Tamaño: se lee el origen en una llamada y se escribe en otra (crear + datos juntos, `batchUpdate` con `AddSheet` + `UpdateCells`, todo o nada). Con unas 5.000 filas × 15 columnas es liviano; el límite de Google (10 millones de celdas por Sheet) no se acerca. Hay que crear la pestaña con filas suficientes (Google la crea con 1.000).
- Columnas desconocidas: van al final con su encabezado; duplicadas por alias → la primera manda y la otra es extra. Columnas sin encabezado pero con datos pasan como extra sin título.
- Filas vacías: se saltean; el conteo es de filas, no de la suma de Cantidad. Se copian valores, no fórmulas (una fórmula del origen queda como su resultado) → **D2**.
- Encabezado en otra fila que no sea la 1 → no se reconoce nada y todo pasa como extra (la lista no la marca `sugerida`).
- "Sheet nuevo" se detecta por "una sola pestaña totalmente vacía" (no por el nombre "Hoja 1", que depende del idioma): requiere leer la pestaña antes de decidir.
- Nombres de pestaña con `'` o espacios: los rangos A1 deben ir entre comillas simples escapadas.
- Clave al escribir: no se escribe por número de fila sobre registros existentes; la copia escribe en una pestaña recién creada.

**Google**
- `?idOLink` de un Sheet no guardado: la cuenta de servicio solo lo ve si está compartido → 403 (la lista muestra error + Reintentar; guardar sigue funcionando). Se exige sesión OAuth vigente igual que en `PUT /sheet`, para no listar Sheets ajenos sin Gmail conectado → **D3**.
- Cuotas: listar con `sugerida` necesita la fila 1 de cada pestaña; se hace con **un** `batchGet` (no una llamada por pestaña). El front lista al abrir, al salir del campo del link, después de guardar y de renombrar: con 60 lecturas por minuto por usuario alcanza, pero no hay que listar por cada tecla.
- Renombrar + guardar configuración no es atómico: si Google renombra y falla guardar el archivo local, el Sheet queda renombrado y la configuración apunta al nombre viejo → 500; al reintentar daría 404. Muy improbable (es un archivo local) → **D4**.

**Decisiones resueltas (2026-10-15, la dueña delega: "hacé lo conveniente"; la v1 puede dejar de funcionar, lo que importa es la v2)**
- **D1:** sí, se bloquea: 400 `PESTANA_FIJA` al renombrar `Caja`, `Movimientos`, `Reservas` o `Clientes`. La lista de pestañas las marca (`fija: true`) para que el front las deshabilite en HU-007. Sumar el texto a `textos.md` (`configuracion.sheet.renombrar.error.fija`: "Esa pestaña la usa la app y no se puede renombrar.") y el locator no cambia.
- **D2:** se copian valores, no fórmulas.
- **D3:** listar con `?idOLink` exige sesión de Google vigente.
- **D4:** mejor que aceptarlo: si Google renombra y falla guardar la configuración, se vuelve a poner el nombre anterior en el Sheet (compensación) y se responde 500.
- **Textos de tienda, precios y puntos:** entran en la tarea 2 (todo el back con el tono de `textos.md`).
- **v1:** no se hacen arreglos de convivencia; si algo de la v1 se rompe por la v2, se acepta.

**Decisiones abiertas para la dueña (originales de la tarea 1)**
- **D1:** ¿bloquear el renombrado de `Caja`, `Movimientos`, `Reservas` y `Clientes`? Propuesta: sí, 400 `PESTANA_FIJA` (falta su texto en `textos.md` y que UX la saque de la lista o la deshabilite).
- **D2:** copiar valores (propuesta) y no fórmulas.
- **D3:** listar con `?idOLink` exige sesión de Google vigente (propuesta: sí).
- **D4:** aceptar el caso raro de D4 sin compensación (propuesta: sí).
- Fuera del alcance de esta HU: los textos de error de tienda, precios y puntos también cambiaron de tono en `textos.md` (HU-005) y el código sigue con los viejos. ¿Se suman a la tarea 2?
