# Spec UX: Configuración (HU-001, actualizada en HU-005)

Prototipo: `prototipo.html` (abrir con doble clic; los botones negros de arriba cambian de estado). Flujos: CFG-F01..F06 y CFG-A01..A09 del documento funcional. Tokens y componentes: `../sistema-diseno.md`. Locators: `../../locators.md`.

**HU-005 cambió dos cosas:** (1) todos los textos pasaron a tono impersonal (ver `docs/v2/textos.md`, que manda); (2) el bloque "Inventario en Google Sheets" tiene ahora dos filas: conectar el Sheet y renombrar una pestaña (sección 2.1). El resto de la spec sigue igual; donde un wireframe de HU-001 muestra textos viejos, mandan los de la biblioteca.

## 1. Flujo
1. La dueña abre Configuración: ve "Cargando" (gatito) y después los cuatro bloques con los valores guardados.
2. Edita lo que necesita en un bloque (Tu tienda, Inventario, Regla de precio, Puntos).
3. Aprieta el Guardar de ese bloque (o Enter en un campo). El botón se deshabilita y muestra spinner y "Guardando…".4. El resultado aparece debajo del botón de ese bloque: éxito, aviso o error. Los otros bloques no cambian y no se pierde lo escrito.
5. Si el error es de un campo, se marca el campo, se le pone el foco y se explica debajo.

Nada recarga la página. Orden de los bloques (también el de Tab): tienda, puntos, inventario, precios.

**Flujo de renombrar una pestaña (CFG-F03, HU-005):** en la segunda fila del bloque Inventario, la dueña elige una pestaña de la lista, escribe el nombre nuevo y aprieta "Renombrar pestaña". Aparece la capa de carga "Renombrando la pestaña…" y el resultado queda debajo de esa fila. Si la pestaña era la del inventario, el campo "Pestaña del inventario" se actualiza solo y la configuración queda guardada con el nombre nuevo.

## 2. Wireframes

### Normal (1280 px: barra lateral 240 + contenido)
```
+-----------+-----------------------------------------------------------+
| [logo]    |  Configuración                                            |
| Mazo      |  Cada bloque se guarda por separado...                    |
| Mágico    |                                                           |
|           |  +-------------------------------------------+            |
| Inventario|  | [ico] Tu tienda                           |            |
| Reservas  |  | [Nombre___________]  Logo [img] [Subir]   |            |
| Caja      |  | [Guardar tienda]                          |            |
| Moviminet.|  +-------------------------------------------+            |
|[Configur.]|  | [ico] Inventario en Google Sheets         |            |
|  (activo) |  | [Link o ID____________] [Pestaña_______]  |            |
|           |  | [Guardar inventario]                      |            |
| (fija)    |  +-------------------------------------------+            |
|           |  | [ico] Regla de precio                     |            |
|           |  | [Cotización____] [Redondear al múltiplo]  |            |
|           |  | Bulk: (o)Common (o)Uncommon (o)Rare       |            |
|           |  |       (o)Mythic (o)Tierra básica          |            |
|           |  | [Guardar precios]                         |            |
|           |  +-------------------------------------------+            |
|           |  | [ico] Puntos de clientes                  |            |
|           |  | [Pesos por punto ($)]  [Guardar puntos]   |            |
|           |  +-------------------------------------------+            |
+-----------+-----------------------------------------------------------+
```
Barra lateral fija (240 px). **El wireframe de arriba muestra el contenido de cada bloque; la ubicación real (HU-005) es una grilla de 6 columnas que aprovecha todo el ancho, con los bordes alineados:**
- arriba, Tienda | Puntos de clientes (3 + 3 columnas, misma altura);
- en medio, Inventario en Google Sheets a todo el ancho (necesita 3 elementos por fila);
- abajo, Regla de precio a todo el ancho.

En las tarjetas de a dos, el Guardar va al pie, alineado con el de su vecina. Puntos lleva al lado del campo un **ejemplo en vivo** ("Una compra de $25.000 suma 25 puntos."), que se recalcula al escribir.

Los campos van en fila dentro de cada bloque, así el Guardar queda cerca:
- Tienda: nombre | logo;
- Sheet: dos filas, ver 2.1;
- Precios: cotización | redondeo | carpeta de cache, y abajo los 5 bulk en una fila (con 2 líneas reservadas para el error).

El contenido ocupa todo el ancho junto al menú (padding de 32 px, tope de 1600 px). Con menos de 1100 px de ancho pasa a una sola columna de 720 px. Sin índice de secciones. (o) = puntito de rareza.

### 2.1 Bloque "Inventario en Google Sheets" (rediseñado en HU-005)
La fila de arriba es campo | campo | lista "Traer cartas de" | botón de 180 px (HU-005, decisión de la dueña); la de renombrar, campo | campo | botón de 180 px. Los dos botones miden lo mismo y quedan alineados a la derecha. Cada fila tiene debajo su **lugar reservado de 40 px para el resultado**, así nada se mueve. La fila de renombrar solo trabaja sobre el **Sheet ya guardado**, no sobre lo que se escribe en el campo del link.
```
 Inventario en Google Sheets
 El Google Sheet es la base de datos de la tienda.
 (plug) Conectado como tienda@gmail.com

 ID o URL del Sheet      Pestaña del inventario   Traer cartas de         (espacio)
 [https://docs.goo..]    [Inventario_________]    [stock viejo · sugerida v] [Guardar inventario]
 Se acepta la URL...     Si no existe, se crea... Solo si la pestaña del inventario es nueva...
 (!) error del campo     (!) error del campo      (!) error de la lista
 [ resultado de Guardar inventario (éxito / aviso / error) ]      <- 40 px reservados
 -------------------------------------------------------------------------------
 Renombrar una pestaña
 Pestaña a renombrar            Nombre nuevo                   (espacio)
 [Elegir una pestaña      v]    [Ej.: Stock 2026__________]    [Renombrar pestaña]
 Pestañas del Sheet guardado.   Hasta 100 caracteres.
 (!) error de la lista          (!) error del nombre
 [ resultado de Renombrar (éxito / aviso / error) ]               <- 40 px reservados
```
**Lista "Traer cartas de"** (mismo componente Lista desplegable). Opciones: "No traer cartas" y una por pestaña del Sheet; la que la app reconoce por tener encabezados de inventario lleva "· sugerida" y viene **preseleccionada**. Reglas: (1) solo cuenta si la pestaña del inventario escrita **no existe**; si existe, se usa tal cual y la lista se ignora (la ayuda lo dice); (2) la lista trabaja sobre el Sheet del campo del link (se carga al salir del campo si es válido, o el guardado al abrir la pantalla); (3) la de origen nunca se modifica.
| Estado | Lista | Ayuda / error de abajo |
|---|---|---|
| Con pestañas (normal) | Preseleccionada la sugerida; si hay varias reconocidas, la de la pestaña en uso o, si no, la primera | "Solo si la pestaña del inventario es nueva. La de origen no se toca." |
| Cargando | Deshabilitada, "Cargando pestañas…", ruedita | igual |
| Sin Sheet (link vacío) | Deshabilitada, "Sin Sheet" | "Disponible cuando se escribe el link del Sheet." |
| Error al listar | Deshabilitada, "No traer cartas" | Error "No se pudieron cargar las pestañas del Sheet." + Reintentar. Guardar sigue funcionando (sin traer cartas) |
| Ninguna reconocida | Habilitada, queda en "No traer cartas" (se puede elegir otra a mano) | Ayuda: "Ninguna pestaña tiene encabezados de inventario." |
| Origen borrado al guardar | Se recarga la lista | Error "La pestaña de origen ya no existe en el Sheet. Se actualizó la lista." |

Al guardar con una pestaña de origen y una de inventario nueva, la capa de carga dice "Trayendo las cartas…" (si no, "Sincronizando con el Sheet…"). El resultado es éxito verde en el lugar reservado de la fila.

**Estados de la lista "Pestaña a renombrar"** (lista desplegable tipo Select de shadcn; hace de campo de 42 px, con la flecha a la derecha). Mientras no está lista, también se deshabilitan el nombre nuevo y el botón:
| Estado | Lista | Ayuda / error de abajo |
|---|---|---|
| Con pestañas (normal) | "Elegir una pestaña" en gris; al abrir, una opción por pestaña. La del inventario lleva "· en uso" | "Pestañas del Sheet guardado." |
| Cargando | Deshabilitada, "Cargando pestañas…" y ruedita en lugar de la flecha | igual |
| Sin Sheet guardado | Deshabilitada, "Sin Sheet guardado" | "Disponible cuando haya un Sheet guardado." |
| Error al listar | Deshabilitada, "Elegir una pestaña" | Error: "No se pudieron cargar las pestañas del Sheet." + enlace-botón "Reintentar" |
| Sin pestañas | Deshabilitada, "El Sheet no tiene pestañas." | igual |

La lista se carga al abrir Configuración (si hay Sheet guardado) y se vuelve a cargar después de **Guardar inventario** (el Sheet pudo cambiar o la pestaña se pudo crear) y de renombrar. El renombrado y el listado son llamadas a Google: renombrar usa la capa de carga con el gatito ("Renombrando la pestaña…"); el listado usa la ruedita dentro de la lista.

**Resultados de renombrar (debajo de su fila):**
- Pestaña común → éxito: "La pestaña «Hoja 1» se renombró a «Stock 2026»."
- Era la del inventario → **éxito (verde, igual que una pestaña cualquiera; decidido con la dueña)**: "La pestaña «Inventario» se renombró a «Stock 2026». También se actualizó la pestaña del inventario." El campo "Pestaña del inventario" pasa a mostrar el nombre nuevo, sin quedar como "cambio sin guardar" (la API ya lo guardó).
- Se limpia el nombre nuevo y la lista se recarga con los nombres actualizados.

**Errores de renombrar:**
| Causa | Dónde | Texto |
|---|---|---|
| Sin elegir pestaña | bajo la lista | La pestaña a renombrar es obligatoria. |
| Nombre vacío | bajo el nombre nuevo | El nombre nuevo es obligatorio. |
| Más de 100 caracteres | bajo el nombre nuevo | El nombre no debe superar los 100 caracteres. |
| Igual al actual | bajo el nombre nuevo | El nombre nuevo es igual al actual. |
| Ya existe otra con ese nombre (sin distinguir mayúsculas) | bajo el nombre nuevo | Ya existe una pestaña con ese nombre. |
| La pestaña fue borrada en el Sheet | resultado de la fila (error) | La pestaña ya no existe en el Sheet. Se actualizó la lista. |
| Sin permiso / sesión vencida / inesperado | resultado de la fila (error) | Los de siempre (ver textos.md) |

**Guardar inventario ya no renombra.** Para cambiar el nombre de una existente se usa la fila de renombrar. Si la pestaña escrita (decidido con la dueña, 2026-10-15):
- **ya existe** → la usa tal cual, sin copiar ni pisar nada;
- **no existe y se eligió una pestaña en "Traer cartas de"** → crea la nueva y **trae las cartas de esa pestaña en el formato de la app** (columnas de la app, en su orden y con sus nombres; se reconocen por los alias del encabezado). Las columnas que la app no reconoce pasan igual, al final. La pestaña de origen **no se modifica**. Éxito (verde): "Se creó la pestaña «Stock 2026» con 128 cartas de «stock viejo». Las columnas que la app no usa quedaron al final." (la segunda frase solo si había columnas desconocidas);
- **no existe y se eligió "No traer cartas"** → la crea con los encabezados ("Se creó la pestaña «X».", aviso);
- **Sheet nuevo** (solo la "Hoja 1" vacía de Google) → usa esa pestaña y le pone el nombre pedido, en lugar de dejarla vacía y crear otra.

### Cargando
```
 Configuración
      (gatito cargando)
   Cargando la configuración…
 [~~~~~~~~ esqueleto ~~~~~~~~]
 [~~~~~~~~ esqueleto ~~~~~~~~]
```

### Falla al cargar
```
      (ícono wifi-off)
 No se pudo cargar la configuración.
 No hay conexión a internet. Se puede reintentar cuando se restablezca.
            [Reintentar]
```

### Guardando (en el bloque)
```
 [ (spinner) Guardando y sincronizando… ]   <- el mismo botón, deshabilitado
```

### Éxito, aviso y error (debajo del botón del bloque)
```
 [Guardar inventario]
 +---------------------------------------------------------+
 | (check) Inventario guardado.                            |   exito (verde)
 +---------------------------------------------------------+
 | (triángulo) Se creó la pestaña «Stock 2026».            |  aviso (ámbar)
 +---------------------------------------------------------+
 | (círculo !) La app no tiene acceso a este Sheet. Compar...|   error (rojo)
 +---------------------------------------------------------+
```
**Sin toasts al guardar** (decisión de la dueña): el resultado se muestra una sola vez, en la misma fila del Guardar de cada bloque y a su derecha, sin mover nada. Los errores de campo usan su lugar reservado debajo del campo. La carga es una capa con blur sobre la pantalla, con el gatito (180 px) en una tarjeta de 420 px. El botón Guardar no cambia de tamaño mientras dice "Guardando…". **Guardar el Sheet** usa la capa de carga ("Sincronizando con el Sheet…"), porque conecta con Google, revisa la pestaña y sincroniza los precios. **Renombrar una pestaña** usa la capa de carga "Renombrando la pestaña…". Tienda, precios y puntos son rápidos y usan la ruedita del botón. Así se ve dónde pasó y QA lo valida con su locator.

### Error de campo
```
 ID o URL del Sheet
 [https://mal...____________]   <- borde rojo
 (!) Debe ser una URL de Google Sheets o solo el ID.   <- 11 px, peso normal, rojo al 80 %
```

### Vacío
No aplica: la configuración siempre tiene valores (por defecto si es la primera vez). Un campo opcional vacío (carpeta de cache) muestra su placeholder.

## 3. Componentes
Todos son nuevos y entran al sistema de diseño (ver `../sistema-diseno.md`): **Marco de app** (barra lateral + contenido), Bloque, Campo, Etiqueta con rareza, Botón (primario, secundario, de texto), Mensaje (éxito, aviso, error), Indicador "Guardando…" (dentro del botón), Estado cargando, Estado de falla. No hay componentes sueltos fuera del sistema.

**Marco:** la barra lateral es fija, con logo y nombre de la tienda arriba y el menú Inventario, Reservas, Caja, Movimientos y Configuración (activo). Los formularios van en una grilla de 2 columnas iguales que usa todo el ancho, con los campos en fila y cada uno con su Guardar debajo. Sin índice de secciones (decisión de la dueña: la pantalla es corta). Ícono de cada sección: `store`, `table-2`, `badge-dollar-sign`, `star`; en el menú: `package`, `bookmark`, `wallet`, `arrow-left-right`, `settings-2`.

**Componente nuevo (HU-005): Lista desplegable** (Select de shadcn sobre Radix). Misma altura y borde que un campo (42 px, 10 px de radio), flecha `chevron-down` a la derecha, texto gris cuando no hay nada elegido, fondo arena y cursor "no permitido" cuando está deshabilitada. Se recorre con teclado (Enter/Espacio abre, flechas eligen, Esc cierra). Se suma al sistema de diseño. Ícono nuevo: `pencil` (botón Renombrar), `chevron-down`.

**Rareza:** puntito de color antes de Common, Uncommon, Rare, Mythic y Tierra básica (tokens `rareza-*`).

**Microinteracciones:** hover suave en botones, ítems y tarjetas; anillo de foco azul en el campo activo; botón con spinner y "Guardando…" mientras guarda; transiciones de 150 ms.

Se arma con Tailwind CSS + estilo shadcn/ui, e íconos Lucide (ver `../sistema-diseno.md`). Íconos usados: `settings-2`, `store`, `table-2`, `badge-dollar-sign`, `star`, `save`, `upload`, `trash-2`, `refresh-cw`, `loader-2`, `plug-zap`, `check-circle-2`, `triangle-alert`, `circle-alert`, `wifi-off`. Esta pantalla no usa toasts.

**Estilo:** moderno, minimalista y cozy: fondo arena, tarjetas blancas de 16 px de radio con sombra muy suave y borde de 1 px, un solo acento azul calmo, tipografía Inter (ver tokens).

**Gatito:** solo en la pantalla de carga (`cat-card-loader.gif`). No aparece en mensajes, falla de carga ni modales. `tcg-default-logo.png` es el logo por defecto del comercio, no la mascota.

**Tamaños (regla):** todos los campos 42 px de alto; botón primario 42 px × 160 px mínimo; botón secundario y de texto 42 px × 120 px mínimo. Errores de campo en 11 px, peso normal, rojo al 80 %; avisos y éxitos de bloque en 13 px (campos 15 px). Tarjetas en una grilla de 6 columnas con 16 px de separación (ver Wireframe); filas del Sheet: campo | campo | botón de 180 px; contenido junto a la barra lateral de 240 px, con padding de 32 px y hasta 1600 px. Los botones nunca parten su texto en dos líneas.

## 4. Textos exactos
> **La fuente de verdad es `docs/v2/textos.md`**, la biblioteca de textos con clave, flujo y locator. Esta tabla queda como resumen de HU-001. Si algo no coincide, manda la biblioteca.

| Dónde | Texto |
|---|---|
| Título | Configuración |
| Bajada | Cada bloque se guarda por separado. Lo escrito en los otros no se pierde. |
| Bloques | Tienda · Puntos de clientes · Inventario en Google Sheets · Regla de precio |
| Botones | Guardar tienda · Guardar inventario · Renombrar pestaña · Guardar precios · Guardar puntos · Subir logo · Quitar · Reintentar |
| Cargando | Cargando la configuración… |
| Guardando | Guardando… (en Inventario, capa de carga: Sincronizando con el Sheet…; al renombrar: Renombrando la pestaña…) |
| Falla al cargar | No se pudo cargar la configuración. / No hay conexión a internet. Se puede reintentar cuando se restablezca. |
| Modal salir | Hay cambios sin guardar / ¿Salir igual? · Seguir editando · Salir igual |
| Éxito tienda | Tienda guardada. |
| Éxito inventario | Inventario guardado. |
| Éxito precios | Precios guardados. Se recalculan. |
| Éxito puntos | Puntos guardados. |
| CFG-F03 pestaña renombrada (éxito) | La pestaña «Hoja 1» se renombró a «Stock 2026». |
| CFG-F03 renombrada = la del inventario (aviso) | La pestaña «Inventario» se renombró a «Stock 2026». También se actualizó la pestaña del inventario. |
| CFG-A07 pestaña creada (aviso) | Se creó la pestaña «X». |
| Errores (CFG-A01 a A08), de renombrar e inesperado | Específicos por campo y por causa (vacío, fuera de rango, formato, repetido, falla externa). **Ver `docs/v2/textos.md`**, que es la única fuente, para no duplicar. |
| CFG-A09 falla CK (aviso) | Guardado, pero no se pudo sincronizar con Card Kingdom. Se puede reintentar más tarde. |

## 5. Interacción
- **Foco inicial:** el campo Nombre de la tienda.
- **Menú:** son enlaces, se recorren con Tab y se activan con Enter. El menú lateral va primero en el orden de Tab.
- **Teclado:** Tab recorre campos y botones en orden visual. Enter en un campo guarda su bloque. No hay atajos globales (pantalla de uso ocasional).
- **Error:** el foco va al primer campo con error. El mensaje de error de bloque usa `role="alert"`; éxito y aviso, `role="status"`.
- **Doble clic en Guardar:** el botón se deshabilita en el primer clic hasta que responde; el segundo no hace nada.
- **Confirmaciones:** no hay acciones destructivas con confirmación. "Quitar logo" solo cambia la vista previa y se aplica al guardar la tienda (reversible hasta entonces).
- **Cambio de bloque durante guardado:** los otros bloques siguen editables; cada uno guarda en forma independiente.
- **Salir con cambios sin guardar:** si algún bloque tiene cambios sin guardar, al navegar a otra pantalla (o cerrar la pestaña) aparece el modal "Hay cambios sin guardar" / "¿Salir igual?" (ícono triángulo de advertencia, sin gatito) con **Seguir editando** (primario, foco inicial, también Esc) y **Salir igual**. Un bloque guardado con éxito deja de contar como sin guardar. En el prototipo: botón "Salir con cambios".
- **Gmail conectado:** el bloque Inventario muestra "Conectado como <correo>" bajo el título.
- **Bulk:** pesos enteros, sin centavos. Error: "El precio debe ser un número entero, sin centavos." (2 líneas reservadas).
- **Renombrar pestaña (HU-005):** el foco, al fallar, va al primer campo con error (la lista o el nombre nuevo). Enter en "Nombre nuevo" aprieta Renombrar; Enter en los campos de la fila 1 guarda el inventario. La lista, el nombre nuevo y el botón se deshabilitan juntos cuando no hay pestañas para elegir. Doble clic en Renombrar: la capa de carga bloquea el segundo. No lleva confirmación (se puede volver a renombrar), pero el aviso aclara cuando se cambió también la del inventario. Renombrar y Guardar inventario son independientes: renombrar no guarda lo escrito en el link ni en la pestaña del inventario, y viceversa. El nombre nuevo escrito no cuenta como "cambio sin guardar".
- **Orden de Tab del Sheet:** link, pestaña del inventario, Traer cartas de, Guardar inventario, lista, nombre nuevo, Renombrar pestaña.
- **Mensajes:** un éxito se queda hasta que el bloque se vuelve a editar o guardar. Los errores y avisos no desaparecen solos.

## 6. Locators
Catálogo completo en `docs/v2/locators.md` (Desde = HU-001). Se suman los enlaces del menú lateral (`app-enlace-<modulo>`). Solo llevan `data-testid` botones, campos y los textos que QA valida (mensajes de éxito, aviso y error, y el Gmail conectado). No hay locators en contenedores, bloques, títulos, íconos, estados de carga ni toasts.

| data-testid | Elemento | Para qué |
|---|---|---|
| `configuracion-campo-nombre-tienda` | Nombre de la tienda | Escribir el nombre |
| `configuracion-campo-sheet` | ID o URL del Sheet | Pegar el link |
| `configuracion-campo-pestana-inventario` | Pestaña del inventario | Escribir la pestaña |
| `configuracion-selector-pestana-a-renombrar` | Lista desplegable de pestañas (HU-005) | Elegir la pestaña a renombrar |
| `configuracion-selector-traer-cartas-de` | Lista "Traer cartas de" (HU-005) | Elegir la pestaña de origen o "No traer cartas" |
| `configuracion-texto-ayuda-traer-cartas-de` | Ayuda de esa lista | Validar "ninguna reconocida" y "sin Sheet" |
| `configuracion-mensaje-error-traer-cartas-de` | Error junto a esa lista | Validar falla al listar y origen borrado |
| `configuracion-boton-reintentar-pestanas-origen` | Dentro del error de esa lista | Recargar la lista |
| `configuracion-campo-nombre-nuevo` | Nombre nuevo (HU-005) | Escribir el nombre |
| `configuracion-boton-renombrar-pestana` | Renombrar pestaña (HU-005) | Renombrar |
| `configuracion-boton-reintentar-pestanas` | Dentro del error de la lista (HU-005) | Recargar la lista |
| `configuracion-mensaje-exito-renombrar` / `-error-renombrar` (renombrar la del inventario también es éxito, en verde) | Resultado de la fila de renombrar (HU-005) | Validar el resultado |
| `configuracion-mensaje-error-pestana-a-renombrar` / `-nombre-nuevo` | Errores junto a la lista y al nombre nuevo (HU-005) | Validar el error de campo |
| `configuracion-campo-cotizacion` / `-redondeo` | Cotización y múltiplo | Regla de precio |
| `configuracion-campo-bulk-<rareza>` | `common`, `uncommon`, `rare`, `mythic`, `tierra-basica` | Precio bulk |
| `configuracion-campo-carpeta-cache` | Carpeta de cache | Ruta opcional |
| `configuracion-campo-pesos-por-punto` | Pesos por punto | Lógica de puntos |
| `configuracion-boton-subir-logo` / `-quitar-logo` | Logo | Subir o quitar |
| `configuracion-boton-guardar-<bloque>` | `tienda`, `sheet`, `precios`, `puntos` | Guardar el bloque |
| `configuracion-boton-reintentar` | Falla al cargar | Reintentar |
| `configuracion-boton-seguir-editando` / `-salir-igual` | Modal de salir | Elegir |
| `configuracion-enlace-iniciar-sesion-google` | Dentro del error de sesión vencida | Volver a iniciar sesión |
| `app-enlace-<modulo>` | Menú lateral: `inventario`, `reservas`, `caja`, `movimientos`, `configuracion` | Navegar entre módulos |
| `configuracion-mensaje-exito-<bloque>` | Éxito del bloque | Validar el guardado |
| `configuracion-mensaje-aviso-<bloque>` | Aviso (pestaña renombrada o creada, falla de Card Kingdom) | Validar el aviso |
| `configuracion-mensaje-error-<bloque>` | Error de bloque (sin permiso, sesión, número) | Validar el error |
| `configuracion-mensaje-error-<campo>` | Error junto a un campo (incluye `logo`; el del link del Sheet es `link-sheet`) | Validar el error de campo |
| `configuracion-texto-gmail-conectado` | "Conectado como ..." | Validar el Gmail |

Nota: el mínimo de la historia pedía `configuracion-mensaje-exito` y `configuracion-mensaje-error`; se les agrega el sufijo `-<bloque>` porque cada bloque tiene su propio mensaje. Los QA los pueden buscar con prefijo.

## 7. Datos por bloque (propuesta de API, CA6)
| Bloque | Lee (GET `/api/v1/configuracion`) | Guarda | Respuesta especial |
|---|---|---|---|
| Tienda | `tienda: { nombre, urlLogo }` | `PUT .../tienda { nombre, quitarLogo? }`; logo: `POST .../tienda/logo` (multipart) | Logo: 400 formato, 413 tamaño |
| Sheet | `sheet: { idOLink, nombrePestanaInventario, correoConectado }` | `PUT .../sheet { idOLink, nombrePestanaInventario, traerCartasDe? }` | **HU-005:** ya no renombra. `traerCartasDe` = nombre de la pestaña de origen, o `null`/ausente para "No traer cartas"; el back lo ignora si la pestaña del inventario ya existe. Respuesta: `pestanaCreada?` (aviso A07; también cuando el Sheet no tenía ninguna) y `cartasTraidas?: { origen, cantidad, hayColumnasExtra }` (éxito). 400 link inválido; 400 `campo: traer-cartas-de` / `ORIGEN_INEXISTENTE`; 403 sin permiso; 401 sesión. `pestanaRenombradaDesde` deja de existir |
| Precios | `precios: { cotizacionUsd, redondeo, bulk{common,uncommon,rare,mythic,tierraBasica}, carpetaCache }` | `PUT .../precios` | `avisoSincronizacion?` (A09: se guardó igual); 400 con campo que falló |
| Puntos | `puntos: { pesosPorPunto }` | `PUT .../puntos { pesosPorPunto }` | 400 |

Pedidos al backend para el diseño: los errores 400 deben traer el **nombre del campo** que falló (para marcarlo), la respuesta del Sheet debe distinguir "renombrada" de "creada", y el GET debe devolver el correo de Gmail conectado (se usa en el mensaje de sin permiso).

### 7.1 Pestañas del Sheet (propuesta para HU-006)
Los dos endpoints trabajan sobre el **Sheet guardado** en la configuración.

**Listar pestañas:** `GET /api/v1/configuracion/sheet/pestanas[?idOLink=<link o ID>]`
- `idOLink` es opcional: si viene, lista las pestañas de ese Sheet (lo usa "Traer cartas de" para un Sheet escrito y todavía no guardado); si no, usa el guardado. Renombrar sigue usando siempre el guardado.
- 200 con Sheet: `{ "sheetGuardado": true, "pestanas": [ { "nombre": "Inventario", "enUso": true, "sugerida": false }, { "nombre": "stock viejo", "enUso": false, "sugerida": true } ] }` (en el orden del Sheet; `enUso` marca la del inventario; `sugerida` marca las que tienen encabezados de inventario reconocibles por alias; el front preselecciona la primera sugerida, prefiriendo la `enUso`). Con `idOLink` válido, `sheetGuardado` es `true` igual (es "hay Sheet").
- 200 sin Sheet guardado: `{ "sheetGuardado": false, "pestanas": [] }` (no es error: la lista muestra "Sin Sheet guardado").
- Errores: 401 sesión vencida, 403 sin permiso sobre el Sheet, 502 o 500 si falla Google (la lista muestra "No se pudieron cargar las pestañas del Sheet." con Reintentar).

**Renombrar una pestaña:** `PUT /api/v1/configuracion/sheet/pestanas/renombrar`
- Request: `{ "nombreActual": "Hoja 1", "nombreNuevo": "Stock 2026" }`.
- 200: `{ "nombreAnterior": "Hoja 1", "nombreNuevo": "Stock 2026", "eraPestanaInventario": false, "nombrePestanaInventario": "Inventario", "pestanas": [ …lista actualizada… ] }`. Si `eraPestanaInventario` es true, el back **ya guardó** el nombre nuevo como pestaña del inventario (el front solo muestra el aviso y actualiza el campo).
- 400 con `campo` y `codigo` (para marcar el campo): `pestana-a-renombrar` / `PESTANA_VACIA`; `nombre-nuevo` / `NOMBRE_VACIO`, `NOMBRE_LARGO` (más de 100), `NOMBRE_IGUAL`, `NOMBRE_REPETIDO` (sin distinguir mayúsculas). El front valida lo mismo antes de enviar; el back lo revalida.
- 404 `PESTANA_INEXISTENTE` (alguien la borró en el Sheet), 401 sesión vencida, 403 sin permiso, 500 inesperado (resultado de la fila).
- No hay Sheet guardado: 400 `SIN_SHEET_GUARDADO` (el front no deja llegar: la lista está deshabilitada).

## 8. Decisiones de la dueña (resueltas)
1. Se avisa al salir con cambios sin guardar (modal con `configuracion-boton-seguir-editando` y `configuracion-boton-salir-igual`).
2. Precios bulk en pesos enteros, sin centavos (`bulk` entero en la API; 400 si trae decimales).
3. Se muestra el Gmail conectado en el bloque Inventario (`configuracion-texto-gmail-conectado`).
