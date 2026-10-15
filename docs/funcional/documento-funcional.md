# Documento funcional: TCG Inventory

- **Versión:** 0.3.0 (la base completa de la v1 pasa a 1.0.0)
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
#### CFG-F01: Guardar identidad de la tienda (nombre y logo) `diseñado · HU-001 → HU-004`
- **Actor:** dueño del comercio · **Disparador:** botón Guardar del bloque Identidad.
- **Precondiciones:** sesión de Google iniciada.
- **Pasos:** 1. Escribe el nombre de la tienda. 2. Opcional: sube un logo (PNG, JPG o WEBP, hasta 2 MB) o marca quitar el logo. 3. Guarda.
- **Resultado:** mensaje de éxito junto al bloque; el nombre y el logo nuevos se ven en la app sin recargar.
- **Reglas de negocio:** el nombre es obligatorio. El logo no puede superar 2 MB. Solo se aceptan PNG, JPG o WEBP.
- **Pantalla / locators:** `configuracion-campo-nombre-tienda`, `configuracion-boton-subir-logo`, `configuracion-boton-guardar-tienda`, `configuracion-mensaje-exito` · **Endpoint:** `PUT /api/v1/configuracion/tienda`, `POST /api/v1/configuracion/tienda/logo` · **Sheet:** no usa.

#### CFG-F02: Conectar el Sheet y elegir la pestaña del inventario `diseñado · HU-001 → HU-004`
- **Actor:** dueño · **Disparador:** botón Guardar del bloque Inventario.
- **Precondiciones:** sesión de Google iniciada; el Sheet es del Gmail conectado o está compartido con él.
- **Pasos:** 1. Pega el link o ID del Sheet. 2. Escribe el nombre de la pestaña del inventario. 3. Guarda. 4. La app verifica el acceso, crea las pestañas y columnas que falten y sincroniza el inventario.
- **Resultado:** el Sheet y la pestaña quedan guardados; mensaje de éxito junto al bloque.
- **Reglas de negocio:** acepta link completo o solo el ID. Ambos campos son obligatorios. Ver GOO-F02 para la conexión inicial.
- **Pantalla / locators:** `configuracion-campo-sheet`, `configuracion-campo-pestana-inventario`, `configuracion-boton-guardar-sheet` · **Endpoint:** `PUT /api/v1/configuracion/sheet` · **Sheet:** pestaña de inventario (y alta de Movimientos, Caja, Reservas, Clientes si faltan).

#### CFG-F03: Renombrar la pestaña única del Sheet `diseñado · HU-001 → HU-004`
- **Actor:** dueño · **Disparador:** guardar el bloque Inventario.
- **Precondiciones:** el Sheet tiene una sola pestaña (ej. «Hoja 1») y no existe una con el nombre pedido (ej. «Inventario»).
- **Pasos:** 1. Escribe «Inventario» como nombre de pestaña. 2. Guarda.
- **Resultado:** la pestaña única se renombra y se muestra el aviso «La pestaña «Hoja 1» se renombró a «Inventario»». El nombre guardado en la configuración es el pedido.
- **Reglas de negocio:** corrige el comportamiento actual (v1), que adopta la pestaña existente y pisa el nombre guardado. Solo se renombra si hay exactamente una pestaña; con varias y sin la pedida, se crea una pestaña nueva (CFG-A07).
- **Pantalla / locators:** `configuracion-mensaje-exito` · **Endpoint:** `PUT /api/v1/configuracion/sheet` (respuesta con la pestaña renombrada) · **Sheet:** nombre de la pestaña única.

#### CFG-F04: Guardar la regla de precio `diseñado · HU-001 → HU-004`
- **Actor:** dueño · **Disparador:** botón Guardar del bloque Precios.
- **Pasos:** 1. Carga la cotización ARS por USD de Card Kingdom. 2. Elige el múltiplo de redondeo. 3. Carga el precio bulk por rareza (common, uncommon, rare, mythic, tierra básica). 4. Opcional: indica la carpeta de cache de Card Kingdom. 5. Guarda.
- **Resultado:** mensaje de éxito; los precios se recalculan con la regla nueva.
- **Reglas de negocio:** cotización, múltiplo y cada precio bulk deben ser mayores a cero. La carpeta de cache debe ser una ruta válida.
- **Pantalla / locators:** `configuracion-campo-cotizacion`, `configuracion-campo-redondeo`, `configuracion-campo-bulk-<rareza>`, `configuracion-boton-guardar-precios` · **Endpoint:** `PUT /api/v1/configuracion/precios` · **Sheet:** no usa.

#### CFG-F05: Guardar la lógica de puntos `diseñado · HU-001 → HU-004`
- **Actor:** dueño · **Disparador:** botón Guardar del bloque Puntos.
- **Pasos:** 1. Escribe cuántos pesos equivalen a un punto. 2. Guarda.
- **Resultado:** mensaje de éxito; los puntos de clientes se calculan con el valor nuevo.
- **Reglas de negocio:** pesos por punto mayor a cero.
- **Pantalla / locators:** `configuracion-campo-pesos-por-punto`, `configuracion-boton-guardar-puntos` · **Endpoint:** `PUT /api/v1/configuracion/puntos` · **Sheet:** no usa.

#### CFG-F06: Ver la configuración y estados de la pantalla `diseñado · HU-001 → HU-004`
- **Actor:** dueño · **Disparador:** abrir Configuración.
- **Pasos:** 1. La pantalla muestra "cargando". 2. Aparecen los cuatro bloques con los valores guardados. 3. Al guardar un bloque pasa a "guardando" y luego a "guardado" o "error", sin tocar los otros bloques.
- **Reglas de negocio:** un error en un bloque no pierde lo escrito en los demás.
- **Pantalla / locators:** `configuracion-mensaje-exito`, `configuracion-mensaje-error` · **Endpoint:** `GET /api/v1/configuracion`.

### Caminos alternativos y de error
Cada error se muestra en español llano, junto al campo o bloque, diciendo qué pasó y qué hacer (`configuracion-mensaje-error`). Lo que el usuario escribió no se pierde. **Los textos exactos están en `docs/v2/textos.md`.** Los mensajes de esta tabla son una descripción; si no coinciden, manda la biblioteca.

| ID | Situación | Mensaje / comportamiento | Estado |
|---|---|---|---|
| CFG-A01 | Nombre de tienda vacío | "Ingresá el nombre de la tienda." No guarda. | `diseñado · HU-001 → HU-004` |
| CFG-A02 | Logo de más de 2 MB o formato no PNG/JPG/WEBP | "El logo no puede superar los 2 MB" / "debe ser PNG, JPG o WEBP". No cambia el logo. | `diseñado · HU-001 → HU-004` |
| CFG-A03 | Link o ID de Sheet inválido o vacío | Error junto al campo del Sheet pidiendo pegar el link completo o el ID. No guarda. | `diseñado · HU-001 → HU-004` |
| CFG-A04 | La app no tiene acceso al Sheet (no está compartido con la cuenta de servicio) | Error 403 en el bloque Inventario: compartir el Sheet con `tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com` como editor, o usar otro Sheet. | `diseñado · HU-001 → HU-004` |
| CFG-A05 | Sesión de Google vencida (en Prueba dura 7 días) | Error 401 en el bloque: volver a iniciar sesión con Google (GOO-F01) y reintentar. | `diseñado · HU-001 → HU-004` |
| CFG-A06 | Número inválido o menor o igual a cero (cotización, múltiplo, bulk, pesos por punto) | Error junto al campo: "debe ser mayor a cero". No guarda el bloque. | `diseñado · HU-001 → HU-004` |
| CFG-A07 | Nombre de pestaña vacío, o el Sheet tiene varias pestañas y no existe la pedida | Vacío: error junto al campo de pestaña (hoy "Ingresa el nombre de la hoja…"). Varias pestañas sin la pedida: se crea la pestaña nueva con sus encabezados y se avisa ("Se creó la pestaña «X»"); las demás no se tocan. | `diseñado · HU-001 → HU-004` |
| CFG-A08 | Carpeta de cache no válida | "La carpeta de cache no es válida." | `diseñado · HU-001 → HU-004` |
| CFG-A09 | Se guardó pero falló la sincronización con Card Kingdom | Aviso de que la configuración quedó guardada pero no se pudo sincronizar ahora; reintentar luego. | `diseñado · HU-001 → HU-004` |

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
