# EP-001: Configuración v2

- **Estado:** abierta
- **Módulo:** configuracion
- **Fase del roadmap:** 1 (base) y 2 (Configuración + sesión Google)

## Objetivo
Pasar la pantalla de Configuración a la v2 con un diseño moderno, sin recargas y con mensajes claros. De paso:
- arreglar el bug de la pestaña del inventario, que no se renombra a "Inventario";
- dejar armada la base técnica (React, API y pruebas) que van a usar las demás pantallas.

## Caso de uso
**Actor:** dueño del comercio.
1. Entra a Configuración.
2. Carga o cambia los datos de la tienda (nombre y logo), el Sheet y su pestaña, la regla de precio y los puntos.
3. Guarda cada bloque por separado y ve el resultado al lado del botón, sin recargar la pantalla.
4. Si conecta un Sheet con una sola pestaña, la app la renombra. Si tiene varias y falta la pedida, la crea.

Flujos detallados: `docs/funcional/documento-funcional.md`, sección CFG (CFG-F01 a F06 y CFG-A01 a A09).

## Alcance
- **Entra:**
  - el diseño de la pantalla;
  - la base técnica de la v2;
  - la API `/api/v1/configuracion` y la pantalla programada;
  - el arreglo del bug de la pestaña;
  - el aviso de sesión de Google vencida.
- **No entra:** el tutorial, las demás pantallas y cualquier cambio en la v1.

## Historias
| HU | Título | Estado | Depende de |
|---|---|---|---|
| [HU-001](../historias/HU-001-diseno-configuracion.md) | Diseño nuevo de Configuración | aceptada | — |
| HU-002 | Base técnica v2 (React en Maven, OpenAPI, errores, perfil e2e, arnés Cucumber + Playwright con 1 smoke) | planificada | — |
| HU-003 | Configuración v2 funcionando (API + pantalla + arreglo de la pestaña + sesión vencida) | planificada | HU-001, HU-002 |

**Progreso:** 1 de 3 historias aceptadas.

## Decisiones y pendientes
- 2026-10-14: el bug de la pestaña se arregla en la v2 (HU-003), no en la v1.
- 2026-10-14: con varias pestañas y sin la pedida, se crea la pestaña nueva y se avisa.
- **Pendiente (HU-003):** conexión al Sheet por OAuth o por la cuenta de servicio `tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com` (ver `estado.md`, Google, punto 7).
