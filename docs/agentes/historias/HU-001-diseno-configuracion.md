# HU-001: Diseño nuevo de Configuración (v2)

- **Estado:** aceptada
- **Épica:** [EP-001](../epicas/EP-001-configuracion-v2.md)
- **Tipo:** funcionalidad
- **Módulo:** configuracion
- **Rama:** `v2/feature/HU-001-diseno-configuracion`
- **QA elegido:** sin QA automático (solo diseño, no hay código); revisión de la persona a cargo sobre el prototipo
- **Funcional documentado:** ambos
- **Flujos funcionales:** CFG-F01 a CFG-F06 (felices); CFG-A01 a CFG-A09 (alternativos)

## Historia
**Como** dueña del comercio
**quiero** ver y aprobar el diseño nuevo de la pantalla de Configuración antes de que se programe
**para** que la v2 mejore lo que hay (más clara, sin recargar la página) sin gastar desarrollo en algo que después haya que cambiar

## Descripción
Es la primera historia de la v2 y sale partida en tres: **HU-001** (este diseño), **HU-002** (base técnica: React en Maven, OpenAPI, arnés E2E) y **HU-003** (Configuración v2 funcionando).
La pantalla actual es `templates/legacy/store.html` (`GET /configuracion`) y tiene cinco bloques: identidad de la tienda (nombre, logo), inventario (pestaña y Sheet), regla de precio (cotización, redondeo, bulk C/U/R/M/L, carpeta de cache CK), lógica de puntos y un único botón "Guardar configuracion" que recarga toda la página.
**Bug conocido** (se arregla en HU-003, no en la v1): si el Sheet tiene una sola pestaña y no existe la que se pidió (por ejemplo "Inventario"), la app adopta esa pestaña y pisa el nombre guardado en vez de renombrarla (`legacy/service/GoogleSheetsService.java:1459-1462`). El diseño tiene que contemplar el mensaje "La pestaña X se renombró a Inventario".
UX entrega la spec, el prototipo HTML navegable, la base del sistema de diseño (colores, tipografía, botones, campos, mensajes, gatito sin modificar) y los `data-testid`. El líder valida la propuesta de API que acompaña al diseño; el contrato definitivo se implementa en HU-003.

## Criterios de aceptación
**CA1: Prototipo navegable**
- **Dado** el archivo `docs/v2/ux/configuracion/prototipo.html`
- **Cuando** la dueña lo abre en el navegador
- **Entonces** ve la pantalla de Configuración nueva con todos los bloques actuales (identidad, inventario/Sheet, regla de precio, puntos) y puede pasar entre los estados: cargando, normal, guardando, guardado y error

**CA2: Guardar sin recargar**
- **Dado** el prototipo
- **Cuando** se guarda un bloque
- **Entonces** el diseño muestra el resultado en el lugar (mensaje de éxito o error junto al bloque) sin recargar la pantalla entera

**CA3: Pestaña del inventario renombrada**
- **Dado** que el Sheet tiene una sola pestaña "Hoja 1"
- **Cuando** la dueña escribe "Inventario" como nombre de la pestaña y guarda
- **Entonces** el diseño muestra el aviso "La pestaña «Hoja 1» se renombró a «Inventario»"

**CA4: Errores entendibles**
- **Dado** el prototipo
- **Cuando** se simula un error (link de Sheet inválido, Sheet sin permiso, sesión de Google vencida, número inválido en precios)
- **Entonces** cada error tiene un texto en español llano que dice qué pasó y qué hacer, junto al campo o bloque que corresponde

**CA5: Sistema de diseño y locators**
- **Dado** la spec terminada
- **Cuando** se revisa `docs/v2/ux/sistema-diseno.md` y `docs/v2/locators.md`
- **Entonces** están definidos los tokens base (colores, tipografía, espaciado), los componentes usados en la pantalla, y todos los `data-testid` de Configuración cargados en el catálogo con "Desde (HU)" = HU-001

**CA6: Propuesta de API**
- **Dado** la spec
- **Cuando** el líder la revisa
- **Entonces** incluye qué datos lee y guarda cada bloque, alineado con el contrato propuesto abajo, y la dueña aprueba el diseño

## Locators (`data-testid`)
Prefijo `configuracion-`. Mínimo esperado (UX completa el resto en el catálogo):

| data-testid | Elemento |
|---|---|
| `configuracion-campo-nombre-tienda` | Nombre de la tienda |
| `configuracion-boton-subir-logo` | Subir logo |
| `configuracion-campo-sheet` | Link o ID del Google Sheet |
| `configuracion-campo-pestana-inventario` | Nombre de la pestaña del inventario |
| `configuracion-campo-cotizacion` | Cotización ARS por CK USD |
| `configuracion-campo-redondeo` | Redondear al múltiplo de |
| `configuracion-campo-bulk-<rareza>` | Precio bulk (common, uncommon, rare, mythic, tierra-basica) |
| `configuracion-campo-pesos-por-punto` | Lógica de puntos |
| `configuracion-boton-guardar-<bloque>` | Guardar de cada bloque |
| `configuracion-mensaje-exito` / `configuracion-mensaje-error` | Resultado de guardar |

## Contrato de API (propuesta, se implementa en HU-003)
| Método | Ruta | Request | Respuesta OK | Errores |
|---|---|---|---|---|
| GET | `/api/v1/configuracion` | — | 200 `{ tienda, sheet, precios, puntos, google: { gmailConectado } }` | 401 (sesión Google vencida) |
| PUT | `/api/v1/configuracion/tienda` | `{ nombre }` | 200 `{ tienda }` | 400 |
| POST | `/api/v1/configuracion/tienda/logo` | multipart `archivo` | 200 `{ urlLogo }` | 400, 413 |
| PUT | `/api/v1/configuracion/sheet` | `{ idOLink, nombrePestanaInventario }` | 200 `{ sheet, pestana: { accion: "existente" \| "renombrada" \| "creada", nombreAnterior? }, avisoPrecios? }` | 400, 403 (sin permiso), 401 |
| PUT | `/api/v1/configuracion/precios` | `{ cotizacionUsd, redondeo, bulk: {...}, carpetaCache }` | 200 `{ precios }` | 400 |
| PUT | `/api/v1/configuracion/puntos` | `{ pesosPorPunto }` | 200 `{ puntos }` | 400 |

**Errores:** todos como `ProblemDetail`; los 400 traen `errores: [{ campo, mensaje }]` para marcar el campo que falló. `avisoPrecios` (también en PUT `/precios`) informa que se guardó pero falló la sincronización con Card Kingdom (CFG-A09).
**Locators:** los mensajes van por bloque (`configuracion-mensaje-exito-<bloque>`, `configuracion-mensaje-error-<campo>`); manda el catálogo de `docs/v2/locators.md`, no la tabla mínima de arriba.

## Fuera de alcance
- Código de backend o frontend (va en HU-002 y HU-003).
- Arreglar el bug de la pestaña en la v1 (decisión de la dueña: se arregla en la v2).
- Rediseñar otras pantallas (inventario, reservas, etc.).
- Tutorial y login de Google (otras historias).

## Tareas por agente (en orden)
| # | Agente | Tarea | Listo cuando |
|---|---|---|---|
| 1 | ux-disenador | Spec + prototipo HTML de Configuración, base del sistema de diseño y locators | `docs/v2/ux/configuracion/` con `spec.md` y `prototipo.html`; `sistema-diseno.md` y `locators.md` actualizados |
| 2 | líder | Revisar la propuesta de API contra el contrato de arriba | Contrato ajustado en esta HU |
| 3 | persona a cargo | Abrir el prototipo y aprobar | Aceptada |

## Consumo
| Agente | Llamada | Tokens |
|---|---|---|
| analista-funcional | Flujos CFG | 26.092 |
| ux-disenador | Diseño inicial | 40.717 |
| ux-disenador | 3 decisiones de la dueña | 45.623 |
| ux-disenador | Estilo comiquería, sin gatito en mensajes | 74.582 |
| ux-disenador | Estilo cozy y locators | 100.851 |
| ux-disenador (nuevo) | Barra lateral, azul, rareza, animaciones e íconos | 61.157 |
| **Total** | | **349.022** |

## Registro
| Fecha | Paso | Resultado |
|---|---|---|
| 2026-10-14 | Brief aprobado | Se parte en HU-001 (diseño), HU-002 (base técnica) y HU-003 (Configuración v2). El bug de la pestaña se arregla en HU-003, no en la v1. |
| 2026-10-14 | Funcional | CFG-F01..F06 y CFG-A01..A09 `propuesto · HU-001`. Decisión: con varias pestañas y sin la pedida, se crea la pestaña nueva y se avisa. La falla de sync con Card Kingdom (CFG-A09) es un aviso que no bloquea: la configuración queda guardada. |
| 2026-10-14 | UX | `docs/v2/ux/configuracion/spec.md` + `prototipo.html`, sistema de diseño base y 35 locators. Cuatro bloques que se guardan por separado, sin recargar. |
| 2026-10-14 | Revisión API (líder) | Contrato ajustado: Gmail conectado en el GET, `pestana.accion` (existente/renombrada/creada), `avisoPrecios`, errores 400 con campo. |
| 2026-10-14 | Decisiones de la dueña | Avisar al salir con cambios sin guardar; bulk en pesos enteros; mostrar el Gmail conectado en el bloque Sheet. UX ajusta spec y prototipo. |
| 2026-10-14 | Rediseño visual | Gatito fuera de mensajes y modales (íconos Lucide). Tailwind + shadcn/ui + Lucide + Sonner. Estilo comiquería (Bangers + Nunito). Tamaños fijos: campo 42 px, botón primario 42×160, secundario 42×120. Mensajes cortos y en singular. 40 locators. Reglas de estilo agregadas en `ux-disenador` y `frontend`. |
| 2026-10-14 | Estilo cozy | La dueña descarta el estilo de comiquería. Nuevo estilo moderno, minimalista y cozy: fondo arena, tarjetas blancas de 16 px, acento terracota, Inter. Locators recortados a 27: solo campos, botones y textos a validar (nueva regla en `ux-disenador` y `locators.md`). |
| 2026-10-14 | Marco y detalles | Acento azul #2b5fa6. Barra lateral de 240 px e índice de secciones fijo; formularios de hasta 720 px. Puntos de rareza, microinteracciones e íconos por sección. +9 locators de enlaces. Se usó un agente UX nuevo para no arrastrar el historial (61k contra ~120k estimados). |
| 2026-10-14 | Ajuste (líder) | Se saca el índice de secciones y el contenido queda pegado al menú lateral, sin centrar. Lo hizo el líder sin agente. Quedan como reglas el contenido alineado al menú y un agente nuevo por cada vuelta de cambios (`CLAUDE.md` y `continuar-historia`). Locators: se quitan los 4 `configuracion-enlace-*`. |
| 2026-10-14 | Ajuste (líder) | Grilla de 2×2 (Tienda y Sheet arriba, Precios y Puntos abajo) con tarjetas de igual altura y Guardar al pie. Botones sin corte de línea (se arregla "Subir logo"). Sin toasts al guardar: solo el mensaje del bloque. Regla en el sistema de diseño: el toast nunca duplica un mensaje visible. |
| 2026-10-14 | Ajuste (líder) | Se aprovecha todo el ancho: grilla de 6 columnas (Tienda 3 \| Sheet 3, Precios 4 \| Puntos 2) con los campos en fila para que el Guardar quede cerca. Se revisó en Chrome con `jwebserver` del JDK, porque la extensión no abre `file://`. |
| 2026-10-14 | Ajuste (líder) | Grilla de 2 columnas iguales en las dos filas, con los bordes alineados (sin zigzag). Revisado en Chrome. Reglas nuevas en `CLAUDE.md`: cuándo no usar agente y cómo revisar un prototipo con `jwebserver`. |
| 2026-10-14 | Ajuste (líder) | Carga como capa con blur y tarjeta de 420 px. El mensaje de cada bloque va al lado del Guardar y no mueve nada. Errores de campo en un lugar reservado. El botón ya no cambia de tamaño con "Guardando…". Nueva regla "nada se mueve" en el sistema de diseño y en `ux-disenador`. Revisado en Chrome. |
| 2026-10-14 | Ajuste (líder) | Gatito de carga de 96 a 180 px. Puntos con la misma altura que Precios y Guardar al pie, alineado. Se suma un ejemplo en vivo de puntos (+1 locator: `configuracion-texto-ejemplo-puntos`). Revisado en Chrome. |
| 2026-10-14 | Ajuste (líder) | Regla de espera: lo que tarda usa la capa de carga, lo rápido la ruedita del botón. Guardar el Sheet muestra "Sincronizando con tu Sheet…". Revisado en Chrome. |
| 2026-10-14 | Ajuste (líder) | La ayuda de un campo queda siempre visible y el error va debajo, en un lugar reservado de alto fijo (se corrigió un salto de 6 px). Nueva **biblioteca de textos** `docs/v2/textos.md` con clave, tipo, flujo y locator; regla en `CLAUDE.md`, `ux-disenador`, `frontend` y `qa-*`. Revisado en Chrome. |
| 2026-10-14 | Ajuste (líder) | Errores específicos por campo y por causa (vacío, fuera de rango, formato, falla externa), siguiendo GOV.UK y NN/g. Desaparece "Revisá X". Biblioteca de textos y regla de `ux-disenador` actualizadas. Revisado en Chrome: los errores entran completos en el ancho de su campo. |
| 2026-10-14 | Dato | La dueña informa la cuenta de servicio `tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com`. Se anota en `estado.md` como decisión pendiente para HU-003 (OAuth o cuenta de servicio). El diseño sigue con OAuth; si cambia, se ajustan "Conectado como" y el error de permiso. |
| 2026-10-14 | Cierre | **Aceptada por la dueña.** Flujos CFG en `diseñado · HU-001 → HU-003`; documento funcional en 0.3.0. Se crea la épica EP-001 (HU-001 a HU-003). Merge a `v2/dev` autorizado. |
