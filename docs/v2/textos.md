# Biblioteca de textos de la app

Acá están **todos los textos que ve el usuario**: títulos, etiquetas, ayudas, botones y mensajes de éxito, aviso y error.

**Quién la usa:**
- **UX:** antes de escribir un texto nuevo, busca acá si ya existe uno que sirva y lo reutiliza. Si crea uno nuevo, lo suma.
- **Frontend:** usa estos textos tal cual. Están en `frontend/src/compartido/textos/textos.ts`, con la misma clave.
- **QA:** valida los mensajes contra este texto exacto.

**Un texto existente no se cambia sin avisar al líder:** puede romper pruebas de QA.

## Reglas de redacción
- Español rioplatense ("Ingresá", "Pegá"), corto y en singular.
- **Mensajes de error: específicos, no genéricos.** Siguen la práctica actual (GOV.UK, Nielsen Norman Group): dicen **qué pasó en ese campo y qué hacer**, sin culpar al usuario. Hay un mensaje por cada causa:
  - **vacío** → "Ingresá {qué}." (ej.: "Ingresá la cotización del dólar.")
  - **valor fuera de rango** → "{Campo} tiene que ser mayor a 0."
  - **formato** → qué formato se espera (ej.: "Sin centavos.", "Ese link no es de un Google Sheet.")
  - **falla externa** → qué pasó y cómo se resuelve (ej.: "Tu cuenta no tiene acceso a este Sheet. Compartilo con {gmail} como editor.")
  - **Prohibido** el "Revisá X" o el "Valor inválido" sueltos, que no dicen qué está mal.
- El error de un campo tiene que entrar **en una línea del ancho del campo**. En campos angostos (los bulk, por ejemplo) se usa la forma corta ("Sin centavos.").
- Los mensajes terminan con punto. Los títulos, botones y etiquetas no llevan punto.
- Lo que está entre `{}` se reemplaza por un dato (por ejemplo `{pestana}`).
- La **clave** es `<pantalla o general>.<tipo>.<nombre>`. Tipos: `titulo`, `bajada`, `etiqueta`, `ayuda`, `boton`, `carga`, `exito`, `aviso`, `error`, `modal`, `texto`.

## Generales (se reutilizan en cualquier pantalla)
| Clave | Texto | Tipo | Desde |
|---|---|---|---|
| `general.boton.reintentar` | Reintentar | botón | HU-001 |
| `general.boton.guardando` | Guardando… | botón (en espera) | HU-001 |
| `general.carga.sincronizando-sheet` | Sincronizando con tu Sheet… | capa de carga | HU-001 |
| `general.error.mayor-a-cero` | Tiene que ser mayor a 0. | error de campo (forma corta) | HU-001 |
| `general.error.sin-centavos` | Sin centavos. | error de campo (forma corta) | HU-001 |
| `general.error.corregir-campo` | No se guardó. Corregí el campo marcado en rojo. | error de bloque | HU-001 |
| `general.error.inesperado` | No pudimos guardar por un error inesperado. Probá de nuevo en un momento. | error de bloque | HU-001 |
| `general.error.conexion` | Revisá tu conexión a internet e intentá de nuevo. | error | HU-001 |
| `general.error.sesion-vencida` | Tu sesión de Google venció. (+ enlace "Volver a iniciar sesión") | error | HU-001 |
| `general.boton.volver-a-iniciar-sesion` | Volver a iniciar sesión | enlace | HU-001 |
| `general.modal.sin-guardar-titulo` | Tenés cambios sin guardar | modal | HU-001 |
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

## Configuración (HU-001)
| Clave | Texto | Tipo | Flujo | Locator |
|---|---|---|---|---|
| `configuracion.titulo` | Configuración | título | | |
| `configuracion.bajada` | Cada bloque se guarda por separado. Lo que escribas en los otros no se pierde. | bajada | | |
| `configuracion.carga` | Cargando la configuración… | capa de carga | CFG-F06 | |
| `configuracion.error.cargar-titulo` | No pudimos cargar la configuración | título de falla | CFG-F06 | |
| **Tienda** | | | | |
| `configuracion.tienda.titulo` | Tu tienda | título de bloque | CFG-F01 | |
| `configuracion.tienda.bajada` | El nombre y el logo se ven en toda la app. | bajada | CFG-F01 | |
| `configuracion.tienda.etiqueta.nombre` | Nombre de la tienda | etiqueta | CFG-F01 | |
| `configuracion.tienda.etiqueta.logo` | Logo | etiqueta | CFG-F01 | |
| `configuracion.tienda.ayuda.logo` | PNG, JPG o WEBP, hasta 2 MB. | ayuda (siempre visible) | CFG-F01 | |
| `configuracion.tienda.boton.subir-logo` | Subir logo | botón | CFG-F01 | `configuracion-boton-subir-logo` |
| `configuracion.tienda.boton.quitar-logo` | Quitar | botón | CFG-F01 | `configuracion-boton-quitar-logo` |
| `configuracion.tienda.boton.guardar` | Guardar tienda | botón | CFG-F01 | `configuracion-boton-guardar-tienda` |
| `configuracion.tienda.exito` | Tienda guardada. | éxito | CFG-F01 | `configuracion-mensaje-exito-tienda` |
| `configuracion.tienda.error.nombre-vacio` | Ingresá el nombre de la tienda. | error de campo | CFG-A01 | `configuracion-mensaje-error-nombre-tienda` |
| `configuracion.tienda.error.logo-pesado` | El logo pesa más de 2 MB. | error de campo | CFG-A02 | `configuracion-mensaje-error-logo` |
| `configuracion.tienda.error.logo-formato` | El archivo no es PNG, JPG ni WEBP. | error de campo | CFG-A02 | `configuracion-mensaje-error-logo` |
| **Sheet** | | | | |
| `configuracion.sheet.titulo` | Inventario en Google Sheets | título de bloque | CFG-F02 | |
| `configuracion.sheet.bajada` | Tu Google Sheet es la base de datos de la tienda. | bajada | CFG-F02 | |
| `configuracion.sheet.texto.conectado` | Conectado como {gmail} | texto | CFG-F02 | `configuracion-texto-gmail-conectado` |
| `configuracion.sheet.etiqueta.link` | Link o ID del Sheet | etiqueta | CFG-F02 | |
| `configuracion.sheet.ayuda.link` | Pegá el link completo o solo el ID. | ayuda | CFG-F02 | |
| `configuracion.sheet.etiqueta.pestana` | Pestaña del inventario | etiqueta | CFG-F02 | |
| `configuracion.sheet.ayuda.pestana` | Si no existe, la creamos. Con una sola pestaña, la renombramos. | ayuda | CFG-F03 | |
| `configuracion.sheet.boton.guardar` | Guardar inventario | botón | CFG-F02 | `configuracion-boton-guardar-sheet` |
| `configuracion.sheet.exito` | Inventario guardado. | éxito | CFG-F02 | `configuracion-mensaje-exito-sheet` |
| `configuracion.sheet.aviso.renombrada` | La pestaña «{anterior}» se renombró a «{pestana}». | aviso | CFG-F03 | `configuracion-mensaje-aviso-sheet` |
| `configuracion.sheet.aviso.creada` | Se creó la pestaña «{pestana}». | aviso | CFG-A07 | `configuracion-mensaje-aviso-sheet` |
| `configuracion.sheet.error.vacio` | Pegá el link o el ID del Sheet. | error de campo | CFG-A03 | `configuracion-mensaje-error-sheet` |
| `configuracion.sheet.error.invalido` | Ese link no es de un Google Sheet. | error de campo | CFG-A03 | `configuracion-mensaje-error-sheet` |
| `configuracion.sheet.error.sin-permiso` | La app no tiene acceso a este Sheet. Compartilo con {cuenta-servicio} como editor. ({cuenta-servicio} = `tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com`) | error de bloque | CFG-A04 | `configuracion-mensaje-error-sheet` |
| `configuracion.sheet.error.pestana-vacia` | Ingresá el nombre de la pestaña. | error de campo | CFG-A07 | `configuracion-mensaje-error-pestana-inventario` |
| **Precios** | | | | |
| `configuracion.precios.titulo` | Regla de precio | título de bloque | CFG-F04 | |
| `configuracion.precios.bajada` | Cómo se pasa el precio de Card Kingdom (USD) a pesos. | bajada | CFG-F04 | |
| `configuracion.precios.etiqueta.cotizacion` | Cotización (ARS por USD) | etiqueta | CFG-F04 | |
| `configuracion.precios.etiqueta.redondeo` | Redondear al múltiplo de ($) | etiqueta | CFG-F04 | |
| `configuracion.precios.etiqueta.cache` | Carpeta de cache CK (opcional) | etiqueta | CFG-F04 | |
| `configuracion.precios.titulo.bulk` | Precio bulk por rareza ($, sin centavos) | subtítulo | CFG-F04 | |
| `configuracion.precios.etiqueta.common` · `uncommon` · `rare` · `mythic` · `tierra-basica` | Common · Uncommon · Rare · Mythic · Tierra básica | etiquetas | CFG-F04 | |
| `configuracion.precios.boton.guardar` | Guardar precios | botón | CFG-F04 | `configuracion-boton-guardar-precios` |
| `configuracion.precios.exito` | Precios guardados. Se recalculan. | éxito | CFG-F04 | `configuracion-mensaje-exito-precios` |
| `configuracion.precios.error.cotizacion-vacia` | Ingresá la cotización del dólar. | error de campo | CFG-A06 | `configuracion-mensaje-error-cotizacion` |
| `configuracion.precios.error.cotizacion-menor` | La cotización tiene que ser mayor a 0. | error de campo | CFG-A06 | `configuracion-mensaje-error-cotizacion` |
| `configuracion.precios.error.redondeo-vacio` | Ingresá el múltiplo para redondear. | error de campo | CFG-A06 | `configuracion-mensaje-error-redondeo` |
| `configuracion.precios.error.redondeo-menor` | El múltiplo tiene que ser mayor a 0. | error de campo | CFG-A06 | `configuracion-mensaje-error-redondeo` |
| `configuracion.precios.error.bulk-vacio` | Ingresá el precio. | error de campo (corto) | CFG-A06 | `configuracion-mensaje-error-bulk-<rareza>` |
| `configuracion.precios.error.bulk-menor` | → `general.error.mayor-a-cero` | error de campo (corto) | CFG-A06 | `configuracion-mensaje-error-bulk-<rareza>` |
| `configuracion.precios.error.bulk-centavos` | → `general.error.sin-centavos` | error de campo (corto) | CFG-A06 | `configuracion-mensaje-error-bulk-<rareza>` |
| `configuracion.precios.error.carpeta` | No encontramos esa carpeta. | error de campo | CFG-A08 | `configuracion-mensaje-error-carpeta-cache` |
| `configuracion.precios.aviso.falla-ck` | Guardado, pero no se pudo sincronizar con Card Kingdom. Probá más tarde. | aviso | CFG-A09 | `configuracion-mensaje-aviso-precios` |
| **Puntos** | | | | |
| `configuracion.puntos.titulo` | Puntos de clientes | título de bloque | CFG-F05 | |
| `configuracion.puntos.bajada` | Cuánto tiene que gastar un cliente para sumar un punto. | bajada | CFG-F05 | |
| `configuracion.puntos.etiqueta.pesos` | Pesos por punto ($) | etiqueta | CFG-F05 | |
| `configuracion.puntos.texto.ejemplo` | Una compra de $25.000 suma {puntos} puntos. | texto | CFG-F05 | `configuracion-texto-ejemplo-puntos` |
| `configuracion.puntos.texto.ejemplo-vacio` | Escribí cuántos pesos vale un punto. | texto | CFG-F05 | `configuracion-texto-ejemplo-puntos` |
| `configuracion.puntos.boton.guardar` | Guardar puntos | botón | CFG-F05 | `configuracion-boton-guardar-puntos` |
| `configuracion.puntos.error.vacio` | Ingresá cuántos pesos vale un punto. | error de campo | CFG-A06 | `configuracion-mensaje-error-pesos-por-punto` |
| `configuracion.puntos.error.menor` | → `general.error.mayor-a-cero` | error de campo | CFG-A06 | `configuracion-mensaje-error-pesos-por-punto` |
| `configuracion.puntos.exito` | Puntos guardados. | éxito | CFG-F05 | `configuracion-mensaje-exito-puntos` |
