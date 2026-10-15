# Spec UX: Configuración (HU-001)

Prototipo: `prototipo.html` (abrir con doble clic; los botones negros de arriba cambian de estado). Flujos: CFG-F01..F06 y CFG-A01..A09 del documento funcional. Tokens y componentes: `../sistema-diseno.md`. Locators: `../../locators.md`.

## 1. Flujo
1. La dueña abre Configuración: ve "Cargando" (gatito) y después los cuatro bloques con los valores guardados.
2. Edita lo que necesita en un bloque (Tu tienda, Inventario, Regla de precio, Puntos).
3. Aprieta el Guardar de ese bloque (o Enter en un campo). El botón se deshabilita y muestra spinner y "Guardando…".4. El resultado aparece debajo del botón de ese bloque: éxito, aviso o error. Los otros bloques no cambian y no se pierde lo escrito.
5. Si el error es de un campo, se marca el campo, se le pone el foco y se explica debajo.

Nada recarga la página. El orden de los bloques va de lo que se toca más a lo que menos: tienda, inventario, precios, puntos.

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
Barra lateral fija (240 px). **El wireframe de arriba muestra el contenido de cada bloque; la ubicación real es una grilla de 2 columnas iguales que aprovecha todo el ancho, con los bordes alineados (sin zigzag):**
- arriba, Tienda | Inventario en Google Sheets;
- abajo, Regla de precio | Puntos.

Las tarjetas de cada fila tienen la misma altura y el Guardar siempre va al pie, alineado con el de su vecina. Puntos lleva al lado del campo un **ejemplo en vivo** ("Una compra de $25.000 suma 25 puntos."), que se recalcula al escribir.

Los campos van en fila dentro de cada bloque, así el Guardar queda cerca:
- Tienda: nombre | logo;
- Sheet: link | pestaña;
- Precios: cotización | redondeo | carpeta de cache, y abajo los 5 bulk en una fila.

El contenido ocupa todo el ancho junto al menú (padding de 32 px, tope de 1600 px). Con menos de 1100 px de ancho pasa a una sola columna de 720 px. Sin índice de secciones. (o) = puntito de rareza.

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
 No pudimos cargar la configuración
 Revisá tu conexión e intentá de nuevo.
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
 | (triángulo) La pestaña «Hoja 1» se renombró a «Inventario». |  aviso (ámbar)
 +---------------------------------------------------------+
 | (círculo !) No pudimos acceder al Sheet. Compartilo...  |   error (rojo)
 +---------------------------------------------------------+
```
**Sin toasts al guardar** (decisión de la dueña): el resultado se muestra una sola vez, en la misma fila del Guardar de cada bloque y a su derecha, sin mover nada. Los errores de campo usan su lugar reservado debajo del campo. La carga es una capa con blur sobre la pantalla, con el gatito (180 px) en una tarjeta de 420 px. El botón Guardar no cambia de tamaño mientras dice "Guardando…". **Guardar el Sheet** usa la capa de carga ("Sincronizando con tu Sheet…"), porque conecta con Google, revisa la pestaña y sincroniza los precios. Tienda, precios y puntos son rápidos y usan la ruedita del botón. Así se ve dónde pasó y QA lo valida con su locator.

### Error de campo
```
 Link o ID del Sheet
 [https://mal...____________]   <- borde rojo grueso
 (!) Revisá el link del Sheet.
```

### Vacío
No aplica: la configuración siempre tiene valores (por defecto si es la primera vez). Un campo opcional vacío (carpeta de cache) muestra su placeholder.

## 3. Componentes
Todos son nuevos y entran al sistema de diseño (ver `../sistema-diseno.md`): **Marco de app** (barra lateral + contenido), Bloque, Campo, Etiqueta con rareza, Botón (primario, secundario, de texto), Mensaje (éxito, aviso, error), Indicador "Guardando…" (dentro del botón), Estado cargando, Estado de falla. No hay componentes sueltos fuera del sistema.

**Marco:** la barra lateral es fija, con logo y nombre de la tienda arriba y el menú Inventario, Reservas, Caja, Movimientos y Configuración (activo). Los formularios van en una grilla de 2 columnas iguales que usa todo el ancho, con los campos en fila y cada uno con su Guardar debajo. Sin índice de secciones (decisión de la dueña: la pantalla es corta). Ícono de cada sección: `store`, `table-2`, `badge-dollar-sign`, `star`; en el menú: `package`, `bookmark`, `wallet`, `arrow-left-right`, `settings-2`.

**Rareza:** puntito de color antes de Common, Uncommon, Rare, Mythic y Tierra básica (tokens `rareza-*`).

**Microinteracciones:** hover suave en botones, ítems y tarjetas; anillo de foco azul en el campo activo; botón con spinner y "Guardando…" mientras guarda; transiciones de 150 ms.

Se arma con Tailwind CSS + estilo shadcn/ui, e íconos Lucide (ver `../sistema-diseno.md`). Íconos usados: `settings-2`, `store`, `table-2`, `badge-dollar-sign`, `star`, `save`, `upload`, `trash-2`, `refresh-cw`, `loader-2`, `plug-zap`, `check-circle-2`, `triangle-alert`, `circle-alert`, `wifi-off`. Esta pantalla no usa toasts.

**Estilo:** moderno, minimalista y cozy: fondo arena, tarjetas blancas de 16 px de radio con sombra muy suave y borde de 1 px, un solo acento azul calmo, tipografía Inter (ver tokens).

**Gatito:** solo en la pantalla de carga (`cat-card-loader.gif`). No aparece en mensajes, falla de carga ni modales. `tcg-default-logo.png` es el logo por defecto del comercio, no la mascota.

**Tamaños (regla):** todos los campos 42 px de alto; botón primario 42 px × 160 px mínimo; botón secundario y de texto 42 px × 120 px mínimo. Mensajes de error, aviso y éxito en 12-13 px (campos 15 px). Tarjetas en una grilla de 2 columnas iguales con 16 px de separación (los 5 bulk pasan a 3 por fila con menos de 1500 px) (ver Wireframe); contenido junto a la barra lateral de 240 px, con padding de 32 px y hasta 1600 px. Los botones nunca parten su texto en dos líneas.

## 4. Textos exactos
> **La fuente de verdad es `docs/v2/textos.md`**, la biblioteca de textos con clave, flujo y locator. Esta tabla queda como resumen de HU-001. Si algo no coincide, manda la biblioteca.

| Dónde | Texto |
|---|---|
| Título | Configuración |
| Bajada | Cada bloque se guarda por separado. Lo que escribas en los otros no se pierde. |
| Bloques | Tu tienda · Inventario en Google Sheets · Regla de precio · Puntos de clientes |
| Botones | Guardar tienda · Guardar inventario · Guardar precios · Guardar puntos · Subir logo · Quitar · Reintentar |
| Cargando | Cargando la configuración… |
| Guardando | Guardando… (en Inventario, capa de carga: Sincronizando con tu Sheet…) |
| Falla al cargar | No pudimos cargar la configuración / Revisá tu conexión e intentá de nuevo. |
| Modal salir | Tenés cambios sin guardar / ¿Salir igual? · Seguir editando · Salir igual |
| Éxito tienda | Tienda guardada. |
| Éxito inventario | Inventario guardado. |
| Éxito precios | Precios guardados. Se recalculan. |
| Éxito puntos | Puntos guardados. |
| CFG-F03 pestaña renombrada (aviso) | La pestaña «Hoja 1» se renombró a «Inventario». |
| CFG-A07 pestaña creada (aviso) | Se creó la pestaña «X». |
| Errores (CFG-A01 a A08) e inesperado | Específicos por campo y por causa (vacío, fuera de rango, formato, falla externa). **Ver `docs/v2/textos.md`**, que es la única fuente, para no duplicar. |
| CFG-A09 falla CK (aviso) | Guardado, pero no se pudo sincronizar con Card Kingdom. Probá más tarde. |

## 5. Interacción
- **Foco inicial:** el campo Nombre de la tienda.
- **Menú:** son enlaces, se recorren con Tab y se activan con Enter. El menú lateral va primero en el orden de Tab.
- **Teclado:** Tab recorre campos y botones en orden visual. Enter en un campo guarda su bloque. No hay atajos globales (pantalla de uso ocasional).
- **Error:** el foco va al primer campo con error. El mensaje de error de bloque usa `role="alert"`; éxito y aviso, `role="status"`.
- **Doble clic en Guardar:** el botón se deshabilita en el primer clic hasta que responde; el segundo no hace nada.
- **Confirmaciones:** no hay acciones destructivas con confirmación. "Quitar logo" solo cambia la vista previa y se aplica al guardar la tienda (reversible hasta entonces).
- **Cambio de bloque durante guardado:** los otros bloques siguen editables; cada uno guarda en forma independiente.
- **Salir con cambios sin guardar:** si algún bloque tiene cambios sin guardar, al navegar a otra pantalla (o cerrar la pestaña) aparece el modal "Tenés cambios sin guardar" / "¿Salir igual?" (ícono triángulo de advertencia, sin gatito) con **Seguir editando** (primario, foco inicial, también Esc) y **Salir igual**. Un bloque guardado con éxito deja de contar como sin guardar. En el prototipo: botón "Salir con cambios".
- **Gmail conectado:** el bloque Inventario muestra "Conectado como <correo>" bajo el título.
- **Bulk:** pesos enteros, sin centavos. Error: "Usá un entero, sin centavos."
- **Mensajes:** un éxito se queda hasta que el bloque se vuelve a editar o guardar. Los errores y avisos no desaparecen solos.

## 6. Locators
Catálogo completo en `docs/v2/locators.md` (Desde = HU-001). Se suman los enlaces del menú lateral (`app-enlace-<modulo>`). Solo llevan `data-testid` botones, campos y los textos que QA valida (mensajes de éxito, aviso y error, y el Gmail conectado). No hay locators en contenedores, bloques, títulos, íconos, estados de carga ni toasts.

| data-testid | Elemento | Para qué |
|---|---|---|
| `configuracion-campo-nombre-tienda` | Nombre de la tienda | Escribir el nombre |
| `configuracion-campo-sheet` | Link o ID del Sheet | Pegar el link |
| `configuracion-campo-pestana-inventario` | Pestaña del inventario | Escribir la pestaña |
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
| `configuracion-mensaje-error-<campo>` | Error junto a un campo (incluye `logo`) | Validar el error de campo |
| `configuracion-texto-gmail-conectado` | "Conectado como ..." | Validar el Gmail |

Nota: el mínimo de la historia pedía `configuracion-mensaje-exito` y `configuracion-mensaje-error`; se les agrega el sufijo `-<bloque>` porque cada bloque tiene su propio mensaje. Los QA los pueden buscar con prefijo.

## 7. Datos por bloque (propuesta de API, CA6)
| Bloque | Lee (GET `/api/v1/configuracion`) | Guarda | Respuesta especial |
|---|---|---|---|
| Tienda | `tienda: { nombre, urlLogo }` | `PUT .../tienda { nombre, quitarLogo? }`; logo: `POST .../tienda/logo` (multipart) | Logo: 400 formato, 413 tamaño |
| Sheet | `sheet: { idOLink, nombrePestanaInventario, correoConectado }` | `PUT .../sheet { idOLink, nombrePestanaInventario }` | `pestanaRenombradaDesde?` (aviso F03), `pestanaCreada?` (aviso A07), 400 link inválido, 403 sin permiso, 401 sesión |
| Precios | `precios: { cotizacionUsd, redondeo, bulk{common,uncommon,rare,mythic,tierraBasica}, carpetaCache }` | `PUT .../precios` | `avisoSincronizacion?` (A09: se guardó igual); 400 con campo que falló |
| Puntos | `puntos: { pesosPorPunto }` | `PUT .../puntos { pesosPorPunto }` | 400 |

Pedidos al backend para el diseño: los errores 400 deben traer el **nombre del campo** que falló (para marcarlo), la respuesta del Sheet debe distinguir "renombrada" de "creada", y el GET debe devolver el correo de Gmail conectado (se usa en el mensaje de sin permiso).

## 8. Decisiones de la dueña (resueltas)
1. Se avisa al salir con cambios sin guardar (modal con `configuracion-boton-seguir-editando` y `configuracion-boton-salir-igual`).
2. Precios bulk en pesos enteros, sin centavos (`bulk` entero en la API; 400 si trae decimales).
3. Se muestra el Gmail conectado en el bloque Inventario (`configuracion-texto-gmail-conectado`).
