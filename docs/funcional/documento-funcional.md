# Documento funcional: TCG Inventory

- **Versión:** 0.4.0 (la base completa de la v1 pasa a 1.0.0)
- **Mantiene:** `analista-funcional` (contenido) y el líder (versión e historial)

> **Cómo leerlo (agentes):** buscá con Grep el título del módulo (por ejemplo `## Reservas`) y leé solo esa sección. No leas el documento entero.
>
> **Estados de un flujo:**
> - `vigente`: así funciona hoy.
> - `propuesto · HU-XXX`: en desarrollo.
> - `diseñado · HU-XXX → HU-YYY`: el diseño se aprobó en HU-XXX, pero falta programarlo. Pasa a `vigente` cuando se acepta HU-YYY.
> - `reemplazado por <ID>`: quedó viejo y lo reemplaza otro flujo.
>
> **Versión:**
> - **MAYOR:** corte de la v2 o una reestructura.
> - **MENOR:** una HU que agrega funcionalidad.
> - **PARCHE:** una corrección o aclaración.

## Historial de versiones
| Versión | Fecha | HU | Cambios |
|---|---|---|---|
| 0.1.0 | 2026-10-14 | — | Esqueleto del documento |
| 0.2.0 | 2026-10-14 | HU-000 | Google: GOO-F01 (iniciar sesión) y GOO-F02 (conectar el Sheet) |
| 0.4.0 | 2026-10-15 | HU-003, HU-004 | Configuración v2 usable: CFG-F01 a F06 y CFG-A01 a A09 pasan a `vigente`. Cambios al programarla: la pestaña pedida que no existe renombra la del inventario de antes (CFG-F03, CFG-A07); mensajes de error que dicen cómo va el campo. |
| 0.3.0 | 2026-10-14 | HU-001 | Configuración v2: CFG-F01 a F06 y CFG-A01 a A09 con el diseño aprobado (`diseñado`, se programa en HU-003). Nuevo estado `diseñado`. Los textos exactos pasan a `docs/v2/textos.md`. |

## Índice
1. [Glosario](#glosario)
2. [Actores](#actores)
3. [Inventario (INV)](#inventario-inv)
4. [Precios Card Kingdom (PRE)](#precios-card-kingdom-pre)
5. [Reservas y clientes (RES)](#reservas-y-clientes-res)
6. [Caja (CAJ)](#caja-caj)
7. [Movimientos y reportes (MOV)](#movimientos-y-reportes-mov)
8. [Importar lista (IMP)](#importar-lista-imp)
9. [Configuración y tutorial (CFG)](#configuración-y-tutorial-cfg)
10. [Google: sesión y Sheet (GOO)](#google-sesión-y-sheet-goo)

## Glosario
- **Condición:** estado físico de la carta (NM, LP, MP, HP, DMG).
- **Printing:** normal o foil.
- **Set / código de set / número:** edición de la carta y su número dentro de esa edición.
- **CK:** Card Kingdom, la tienda de EE. UU. de donde salen los precios de referencia en USD.
- **Precio local:** precio CK × cotización, redondeado hacia arriba al múltiplo configurado.
- **Reserva:** carta apartada para un cliente.
- **Retiro:** el cliente viene a buscarla.
- **Separar:** reservar a partir del stock pendiente.
- **Entregar:** la venta se concreta; se descuenta el stock y se registra en caja.
- **Caja:** registro de ventas del día, con total diario.
- **Movimientos:** historial de cambios de stock (cantidad anterior y nueva).
- **Sheet:** el Google Sheet del comercio. Es la fuente de verdad y también se edita a mano.

## Actores
- **Empleado:** atiende el mostrador: busca, vende, reserva, entrega.
- **Dueño del comercio:** configura, ve movimientos, caja y reportes, y edita el Sheet a mano.
- **Sistemas externos:** Google (OAuth y Sheets) y Card Kingdom (lista de precios).

## Inventario (INV)
### Caminos felices
_(pendiente)_
### Caminos alternativos y de error
_(pendiente)_

## Precios Card Kingdom (PRE)
### Caminos felices
_(pendiente)_
### Caminos alternativos y de error
_(pendiente)_

## Reservas y clientes (RES)
### Caminos felices
_(pendiente)_
### Caminos alternativos y de error
_(pendiente)_

## Caja (CAJ)
### Caminos felices
_(pendiente)_
### Caminos alternativos y de error
_(pendiente)_

## Movimientos y reportes (MOV)
### Caminos felices
_(pendiente)_
### Caminos alternativos y de error
_(pendiente)_

## Importar lista (IMP)
### Caminos felices
_(pendiente)_
### Caminos alternativos y de error
_(pendiente)_

## Configuración y tutorial (CFG)
> Pantalla Configuración v2 (diseño en HU-001; la API se programa en HU-003 y la pantalla en HU-004). Cada bloque se guarda por separado, sin recargar la pantalla; el resultado se muestra junto al bloque. Hoy (v1) hay un solo botón que guarda todo y recarga. El tutorial no está documentado todavía.

### Caminos felices
#### CFG-F01: Guardar identidad de la tienda (nombre y logo) `vigente · HU-004`
- **Actor:** dueño del comercio · **Disparador:** botón Guardar del bloque Identidad.
- **Precondiciones:** sesión de Google iniciada.
- **Pasos:** 1. Escribe el nombre de la tienda. 2. Opcional: sube un logo (PNG, JPG o WEBP, hasta 2 MB) o marca quitar el logo. 3. Guarda.
- **Resultado:** mensaje de éxito junto al bloque; el nombre y el logo nuevos se ven en la app sin recargar.
- **Reglas de negocio:** el nombre es obligatorio. El logo no puede superar 2 MB. Solo se aceptan PNG, JPG o WEBP.
- **Pantalla / locators:** `configuracion-campo-nombre-tienda`, `configuracion-boton-subir-logo`, `configuracion-boton-guardar-tienda`, `configuracion-mensaje-exito` · **Endpoint:** `PUT /api/v1/configuracion/tienda`, `POST /api/v1/configuracion/tienda/logo` · **Sheet:** no usa.

#### CFG-F02: Conectar el Sheet y elegir la pestaña del inventario `propuesto · HU-005` [vigente desde 0.4.0 · HU-004; cambia el trato de la pestaña]
- **Actor:** dueño · **Disparador:** botón Guardar inventario (fila 1 del bloque).
- **Precondiciones:** sesión de Google iniciada; el Sheet es del Gmail conectado o está compartido con él.
- **Pasos:** 1. Ingresa el link o ID del Sheet; al salir del campo se cargan sus pestañas en la lista «Traer cartas de». 2. Escribe el nombre de la pestaña del inventario. 3. En «Traer cartas de» deja la pestaña sugerida (la que tiene encabezados de inventario), elige otra o elige «No traer cartas». 4. Guarda. 5. La app verifica el acceso, crea las pestañas y columnas que falten y sincroniza el inventario. 6. Se vuelve a cargar la lista de pestañas de la fila de renombrar (CFG-F03).
- **Resultado:** el Sheet y la pestaña quedan guardados; mensaje junto al bloque. Según la pestaña escrita:
  - ya existe: se usa tal cual, sin copiar ni pisar nada; «Traer cartas de» se ignora;
  - no existe y se eligió una pestaña de origen: se crea la nueva y se **traen las cartas de esa pestaña en el formato de la app** (columnas con sus nombres y orden; se reconocen por alias del encabezado; las que la app no reconoce pasan igual, al final). La pestaña de origen **no se modifica**. Éxito en verde con la cantidad de cartas y el origen (`configuracion.sheet.traer.*`; suma la frase de columnas extra si hubo desconocidas). Mientras trabaja, la capa de carga dice «Trayendo las cartas…»;
  - no existe y se eligió «No traer cartas»: se crea con los encabezados y se avisa (`configuracion.sheet.aviso.creada`, ver CFG-A07);
  - Sheet nuevo (solo la «Hoja 1» vacía de Google): se usa esa pestaña con el nombre pedido, sin dejar una vacía.
- **Reglas de negocio:** acepta link completo o solo el ID. Ambos campos son obligatorios. **Guardar inventario ya no renombra pestañas** (reemplaza el comportamiento de 0.4.0, que renombraba la anterior); para renombrar se usa CFG-F03. Si el Sheet no tiene ninguna pestaña, se crea la primera. Ver GOO-F02 para la conexión inicial.
- **Pantalla / locators:** `configuracion-campo-sheet`, `configuracion-campo-pestana-inventario`, `configuracion-selector-traer-cartas-de`, `configuracion-texto-ayuda-traer-cartas-de`, `configuracion-mensaje-error-traer-cartas-de`, `configuracion-boton-reintentar-pestanas-origen`, `configuracion-boton-guardar-sheet`, `configuracion-mensaje-exito-sheet`, `configuracion-mensaje-aviso-sheet` · **Endpoint:** `GET /api/v1/configuracion/sheet/pestanas?idOLink=…` (lista con `sugerida`), `PUT /api/v1/configuracion/sheet` (con `traerCartasDe` opcional) · **Sheet:** pestaña de inventario (nueva con las cartas traídas, nueva vacía o existente) y alta de Movimientos, Caja, Reservas, Clientes si faltan; la de origen solo se lee.
- **Alternativos de «Traer cartas de»:** sin link escrito, la lista queda deshabilitada («Disponible cuando se escribe el link del Sheet.»); si falla el listado, error con Reintentar y Guardar sigue funcionando sin traer cartas (como CFG-A10); si ninguna pestaña se reconoce, queda en «No traer cartas» con su ayuda; si la de origen fue borrada al guardar, error «La pestaña de origen ya no existe en el Sheet. Se actualizó la lista.» (como CFG-A13).

#### CFG-F03: Renombrar una pestaña del Sheet `propuesto · HU-005` [reemplaza al CFG-F03 de 0.4.0 «Renombrar la pestaña única», que ocurría al guardar el inventario]
- **Actor:** dueño · **Disparador:** botón Renombrar pestaña (fila 2 del bloque Inventario).
- **Precondiciones:** hay un Sheet ya guardado (la fila trabaja sobre el guardado, no sobre lo escrito en el campo del link); sesión de Google iniciada.
- **Pasos:** 1. La lista «Pestaña a renombrar» se carga sola al abrir Configuración, con las pestañas del Sheet guardado; la del inventario lleva la marca «en uso». 2. Elige una pestaña. 3. Escribe el nombre nuevo (hasta 100 caracteres). 4. Aprieta Renombrar pestaña. 5. Aparece la carga «Renombrando la pestaña…».
- **Resultado:** la pestaña se renombra y la lista se actualiza. Éxito en verde debajo de la fila (`configuracion.sheet.renombrar.exito`). Si era la pestaña del inventario, el mensaje agrega que también se actualizó la pestaña del inventario (`configuracion.sheet.aviso.renombrada`, también éxito en verde): la configuración queda guardada con el nombre nuevo y el campo «Pestaña del inventario» lo muestra sin quedar como cambio sin guardar.
- **Reglas de negocio:** el nombre nuevo es obligatorio, de hasta 100 caracteres, distinto del actual y sin repetir otra pestaña (sin distinguir mayúsculas). La lista se recarga al abrir, tras Guardar inventario y tras renombrar. Estados de la lista: con pestañas; cargando (lista, nombre nuevo y botón deshabilitados); sin Sheet guardado («Disponible cuando haya un Sheet guardado.»); error al listar con enlace Reintentar (CFG-A10); Sheet sin pestañas.
- **Pantalla / locators:** `configuracion-selector-pestana-a-renombrar`, campo de nombre nuevo, `configuracion-boton-renombrar-pestana`, `configuracion-mensaje-exito-renombrar` (ver `docs/v2/ux/configuracion/spec.md`, sección 6) · **Endpoint:** `GET /api/v1/configuracion/sheet/pestanas`, `PUT /api/v1/configuracion/sheet/pestanas/renombrar` (propuestos, HU-006) · **Sheet:** nombre de la pestaña elegida; si era la del inventario, también el nombre guardado en la configuración.

#### CFG-F04: Guardar la regla de precio `vigente · HU-004`
- **Actor:** dueño · **Disparador:** botón Guardar del bloque Precios.
- **Pasos:** 1. Carga la cotización ARS por USD de Card Kingdom. 2. Elige el múltiplo de redondeo. 3. Carga el precio bulk por rareza (common, uncommon, rare, mythic, tierra básica). 4. Opcional: indica la carpeta de cache de Card Kingdom. 5. Guarda.
- **Resultado:** mensaje de éxito; los precios se recalculan con la regla nueva.
- **Reglas de negocio:** cotización, múltiplo y cada precio bulk deben ser mayores a cero. La carpeta de cache debe ser una ruta válida.
- **Pantalla / locators:** `configuracion-campo-cotizacion`, `configuracion-campo-redondeo`, `configuracion-campo-bulk-<rareza>`, `configuracion-boton-guardar-precios` · **Endpoint:** `PUT /api/v1/configuracion/precios` · **Sheet:** no usa.

#### CFG-F05: Guardar la lógica de puntos `vigente · HU-004`
- **Actor:** dueño · **Disparador:** botón Guardar del bloque Puntos.
- **Pasos:** 1. Escribe cuántos pesos equivalen a un punto. 2. Guarda.
- **Resultado:** mensaje de éxito; los puntos de clientes se calculan con el valor nuevo.
- **Reglas de negocio:** pesos por punto mayor a cero.
- **Pantalla / locators:** `configuracion-campo-pesos-por-punto`, `configuracion-boton-guardar-puntos` · **Endpoint:** `PUT /api/v1/configuracion/puntos` · **Sheet:** no usa.

#### CFG-F06: Ver la configuración y estados de la pantalla `vigente · HU-004`
- **Actor:** dueño · **Disparador:** abrir Configuración.
- **Pasos:** 1. La pantalla muestra "cargando". 2. Aparecen los cuatro bloques con los valores guardados. 3. Al guardar un bloque pasa a "guardando" y luego a "guardado" o "error", sin tocar los otros bloques.
- **Reglas de negocio:** un error en un bloque no pierde lo escrito en los demás.
- **Pantalla / locators:** `configuracion-mensaje-exito`, `configuracion-mensaje-error` · **Endpoint:** `GET /api/v1/configuracion`.

### Caminos alternativos y de error
Cada error se muestra en español llano, junto al campo o bloque, diciendo qué pasó y qué hacer (`configuracion-mensaje-error`). Lo que el usuario escribió no se pierde. **Los textos exactos están en `docs/v2/textos.md`.** Los mensajes de esta tabla son una descripción; si no coinciden, manda la biblioteca.

| ID | Situación | Mensaje / comportamiento | Estado |
|---|---|---|---|
| CFG-A01 | Nombre de tienda vacío | "El nombre de la tienda es obligatorio." No guarda. | `vigente · HU-004` |
| CFG-A02 | Logo de más de 2 MB o formato no PNG/JPG/WEBP | "El logo no debe superar los 2 MB." / "El logo debe ser PNG, JPG o WEBP." No cambia el logo. | `vigente · HU-004` |
| CFG-A03 | Link o ID de Sheet inválido o vacío | Error junto al campo del Sheet: el ID o la URL es obligatorio / debe ser una URL de Google Sheets o solo el ID (`configuracion.sheet.error.vacio` / `.invalido`). No guarda. | `propuesto · HU-005` (cambia el tono; antes pedía «pegar») |
| CFG-A04 | La app no tiene acceso al Sheet (no está compartido con la cuenta de servicio) | Error 403 en el bloque Inventario: compartir el Sheet con `tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com` como editor, o usar otro Sheet. | `vigente · HU-004` |
| CFG-A05 | Sesión de Google vencida (en Prueba dura 7 días) | Error 401 en el bloque: volver a iniciar sesión con Google (GOO-F01) y reintentar. | `vigente · HU-004` |
| CFG-A06 | Número inválido o menor o igual a cero (cotización, múltiplo, bulk, pesos por punto) | Error junto al campo: "Debe ser mayor a 0." (en bulk con centavos: "El precio debe ser un número entero, sin centavos."). No guarda el bloque. | `vigente · HU-004` |
| CFG-A07 | Nombre de pestaña del inventario vacío, o la pestaña pedida no existe | Vacío: error junto al campo, «El nombre de la pestaña es obligatorio.». No existe: **ya no se renombra la anterior** (reemplaza el comportamiento de 0.4.0). Si la actual tiene cartas, se crea la nueva copiándolas en el formato de la app y la anterior queda intacta (aviso de copia, CFG-F02). Si la actual está vacía o sin ninguna pestaña, se crea con sus encabezados y se avisa («Se creó la pestaña «X».»). Las demás pestañas no se tocan. | `propuesto · HU-005` |
| CFG-A08 | Carpeta de cache no válida | "La carpeta no existe. Se puede indicar otra o dejar el campo vacío." | `vigente · HU-004` |
| CFG-A09 | Se guardó pero falló la sincronización con Card Kingdom | Aviso de que la configuración quedó guardada pero no se pudo sincronizar ahora; reintentar luego. | `vigente · HU-004` |
| CFG-A10 | No se puede cargar la lista de pestañas (Google falla, sin permiso o sesión vencida) | La lista queda deshabilitada con el error «No se pudieron cargar las pestañas del Sheet.» y el enlace Reintentar. Nombre nuevo y botón deshabilitados. El resto del bloque sigue usable. | `propuesto · HU-005` |
| CFG-A11 | Renombrar sin elegir pestaña, o con nombre nuevo vacío, de más de 100 caracteres o igual al actual | Error junto a la lista («La pestaña a renombrar es obligatoria.») o junto al nombre nuevo (obligatorio / no debe superar los 100 caracteres / igual al actual). No llama a Google. | `propuesto · HU-005` |
| CFG-A12 | Ya existe otra pestaña con el nombre nuevo (sin distinguir mayúsculas) | Error junto al nombre nuevo: «Ya existe una pestaña con ese nombre.». No renombra. | `propuesto · HU-005` |
| CFG-A13 | La pestaña elegida fue borrada en el Sheet | Error de la fila: «La pestaña ya no existe en el Sheet. Se actualizó la lista.». | `propuesto · HU-005` |
| CFG-A14 | Sin permiso (403), sesión vencida (401) o falla inesperada al renombrar | Error de la fila de renombrar con los textos generales (como CFG-A04, CFG-A05). Lo escrito no se pierde. | `propuesto · HU-005` |

## Google: sesión y Sheet (GOO)
> Requisito previo: claves del cliente OAuth en `config/google-oauth.properties` (ver `docs/google/guia-credenciales-oauth.md`). Mientras la app esté en modo Prueba, solo entran los Gmail cargados como usuarios de prueba (hoy `TCGStorehelper@gmail.com`).

### Caminos felices
#### GOO-F01: Iniciar sesión con Google · `vigente` (HU-000)
- **Actor:** dueño del comercio.
- **Precondición:** el Gmail está cargado como usuario de prueba y la app tiene las claves configuradas.
1. El dueño abre la app; aparece la pantalla de login con el botón **Iniciar sesion con Google**.
2. Hace clic en el botón; se abre la página de Google (`/configuracion/google/oauth/iniciar`).
3. Elige el Gmail y escribe su contraseña en la página de Google.
4. Google avisa **"Google no verificó esta app"**; el dueño hace clic en **Configuración avanzada** → **Ir a TCG Inventory (no seguro)**.
5. Acepta el permiso para ver y editar sus hojas de cálculo y hace clic en **Continuar**.
6. Google vuelve a la app (`/configuracion/google/oauth/callback`), que guarda el permiso en la carpeta local `data/` y muestra **"Google conectado. Ya podes usar la app."**
- **Resultado:** la sesión queda iniciada. En modo Prueba, el permiso dura 7 días; después se repite este flujo.

#### GOO-F02: Conectar el Sheet del comercio · `vigente` (HU-000)
- **Actor:** dueño del comercio.
- **Precondición:** GOO-F01 completo; el Sheet pertenece al Gmail conectado o está compartido con él.
1. El dueño entra a **Configuración**.
2. En **Google Sheet del inventario** pega el link o el ID del Sheet y guarda.
3. La app crea las pestañas y columnas que faltan (Inventario, Movimientos, Caja, Reservas, Clientes).
- **Resultado:** la app lee y escribe ese Sheet; un Sheet nuevo queda en blanco con los encabezados.
### Caminos alternativos y de error
_(pendiente)_
