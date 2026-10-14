# Documento funcional: TCG Inventory

- **Versión:** 0.2.0 (la base completa de la v1 pasa a 1.0.0)
- **Mantiene:** `analista-funcional` (contenido) y el líder (versión e historial)

> **Cómo leerlo (agentes):** buscá con Grep el título del módulo (por ejemplo `## Reservas`) y leé solo esa sección. No leas el documento entero.
>
> **Estados de un flujo:**
> - `vigente`: así funciona hoy.
> - `propuesto · HU-XXX`: en desarrollo.
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
### Caminos felices
_(pendiente)_
### Caminos alternativos y de error
_(pendiente)_

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
