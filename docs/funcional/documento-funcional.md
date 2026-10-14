# Documento funcional: TCG Inventory

- **Versión:** 0.1.0 (esqueleto; la base de la v1 se documenta en HU-000 y pasa a 1.0.0)
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
### Caminos felices
_(pendiente)_
### Caminos alternativos y de error
_(pendiente)_
