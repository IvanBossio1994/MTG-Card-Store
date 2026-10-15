# Sistema de diseño

> Lo mantiene `ux-disenador`. Base definida en HU-001 (Configuración).

## Pantalla de referencia (obligatorio)
**Configuración (`docs/v2/ux/configuracion/prototipo.html` y su `spec.md`) es el ejemplo aprobado por la dueña para todos los módulos.** Toda pantalla nueva parte de ella y repite lo mismo:
- el marco con la barra lateral;
- las tarjetas con ícono y bajada;
- la grilla con columnas alineadas;
- los tamaños de campos y botones;
- los mensajes al lado del botón;
- los errores en un lugar reservado;
- la capa de carga y la regla de espera.

Solo se diseña distinto lo que la pantalla necesita y Configuración no tiene (por ejemplo, tablas). Eso se suma a este sistema como componente nuevo.

## Mascota: el gatito (OBLIGATORIO)
El gatito es la **mascota oficial** de la app. Su diseño **no se modifica**.

| Archivo (`src/main/resources/static/images/`) | Uso |
|---|---|
| `cat-store-login.png` | Pantalla de login |
| `cat-card-loader.gif` | Pantalla de carga |
| `cat-warning.png` | Sin uso en mensajes (ver regla). Reservado |
| `tutorial-cat-box.png` | Tutorial y estados vacíos |
| `tutorial-cat-teacher.png` | Tutorial, explicaciones y ayuda |
| `tutorial-cat-detective.png` | Tutorial, búsquedas y "no se encontró" |
| `tcg-default-logo.png` | Logo por defecto del comercio (no es la mascota) |

**Reglas:**
- **Prohibido:** redibujarlo, recolorearlo, deformarlo, recortarlo, aplicarle filtros o generar versiones nuevas.
- **Permitido:** escalarlo de forma proporcional.
- **No va en mensajes ni ventanas emergentes** (éxito, error, advertencia, confirmación o pregunta): ahí van íconos Lucide. Queda para pantalla de carga, login, tutorial y estados vacíos.
- **Pose nueva:** solo si la persona a cargo aporta la imagen.

## Stack visual (v2)
Tailwind CSS + componentes estilo shadcn/ui (sobre Radix), íconos **Lucide** y avisos emergentes **Sonner**. El prototipo usa Tailwind y Lucide por CDN y un toast propio equivalente a Sonner.

## Identidad
Moderno, minimalista y cozy, al estilo shadcn/ui: fondo arena cálido, tarjetas blancas con borde fino y sombra muy suave, un solo acento azul calmo (derivado del azul de la v1, #1769e0, más apagado) y tipografía sans moderna. Mucho aire. La personalidad la pone el gatito en los estados especiales y el acento, no la decoración. Sin bordes gruesos ni efectos de cómic.

## Tokens

### Colores
| Token (Tailwind) | Valor | Uso |
|---|---|---|
| `arena` | #faf7f2 | Fondo de la app |
| (blanco) | #ffffff | Tarjetas, campos y modales |
| `tinta` | #292524 | Texto principal |
| `suave` | #78716c | Ayudas y texto secundario |
| `borde` | #e7e2d9 | Borde de tarjetas |
| `campo` | #d6d0c4 | Borde de campos y botón secundario |
| `acento` / `hover` / `suave` | #2b5fa6 / #214b85 / #eaf1fa | Único acento (azul calmo, 6,3:1 sobre blanco): botón primario, ítem activo de menú e índice, íconos de bloque, anillo de foco. No se usa para estados (éxito verde, aviso ámbar, error rojo) |
| `exito` / `suave` / `borde` | #0a6b3d / #ecf8f1 / #bfe6cf | Guardado, Gmail conectado |
| `aviso` / `suave` / `borde` | #7a4b00 / #fff6dc / #f1dca0 | Avisos que no bloquean |
| `error` / `suave` / `borde` | #b3261e / #fdeceb / #f3c3bf | Errores |

#### Rareza (puntito junto a la etiqueta)
| Token | Valor | Rareza |
|---|---|---|
| `rareza-common` | #1c1917 | Común (negro) |
| `rareza-uncommon` | #a3acb5 | Infrecuente (plata) |
| `rareza-rare` | #c8a03a | Rara (dorado) |
| `rareza-mythic` | #e2711d | Mítica (naranja) |
| `rareza-tierra` | #7b8f85 | Tierra básica (gris verdoso) |

Puntito de 10 px, redondo, con anillo interno de 1 px al 18 % de negro (para que la plata se vea sobre blanco). Es decorativo (`aria-hidden`): la rareza siempre se lee en el texto de la etiqueta.

El estado nunca va solo por color: siempre lleva ícono y texto.

### Tipografía (Google Fonts)
- **Toda la app:** Inter. Título de pantalla 28 px bold; título de bloque 18 px semibold; texto 15 px; etiquetas 14 px semibold; ayudas 12-14 px.
- **Mensajes** (error, aviso, éxito): 11 px en campos y 12,5 px en bloque, **sin negrita** (peso normal), siempre menores que el texto de los campos (15 px). El error de campo va en rojo suavizado (`error` al 80 %) (pedido de la dueña, HU-004).

### Espaciado
Escala 4 / 8 / 12 / 16 / 24 / 32 / 40 px (`1`, `2`, `3`, `4`, `6`, `8`, `10` de Tailwind). Nada de valores sueltos. Separación entre bloques y entre columnas: 16 px. Padding de tarjeta: 24 px.

### Bordes, radios y sombras
Borde de 1 px. Radio 16 px en tarjetas y modales; 12 px en mensajes y toasts; 10 px en campos y botones. Sombra de tarjeta `0 1px 2px rgba(41,37,36,.04), 0 6px 20px rgba(41,37,36,.05)`; modal y toast, algo más marcada pero difusa. Foco: borde `acento` y anillo de 3 px (acento al 20-30 %).

### Movimiento
Transiciones de 150 ms (`ease`) en color, borde y sombra; entrada de mensajes y toasts 180 ms; botón con `scale(.98)` al presionar; tarjetas con sombra un poco más marcada al pasar el mouse. "Guardando…" usa un spinner de 0,8 s por vuelta. Con `prefers-reduced-motion` todo se reduce a casi cero.

### Layout
Diseñado desde 1280 px. Toda pantalla va dentro del **Marco de app** (ver componentes): barra lateral de 240 px y, a su derecha, el contenido, **alineado a la izquierda y pegado al menú** (padding horizontal 32 px, sin centrar). **Se aprovecha el ancho de la pantalla:**
- **nada se mueve de lugar cuando aparece un mensaje, un error o un estado de carga:** se usan lugares reservados, la fila del botón o capas encima;
- los bloques van uno al lado del otro en una grilla de columnas fijas: todas las filas usan las mismas columnas, así los bordes quedan alineados uno debajo del otro (sin zigzag);
- dentro de cada bloque, los campos van en fila, para que el botón Guardar quede cerca de lo que se edita;
- no se apilan campos si hay lugar al costado (ejemplo: Configuración).
- Una sola columna de 720 px solo debajo de 1100 px de ancho. Las tablas pueden usar todo el ancho disponible. Grillas de 2 columnas (3 para series de campos cortos). Los botones nunca parten su texto en dos líneas.

### Tamaños fijos (reglas)
| Elemento | Alto | Ancho mínimo |
|---|---|---|
| Campo (input, select) | 42 px | 100 % de su columna |
| Botón primario | 42 px | 160 px |
| Botón secundario | 42 px | 120 px |
| Botón de texto | 42 px | 120 px |

Todos los campos tienen el mismo alto y los botones del mismo tipo, el mismo alto y ancho mínimo.

## Componentes
| Componente | Spec | Se usa en |
|---|---|---|
| **Marco de app (AppShell)** | Lo reutilizan todas las pantallas. **Barra lateral** fija a la izquierda: 240 px, fondo blanco, borde derecho 1 px `borde`, padding 24/16, sticky a pantalla completa. Arriba, logo de la tienda (40 px) y nombre (16 px semibold). Debajo, menú vertical con separación de 4 px: Inventario (`package`), Reservas (`bookmark`), Caja (`wallet`), Movimientos (`arrow-left-right`), Configuración (`settings-2`). Ítem: 40 px de alto, radio 10, ícono de 20 px + texto 15 px; hover fondo arena; activo con fondo `acento-suave`, texto `acento` semibold y `aria-current="page"`. **Contenido**: ocupa el resto del ancho, alineado a la izquierda junto al menú, padding 32 px; formularios hasta 720 px. Locators `app-enlace-<modulo>`. | Todas las pantallas |
| **Índice de secciones** | Para pantallas largas con secciones. Columna de 176 px a la izquierda del contenido, sticky. Ítems de 36 px (ícono 16 px + texto 14 px); clic hace scroll suave a la sección (`scroll-margin` para no quedar tapada); la sección visible se marca con fondo `acento-suave`, texto `acento` y `aria-current="true"`. Al llegar al final de la página se marca la última. Formularios a la derecha, apilados, ancho máximo 720 px. **Usarlo solo si la pantalla es realmente larga**, con más de 5 o 6 secciones. | Ninguna por ahora (se sacó de Configuración, que es corta) |
| **Bloque (card)** | Tarjeta blanca, borde 1 px `borde`, radio 16, sombra suave, padding 24. Título con ícono Lucide de 20 px en un cuadradito de 32 px (radio 10, fondo `acento-suave`, ícono `acento`), ayuda, contenido, pie con botón y mensaje. | Configuración |
| **Campo** | Etiqueta arriba (semibold), input de 42 px con borde 1 px `campo`, ayuda opcional y error opcional debajo (11 px, peso normal). Con error: borde y fondo rojo suave, ícono `circle-alert` junto al texto. | Configuración |
| **Botón primario** | Fondo `acento`, texto blanco, ícono Lucide opcional. Hover más oscuro. Mientras guarda: deshabilitado, el contenido pasa a spinner `loader-2` + "Guardando…" dentro del mismo botón (mínimo 160 px). | Guardar |
| **Etiqueta con rareza** | Etiqueta de campo con puntito de rareza (10 px, 8 px de separación) antes del texto. | Precios bulk |
| **Botón secundario** | Fondo blanco, borde 1 px `campo`. | Subir logo, Salir igual |
| **Botón de texto** | Texto `acento`, sin borde; subrayado al pasar el mouse. | Quitar |
| **Mensaje** | Caja con borde 1 px y color por tipo, ícono Lucide (`check-circle-2` éxito, `triangle-alert` aviso, `circle-alert` error), 13 px. `role="alert"` en error, `role="status"` en el resto. | Resultado de guardar |
| **Toast (Sonner)** | Abajo a la derecha, 3,5 s, ícono por tipo, borde fino y sombra difusa. **Regla: nunca duplica un mensaje que ya se ve en pantalla.** Si la acción tiene un lugar propio (un bloque, una fila), el resultado va ahí y no hay toast. Se usa solo para acciones sin lugar fijo (por ejemplo, algo que pasa en segundo plano). | Ninguna por ahora |
| **Modal de confirmación** | Fondo oscuro al 40 %, tarjeta centrada de 420 px, ícono Lucide `triangle-alert`, título, dos botones centrados. Foco en la opción segura; Esc cierra. Sin gatito. | Salir sin guardar |
| **Regla de espera** | **Si la acción tarda** (sincronizar con el Sheet, hablar con Google o Card Kingdom, procesar varias filas), se usa la **capa de carga** con el gatito y un texto que dice qué está pasando ("Sincronizando con tu Sheet…"). **Si es rápida** (guardar un dato local), se usa la **ruedita dentro del botón**. Nunca las dos a la vez. | Toda la app |
| **Indicador "Guardando…"** | Va dentro del botón primario, con un spinner. El texto normal y el de "Guardando…" ocupan la misma celda, así **el botón nunca cambia de tamaño**: siempre mide lo que el texto más largo. | Guardar |
| **Estado cargando** | Capa sobre la pantalla (debajo de la barra superior) con fondo arena al 55 % y **blur de 6 px**: el contenido se adivina detrás. En el centro, una tarjeta del mismo tamaño que el modal (420 px, radio 16) con `cat-card-loader.gif` (180 px) y un título que cambia según lo que pasa ("Cargando la configuración…", "Sincronizando con tu Sheet…"). Es el único lugar con gatito fuera del tutorial. | Carga de pantalla y acciones que tardan |
| **Mensaje de resultado (bloque)** | Va en la misma fila que el botón Guardar, a su derecha, y ocupa el resto del ancho. **No mueve nada:** esa fila ya existe. Una línea con ícono, borde fino y fondo suave del color del estado. | Guardar de cada bloque |
| **Error de campo** | Debajo del campo (y de su ayuda, si tiene), en un lugar reservado de 18 px, así **no mueve nada** al aparecer. **La ayuda queda siempre visible**: muchas veces explica justo el límite del error (por ejemplo, "hasta 2 MB"). | Validaciones |
| **Estado de falla** | Ícono `wifi-off`, título, explicación y botón Reintentar. | Carga fallida |

_(pendiente para próximas historias: tabla, badge de condición o printing, tarjeta de carta)_
