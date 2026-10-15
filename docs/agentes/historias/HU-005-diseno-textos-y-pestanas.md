# HU-005: Configuración v2 · diseño: tono de los textos y pestañas del Sheet

- **Estado:** aceptada
- **Épica:** [EP-001](../epicas/EP-001-configuracion-v2.md)
- **Tipo:** diseño
- **Módulo:** configuracion (y la biblioteca general de textos)
- **Rama:** `v2/feature/HU-005-diseno-textos-y-pestanas`
- **Funcional documentado:** ambos (felices y alternativos), elegido por la dueña
- **Flujos funcionales:** CFG-F02, CFG-F03 (ahora renombrar pestaña), CFG-A03, CFG-A07 (cambian); nuevos CFG-A10 a CFG-A14

## Historia
**Como** dueño del comercio
**quiero** que los textos de la app sean formales e impersonales, y poder crear o renombrar las pestañas del Sheet por separado
**para** entender cada mensaje sin que la app "me hable" y saber exactamente qué pasa con mis pestañas

## Descripción
Pedidos de la dueña al probar HU-004 (2026-10-15):
1. **Tono de toda la biblioteca** (`docs/v2/textos.md`): impersonal, en singular y sin hablarle al usuario. Nada de "Ingresá…", "Pegá…", "la creamos", "No pudimos…". Los mensajes siguen diciendo cómo va el campo y siguen cortos. Ejemplos aprobados:
   - "Ingresá el nombre de la tienda." → "El nombre de la tienda es obligatorio."
   - "Usá un logo de hasta 2 MB." → "El logo no debe superar los 2 MB."
   - "Usá un entero, sin centavos." → "El precio debe ser un número entero, sin centavos."
   - "No pudimos cargar la configuración" → "No se pudo cargar la configuración."
   - "Pegá la URL o solo el ID." → "Se acepta la URL completa o solo el ID."
   Aplica a errores, avisos, éxitos, ayudas, bajadas, botones y modales. Las claves no cambian (solo el texto), salvo que haga falta una nueva. Las reglas de redacción de `textos.md` se actualizan con el tono nuevo.
2. **Bloque "Inventario en Google Sheets" rediseñado:**
   - Fila 1: "ID o URL del Sheet" + "Pestaña del inventario" (si no existe, se crea; si el Sheet no tiene ninguna, la primera) + "Guardar inventario".
   - Fila 2: "Pestaña a renombrar" (**lista desplegable** con las pestañas del Sheet) + "Nombre nuevo" + su propio botón para renombrar, con su resultado debajo.
   - Hay que definir: estados de la lista (cargando, sin Sheet guardado, error), qué pasa si se renombra la pestaña del inventario (se actualiza la configuración), errores (nombre vacío, nombre repetido) y locators nuevos.
3. Ya aplicado en HU-004 y se mantiene: errores de campo de 11 px, peso normal, rojo al 80 %; ejemplos "Ej.: …" en todos los campos; dos líneas de error en los bulk.

## Criterios de aceptación
**CA1: Textos con el tono nuevo**
- **Given** la biblioteca `docs/v2/textos.md`
- **When** la revisa la dueña
- **Then** ningún texto le habla al usuario ni usa plural de "nosotros"
- **And** cada error dice qué está mal y cómo va el campo, en una frase corta

**CA2: Bloque del Sheet rediseñado**
- **Given** el prototipo de Configuración
- **When** visualiza el bloque "Inventario en Google Sheets"
- **Then** visualiza la fila de conectar el Sheet y la pestaña del inventario, y debajo la fila de renombrar con la lista desplegable de pestañas
- **And** la barra del prototipo muestra los estados nuevos (lista cargando, renombrada, errores)

**CA3: Spec, locators y contrato propuesto**
- **Given** la spec de Configuración
- **When** la lee backend
- **Then** encuentra los datos que necesita para HU-006 (listar pestañas, renombrar una pestaña: request, respuesta y errores)
- **And** los locators nuevos están en `docs/v2/locators.md`

## Fuera de alcance
- Programar el back (HU-006) y el front (HU-007). El QA E2E (HU-008).
- Cambiar los textos en el código: lo hacen HU-006 (back) y HU-007 (front).

## Tareas (en orden)
| # | Agente | Tarea | Listo cuando |
|---|---|---|---|
| 1 | ux-disenador | Reescribir `textos.md` con el tono nuevo; rediseñar el bloque del Sheet en `spec.md` y `prototipo.html`; locators nuevos; propuesta de datos para la API | Spec, prototipo, textos y locators |
| 2 | analista-funcional | Ajustar CFG-F02, CFG-F03, CFG-A03, CFG-A07 y sumar el flujo de renombrar pestaña, según la spec | Flujos `propuesto · HU-005` |

## Consumo
| Agente | Llamada | Tokens |
|---|---|---|
| ux-disenador | 1 (tarea 1, 30 herramientas, ~5 min) | 108.327 |
| analista-funcional | 1 (tarea 2, 13 herramientas, <1 min) | 39.143 |
| ux-disenador | 2 (agente nuevo: "Traer cartas de", 34 herramientas, ~2 min) | 68.960 |
| **Total** | | **216.430** |

## Registro
| Fecha | Paso | Resultado |
|---|---|---|
| 2026-10-15 | Brief aprobado | Tono (ejemplos aprobados) y lista desplegable para renombrar aprobados por la dueña. QA pasa a HU-008 |
| 2026-10-15 | Inicio de desarrollo | Rama `v2/feature/HU-005-diseno-textos-y-pestanas`; tarea 1 a `ux-disenador` |
| 2026-10-15 | Tarea 1 entregada | `textos.md` reescrito (tono nuevo, claves intactas salvo `configuracion.sheet.renombrar.*` nuevas y "Tu tienda" → "Tienda"), spec 2.1 y 7.1, prototipo y 9 locators. Layout: Tienda + Puntos arriba, Sheet a lo ancho, Precios abajo. API propuesta: `GET /configuracion/sheet/pestanas` y `PUT /configuracion/sheet/pestanas/renombrar`; `PUT /sheet` deja de renombrar |
| 2026-10-15 | Revisión del líder en Chrome | Parcial: se vio la parte de arriba (bien alineada, textos nuevos). Chrome dejó de responder al ver los estados de renombrar; no se reintentó. Se abrió el prototipo a la dueña |
| 2026-10-15 | Decisiones de la dueña sobre el prototipo (líder) | Layout y resto OK. 1) Renombrar la pestaña del inventario es éxito en verde, no aviso (`configuracion-mensaje-aviso-renombrar` se quita). 2) **Pestaña del inventario nueva:** si la actual tiene cartas, se crea la nueva y se copian las cartas **ordenadas en el formato de la app** (columnas por alias; las desconocidas al final); la anterior no se toca; textos `configuracion.sheet.aviso.copiada` y `-columnas-extra`. 3) Sheet nuevo: se usa la "Hoja 1" vacía con el nombre pedido. Pendiente en el prototipo: estado "Pestaña copiada" (lo suma HU-007 al programar, o el líder si hace falta mostrarlo) |
| 2026-10-15 | Tarea 2 entregada | Flujos `propuesto · HU-005`: CFG-F02 (crea con copia en formato de la app), CFG-F03 (renombrar con lista; reemplaza al anterior), CFG-A03, CFG-A07, y nuevos CFG-A10 a A14. El líder pasó al tono nuevo los mensajes de CFG-A01, A02, A06 y A08 |
| 2026-10-15 | Bug del ID (líder) | `1vSk7…/edit?gid=0#gid=0` (ID con el final de la URL) daba "inválido": ahora se toma el ID (`ConfiguracionService.ID_DE_SHEET` + test). Falta reiniciar la app para verlo |
| 2026-10-15 | **Decisión: "Traer cartas de"** (aprobada por la dueña) | Caso real: un Sheet propio y desordenado (pestaña "stock viejo" con columnas en otro orden). Con la regla de HU-004 la app solo la renombró a "Inventario", sin acomodar columnas. Nuevo diseño: en la fila de arriba, lista "Traer cartas de" (pestañas del Sheet; la app sugiere la que tiene encabezados de inventario; opción "No traer cartas"). Al guardar crea la pestaña del inventario en el formato de la app con esas cartas (columnas por alias, desconocidas al final) y no toca la de origen. Reemplaza a la "copia de la actual". **Pendiente (después del reinicio de tokens de la dueña):** `ux-disenador` lo dibuja en spec, textos y prototipo, y `analista-funcional` ajusta CFG-F02 |
| 2026-10-15 | "Traer cartas de" dibujada (ux-disenador, agente nuevo) | Spec 2.1 y 7/7.1, prototipo (9 estados nuevos en la barra), 12 claves `configuracion.sheet.traer.*` (quitadas `aviso.copiada*`), 4 locators. API: `PUT /sheet` suma `traerCartasDe` y responde `cartasTraidas`; `GET /sheet/pestanas` suma `?idOLink` opcional (a confirmar en HU-006) y `sugerida` |
| 2026-10-15 | CFG-F02 ajustado (líder, sin agente, por la regla de tokens) | Pasos, resultado, locators, endpoints y alternativos de "Traer cartas de" |
| 2026-10-15 | Revisión del líder en Chrome | Fila de arriba alineada (4 columnas), "Cartas traídas" en verde y "Error al listar" con Reintentar, bien. Prototipo abierto a la dueña |
| 2026-10-15 | **Aceptada por la dueña** | Costo medido de la vuelta "Traer cartas de": 7 puntos de ventana y 1 de semana. Flujos quedan `propuesto · HU-005` hasta HU-007; la versión del funcional no cambia |
