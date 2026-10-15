# Biblioteca de textos de la app

Acá están **todos los textos que ve el usuario**: títulos, etiquetas, ayudas, botones y mensajes de éxito, aviso y error.

**Quién la usa:**
- **UX:** antes de escribir un texto nuevo, busca acá si ya existe uno que sirva y lo reutiliza. Si crea uno nuevo, lo suma.
- **Frontend:** usa estos textos tal cual. Están en `frontend/src/compartido/textos/textos.ts`, con la misma clave.
- **QA:** valida los mensajes contra este texto exacto.

**Un texto existente no se cambia sin avisar al líder:** puede romper pruebas de QA.

## Reglas de redacción
**Tono (pedido de la dueña, HU-005): formal, impersonal y en singular.** La app no le habla al usuario ni habla de sí misma en plural.
- **Prohibido** el imperativo y el voseo ("Ingresá", "Pegá", "Revisá", "Probá", "Compartilo", "Tenés"), el plural de "nosotros" ("la creamos", "No pudimos…") y el "tu / tus" ("Tu tienda", "Tu cuenta").
- Se escribe en tercera persona o con "se": "El nombre de la tienda es obligatorio.", "No se pudo cargar la configuración.", "Se acepta la URL completa o solo el ID.".
- Cuando hay que indicar una acción, va en infinitivo o con "se puede": "Compartirlo con {cuenta} como editor.", "Se puede reintentar más tarde.".
- Corto, con punto final en los mensajes, y en singular.

**Mensajes de error: específicos, no genéricos.** Siguen la práctica actual (GOV.UK, Nielsen Norman Group): dicen **qué está mal en ese campo y cómo va el campo**, sin culpar al usuario. Hay un mensaje por cada causa:
- **vacío** → "{El campo} es obligatorio." (ej.: "El nombre de la tienda es obligatorio.")
- **valor fuera de rango** → "{El campo} debe ser mayor a 0."
- **formato o límite** → cómo debe ser (ej.: "El logo no debe superar los 2 MB.", "El precio debe ser un número entero, sin centavos.", "El logo debe ser PNG, JPG o WEBP.")
- **repetido o inexistente** → qué pasa (ej.: "Ya existe una pestaña con ese nombre.")
- **falla externa** → qué pasó y cómo se resuelve (ej.: "La app no tiene acceso a este Sheet. Compartirlo con {cuenta-servicio} como editor.")
- **Prohibido** el "Revisar X" o el "Valor inválido" sueltos, que no dicen qué está mal.
- El error de un campo tiene que entrar **en una línea del ancho del campo**. En campos angostos (los bulk) se reservan **dos líneas** debajo del campo, para que el mensaje entre entero sin mover nada.
- **Todo campo de texto o número lleva su ejemplo** (`ejemplo`, en gris dentro del campo vacío), con el formato "Ej.: …" (pedido de la dueña, HU-004).
- Los mensajes terminan con punto. Los títulos, botones y etiquetas no llevan punto (excepto el título de falla "No se pudo cargar la configuración.", aprobado así por la dueña).
- Lo que está entre `{}` se reemplaza por un dato (por ejemplo `{pestana}`).
- La **clave** es `<pantalla o general>.<tipo>.<nombre>`. Tipos: `titulo`, `bajada`, `etiqueta`, `ayuda`, `ejemplo` (texto gris dentro de un campo vacío), `boton`, `carga`, `exito`, `aviso`, `error`, `modal`, `texto`.

## Generales (se reutilizan en cualquier pantalla)
| Clave | Texto | Tipo | Desde |
|---|---|---|---|
| `general.boton.reintentar` | Reintentar | botón | HU-001 |
| `general.boton.guardando` | Guardando… | botón (en espera) | HU-001 |
| `general.carga.sincronizando-sheet` | Sincronizando con el Sheet… | capa de carga | HU-001 (tono HU-005) |
| `general.error.mayor-a-cero` | Debe ser mayor a 0. | error de campo (forma corta) | HU-001 (tono HU-005) |
| `general.error.sin-centavos` | El precio debe ser un número entero, sin centavos. | error de campo | HU-001 (tono HU-005) |
| `general.error.corregir-campo` | No se guardó. Hay un campo con error, marcado en rojo. | error de bloque | HU-001 (tono HU-005) |
| `general.error.inesperado` | No se pudo guardar por un error inesperado. Se puede reintentar en un momento. | error de bloque | HU-001 (tono HU-005) |
| `general.error.conexion` | No hay conexión a internet. Se puede reintentar cuando se restablezca. | error | HU-001 (tono HU-005) |
| `general.error.sesion-vencida` | La sesión de Google venció. (+ enlace "Volver a iniciar sesión") | error | HU-001 (tono HU-005) |
| `general.boton.volver-a-iniciar-sesion` | Volver a iniciar sesión | enlace | HU-001 |
| `general.error.sesion-ilegible` | La app no puede leer la sesión de Google en esta PC. Abrirla sin permisos de administrador e iniciar sesión de nuevo. | error (500: Windows no deja leer la carpeta de la sesión) | HU-003 (tono HU-005) |
| `general.modal.sin-guardar-titulo` | Hay cambios sin guardar | modal | HU-001 (tono HU-005) |
| `general.modal.sin-guardar-pregunta` | ¿Salir igual? | modal | HU-001 |
| `general.boton.seguir-editando` | Seguir editando | botón | HU-001 |
| `general.boton.salir-igual` | Salir igual | botón | HU-001 |

## Menú lateral
| Clave | Texto | Desde |
|---|---|---|
| `menu.inventario` | Inventario | HU-001 |
| `menu.reservas` | Reservas | HU-001 |
| `menu.caja` | Caja | HU-001 |
| `menu.movimientos` | Movimientos | HU-001 |
| `menu.configuracion` | Configuración | HU-001 |
| `menu.etiqueta.menu-principal` | Menú principal (etiqueta de accesibilidad del menú, no se ve) | HU-002 |
| `general.texto.nombre-tienda-por-defecto` | Inventory Manager (nombre que se muestra si la tienda todavía no cargó el suyo; es el de la v1) | HU-002 |

## Configuración (HU-001, tono y Sheet rediseñados en HU-005)
| Clave | Texto | Tipo | Flujo | Locator |
|---|---|---|---|---|
| `configuracion.titulo` | Configuración | título | | |
| `configuracion.bajada` | Cada bloque se guarda por separado. Lo escrito en los otros no se pierde. | bajada | | |
| `configuracion.carga` | Cargando la configuración… | capa de carga | CFG-F06 | |
| `configuracion.error.cargar-titulo` | No se pudo cargar la configuración. | título de falla | CFG-F06 | |
| **Tienda** | | | | |
| `configuracion.tienda.titulo` | Tienda | título de bloque | CFG-F01 | |
| `configuracion.tienda.bajada` | El nombre y el logo se ven en toda la app. | bajada | CFG-F01 | |
| `configuracion.tienda.etiqueta.nombre` | Nombre de la tienda | etiqueta | CFG-F01 | |
| `configuracion.tienda.etiqueta.logo` | Logo | etiqueta | CFG-F01 | |
| `configuracion.tienda.ayuda.logo` | PNG, JPG o WEBP, hasta 2 MB. | ayuda (siempre visible) | CFG-F01 | |
| `configuracion.tienda.boton.subir-logo` | Subir logo | botón | CFG-F01 | `configuracion-boton-subir-logo` |
| `configuracion.tienda.boton.quitar-logo` | Quitar | botón | CFG-F01 | `configuracion-boton-quitar-logo` |
| `configuracion.tienda.boton.guardar` | Guardar tienda | botón | CFG-F01 | `configuracion-boton-guardar-tienda` |
| `configuracion.tienda.exito` | Tienda guardada. | éxito | CFG-F01 | `configuracion-mensaje-exito-tienda` |
| `configuracion.tienda.error.nombre-vacio` | El nombre de la tienda es obligatorio. | error de campo | CFG-A01 | `configuracion-mensaje-error-nombre-tienda` |
| `configuracion.tienda.error.logo-pesado` | El logo no debe superar los 2 MB. | error de campo | CFG-A02 | `configuracion-mensaje-error-logo` |
| `configuracion.tienda.error.logo-formato` | El logo debe ser PNG, JPG o WEBP. | error de campo | CFG-A02 | `configuracion-mensaje-error-logo` |
| **Sheet · fila de conectar** | | | | |
| `configuracion.sheet.titulo` | Inventario en Google Sheets | título de bloque | CFG-F02 | |
| `configuracion.sheet.bajada` | El Google Sheet es la base de datos de la tienda. | bajada | CFG-F02 | |
| `configuracion.sheet.texto.conectado` | Conectado como {gmail} | texto | CFG-F02 | `configuracion-texto-gmail-conectado` |
| `configuracion.sheet.etiqueta.link` | ID o URL del Sheet | etiqueta | CFG-F02 | |
| `configuracion.sheet.ayuda.link` | Se acepta la URL completa o solo el ID. | ayuda | CFG-F02 | |
| `configuracion.sheet.etiqueta.pestana` | Pestaña del inventario | etiqueta | CFG-F02 | |
| `configuracion.sheet.ayuda.pestana` | Si no existe, se crea. Si el Sheet no tiene ninguna, se crea la primera. | ayuda (cambia en HU-005: ya no renombra) | CFG-F03, CFG-A07 | |
| `configuracion.sheet.boton.guardar` | Guardar inventario | botón | CFG-F02 | `configuracion-boton-guardar-sheet` |
| `configuracion.sheet.exito` | Inventario guardado. | éxito | CFG-F02 | `configuracion-mensaje-exito-sheet` |
| `configuracion.sheet.aviso.creada` | Se creó la pestaña «{pestana}». | aviso | CFG-A07 | `configuracion-mensaje-aviso-sheet` |
| `configuracion.sheet.traer.etiqueta` | Traer cartas de | etiqueta de la lista (reemplaza a las claves `aviso.copiada*`, quitadas) | CFG-F02 | |
| `configuracion.sheet.traer.ayuda` | Solo si la pestaña del inventario es nueva. La de origen no se toca. | ayuda | CFG-F02 | `configuracion-texto-ayuda-traer-cartas-de` |
| `configuracion.sheet.traer.opcion.ninguna` | No traer cartas | opción de la lista | CFG-F02 | |
| `configuracion.sheet.traer.texto.sugerida` | sugerida | marca junto a la pestaña con encabezados de inventario, dentro de la lista | CFG-F02 | |
| `configuracion.sheet.traer.texto.sin-sheet` | Sin Sheet | texto de la lista (deshabilitada, sin link escrito) | CFG-F02 | |
| `configuracion.sheet.traer.ayuda.sin-sheet` | Disponible cuando se escribe el link del Sheet. | ayuda (reemplaza a la de arriba) | CFG-F02 | `configuracion-texto-ayuda-traer-cartas-de` |
| `configuracion.sheet.traer.ayuda.ninguna-reconocida` | Ninguna pestaña tiene encabezados de inventario. | ayuda informativa (reemplaza a la de arriba; la lista queda en "No traer cartas") | CFG-F02 | `configuracion-texto-ayuda-traer-cartas-de` |
| `configuracion.sheet.traer.error.lista` | No se pudieron cargar las pestañas del Sheet. (+ botón "Reintentar") | error de campo (mismo texto que `renombrar.error.lista`; la carga reutiliza `renombrar.carga.lista`: "Cargando pestañas…") | CFG-F02 | `configuracion-mensaje-error-traer-cartas-de` |
| `configuracion.sheet.traer.error.origen-inexistente` | La pestaña de origen ya no existe en el Sheet. Se actualizó la lista. | error de campo | CFG-F02 | `configuracion-mensaje-error-traer-cartas-de` |
| `configuracion.sheet.traer.carga` | Trayendo las cartas… | capa de carga | CFG-F02 | |
| `configuracion.sheet.traer.exito` | Se creó la pestaña «{pestana}» con {n} cartas de «{origen}». | éxito (con 1 sola: "con 1 carta") | CFG-F02 | `configuracion-mensaje-exito-sheet` |
| `configuracion.sheet.traer.exito-columnas-extra` | Las columnas que la app no usa quedaron al final. | éxito (se suma al anterior, con un espacio, si había columnas desconocidas) | CFG-F02 | `configuracion-mensaje-exito-sheet` |
| `configuracion.sheet.error.vacio` | El ID o la URL del Sheet es obligatorio. | error de campo | CFG-A03 | `configuracion-mensaje-error-link-sheet` |
| `configuracion.sheet.error.invalido` | Debe ser una URL de Google Sheets o solo el ID. | error de campo | CFG-A03 | `configuracion-mensaje-error-link-sheet` |
| `configuracion.sheet.error.sin-permiso` | La app no tiene acceso a este Sheet. Compartirlo con {cuenta-servicio} como editor. ({cuenta-servicio} = `tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com`) | error de bloque | CFG-A04 | `configuracion-mensaje-error-sheet` |
| `configuracion.sheet.error.pestana-vacia` | El nombre de la pestaña es obligatorio. | error de campo | CFG-A07 | `configuracion-mensaje-error-pestana-inventario` |
| **Sheet · fila de renombrar (nuevo en HU-005)** | | | | |
| `configuracion.sheet.renombrar.titulo` | Renombrar una pestaña | subtítulo de la fila | CFG-F03 | |
| `configuracion.sheet.renombrar.ayuda.nombre-nuevo` | Hasta 100 caracteres. | ayuda | CFG-F03 | |
| `configuracion.sheet.renombrar.etiqueta.pestana` | Pestaña a renombrar | etiqueta | CFG-F03 | |
| `configuracion.sheet.renombrar.ayuda.pestana` | Pestañas del Sheet guardado. | ayuda | CFG-F03 | |
| `configuracion.sheet.renombrar.ejemplo.pestana` | Elegir una pestaña | texto gris de la lista sin elegir | CFG-F03 | |
| `configuracion.sheet.renombrar.carga.lista` | Cargando pestañas… | texto de la lista (cargando) | CFG-F03 | |
| `configuracion.sheet.renombrar.texto.sin-sheet` | Sin Sheet guardado | texto de la lista (deshabilitada) | CFG-F03 | |
| `configuracion.sheet.renombrar.ayuda.sin-sheet` | Disponible cuando haya un Sheet guardado. | ayuda (reemplaza a la de arriba) | CFG-F03 | |
| `configuracion.sheet.renombrar.texto.sin-pestanas` | El Sheet no tiene pestañas. | texto de la lista (vacía) | CFG-F03 | |
| `configuracion.sheet.renombrar.texto.en-uso` | en uso | marca junto a la pestaña del inventario, dentro de la lista | CFG-F03 | |
| `configuracion.sheet.renombrar.error.lista` | No se pudieron cargar las pestañas del Sheet. (+ botón "Reintentar") | error de campo | CFG-F03 | `configuracion-mensaje-error-pestana-a-renombrar` |
| `configuracion.sheet.renombrar.etiqueta.nombre-nuevo` | Nombre nuevo | etiqueta | CFG-F03 | |
| `configuracion.sheet.renombrar.ejemplo.nombre-nuevo` | Ej.: Stock 2026 | ejemplo | CFG-F03 | |
| `configuracion.sheet.renombrar.boton` | Renombrar pestaña | botón | CFG-F03 | `configuracion-boton-renombrar-pestana` |
| `configuracion.sheet.renombrar.carga` | Renombrando la pestaña… | capa de carga | CFG-F03 | |
| `configuracion.sheet.renombrar.exito` | La pestaña «{anterior}» se renombró a «{pestana}». | éxito | CFG-F03 | `configuracion-mensaje-exito-renombrar` |
| `configuracion.sheet.aviso.renombrada` | La pestaña «{anterior}» se renombró a «{pestana}». También se actualizó la pestaña del inventario. | éxito, en verde (se renombró la que usa el inventario; texto y uso cambiados en HU-005; verde por decisión de la dueña) | CFG-F03 | `configuracion-mensaje-exito-renombrar` |
| `configuracion.sheet.renombrar.error.sin-pestana` | La pestaña a renombrar es obligatoria. | error de campo | CFG-F03 | `configuracion-mensaje-error-pestana-a-renombrar` |
| `configuracion.sheet.renombrar.error.nombre-vacio` | El nombre nuevo es obligatorio. | error de campo | CFG-F03 | `configuracion-mensaje-error-nombre-nuevo` |
| `configuracion.sheet.renombrar.error.nombre-largo` | El nombre no debe superar los 100 caracteres. | error de campo | CFG-F03 | `configuracion-mensaje-error-nombre-nuevo` |
| `configuracion.sheet.renombrar.error.nombre-igual` | El nombre nuevo es igual al actual. | error de campo | CFG-F03 | `configuracion-mensaje-error-nombre-nuevo` |
| `configuracion.sheet.renombrar.error.nombre-repetido` | Ya existe una pestaña con ese nombre. | error de campo | CFG-F03 | `configuracion-mensaje-error-nombre-nuevo` |
| `configuracion.sheet.renombrar.error.pestana-inexistente` | La pestaña ya no existe en el Sheet. Se actualizó la lista. | error de bloque | CFG-F03 | `configuracion-mensaje-error-renombrar` |
| (sin permiso, sesión vencida, inesperado en la fila de renombrar) | → `configuracion.sheet.error.sin-permiso`, `general.error.sesion-vencida`, `general.error.inesperado` | error de bloque | CFG-F03 | `configuracion-mensaje-error-renombrar` |
| **Precios** | | | | |
| `configuracion.precios.titulo` | Regla de precio | título de bloque | CFG-F04 | |
| `configuracion.precios.bajada` | Cómo se pasa el precio de Card Kingdom (USD) a pesos. | bajada | CFG-F04 | |
| `configuracion.precios.etiqueta.cotizacion` | Cotización (ARS por USD) | etiqueta | CFG-F04 | |
| `configuracion.precios.etiqueta.redondeo` | Redondear al múltiplo de ($) | etiqueta | CFG-F04 | |
| `configuracion.precios.etiqueta.cache` | Carpeta de cache CK (opcional) | etiqueta | CFG-F04 | |
| `configuracion.precios.ejemplo.cache` | Ej.: C:\TCG-inventory\cache | ejemplo dentro del campo (placeholder) | CFG-F04 | |
| `configuracion.precios.ejemplo.cotizacion` | Ej.: 1350 | ejemplo | CFG-F04 | |
| `configuracion.precios.ejemplo.redondeo` | Ej.: 500 | ejemplo | CFG-F04 | |
| `configuracion.precios.ejemplo.bulk` | Ej.: 50 | ejemplo (los 5 bulk) | CFG-F04 | |
| `configuracion.tienda.ejemplo.nombre` | Ej.: Mazo Mágico | ejemplo | CFG-F01 | |
| `configuracion.sheet.ejemplo.link` | Ej.: 1vSk7YvkaEKOsomY7hnRG… o la URL | ejemplo | CFG-F02 | |
| `configuracion.sheet.ejemplo.pestana` | Ej.: Inventario | ejemplo | CFG-F02 | |
| `configuracion.puntos.ejemplo.pesos` | Ej.: 1000 | ejemplo | CFG-F05 | |
| `configuracion.precios.titulo.bulk` | Precio bulk por rareza ($, sin centavos) | subtítulo | CFG-F04 | |
| `configuracion.precios.etiqueta.common` · `uncommon` · `rare` · `mythic` · `tierra-basica` | Common · Uncommon · Rare · Mythic · Tierra básica | etiquetas | CFG-F04 | |
| `configuracion.precios.boton.guardar` | Guardar precios | botón | CFG-F04 | `configuracion-boton-guardar-precios` |
| `configuracion.precios.exito` | Precios guardados. Se recalculan. | éxito | CFG-F04 | `configuracion-mensaje-exito-precios` |
| `configuracion.precios.error.cotizacion-vacia` | La cotización del dólar es obligatoria. | error de campo | CFG-A06 | `configuracion-mensaje-error-cotizacion` |
| `configuracion.precios.error.cotizacion-menor` | La cotización debe ser mayor a 0. | error de campo | CFG-A06 | `configuracion-mensaje-error-cotizacion` |
| `configuracion.precios.error.redondeo-vacio` | El múltiplo para redondear es obligatorio. | error de campo | CFG-A06 | `configuracion-mensaje-error-redondeo` |
| `configuracion.precios.error.redondeo-menor` | El múltiplo debe ser mayor a 0. | error de campo | CFG-A06 | `configuracion-mensaje-error-redondeo` |
| `configuracion.precios.error.bulk-vacio` | El precio es obligatorio. | error de campo (corto) | CFG-A06 | `configuracion-mensaje-error-bulk-<rareza>` |
| `configuracion.precios.error.bulk-menor` | → `general.error.mayor-a-cero` | error de campo (corto) | CFG-A06 | `configuracion-mensaje-error-bulk-<rareza>` |
| `configuracion.precios.error.bulk-centavos` | → `general.error.sin-centavos` | error de campo (corto) | CFG-A06 | `configuracion-mensaje-error-bulk-<rareza>` |
| `configuracion.precios.error.carpeta` | La carpeta no existe. Se puede indicar otra o dejar el campo vacío. | error de campo | CFG-A08 | `configuracion-mensaje-error-carpeta-cache` |
| `configuracion.precios.aviso.falla-ck` | Guardado, pero no se pudo sincronizar con Card Kingdom. Se puede reintentar más tarde. | aviso | CFG-A09 | `configuracion-mensaje-aviso-precios` |
| **Puntos** | | | | |
| `configuracion.puntos.titulo` | Puntos de clientes | título de bloque | CFG-F05 | |
| `configuracion.puntos.bajada` | Cuánto debe gastar un cliente para sumar un punto. | bajada | CFG-F05 | |
| `configuracion.puntos.etiqueta.pesos` | Pesos por punto ($) | etiqueta | CFG-F05 | |
| `configuracion.puntos.texto.ejemplo` | Una compra de $25.000 suma {puntos} puntos. | texto | CFG-F05 | `configuracion-texto-ejemplo-puntos` |
| `configuracion.puntos.texto.ejemplo-vacio` | Falta indicar cuántos pesos vale un punto. | texto | CFG-F05 | `configuracion-texto-ejemplo-puntos` |
| `configuracion.puntos.boton.guardar` | Guardar puntos | botón | CFG-F05 | `configuracion-boton-guardar-puntos` |
| `configuracion.puntos.error.vacio` | Los pesos por punto son obligatorios. | error de campo | CFG-A06 | `configuracion-mensaje-error-pesos-por-punto` |
| `configuracion.puntos.error.menor` | → `general.error.mayor-a-cero` | error de campo | CFG-A06 | `configuracion-mensaje-error-pesos-por-punto` |
| `configuracion.puntos.exito` | Puntos guardados. | éxito | CFG-F05 | `configuracion-mensaje-exito-puntos` |

## Cambios de HU-005 (para frontend y QA)
- **Solo cambió el texto** (misma clave) en todos los textos que tenían voseo, imperativo o plural: ver la columna "Desde" de los generales y los de Configuración.
- **Título de bloque `configuracion.tienda.titulo`:** "Tu tienda" pasa a "Tienda".
- **Clave `configuracion.sheet.aviso.renombrada`:** deja de ser el resultado de "Guardar inventario" (ese botón ya no renombra) y pasa a la fila de renombrar, solo cuando la pestaña renombrada es la del inventario. Su locator cambia de `configuracion-mensaje-aviso-sheet` a `configuracion-mensaje-exito-renombrar` (se muestra en verde, como éxito).
- **Claves nuevas:** todas las `configuracion.sheet.renombrar.*`.
