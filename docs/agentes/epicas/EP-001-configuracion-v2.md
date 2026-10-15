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
| [HU-002](../historias/HU-002-base-tecnica-v2.md) | Base técnica v2 (React en Maven, OpenAPI, errores, perfil e2e, arnés Cucumber + Playwright con 1 smoke) | aceptada | — |
| HU-003 | Configuración v2 · backend: contrato `docs/v2/api/configuracion.md`, `ConfiguracionController`, arreglo de la pestaña (renombrar o crear) y sesión vencida (401) | planificada | HU-002 |
| HU-004 | Configuración v2 · frontend: pantalla según el prototipo, contra la API de HU-003. Se prueba en el navegador sobre el Sheet de pruebas de la dueña | planificada | HU-001, HU-003 |
| HU-005 | Configuración v2 · QA E2E. **Cubre** HU-002, HU-003 y HU-004: arnés Cucumber + Playwright (`PantallaBase`, `MarcoApp`), smoke `@critico` (heredado de HU-002) y escenarios de los flujos CFG-F01..F06 y CFG-A01..A09, contra el Sheet de QA | planificada | HU-004 |

**Progreso:** 2 de 5 historias aceptadas.

> 2026-10-14: QA pasa a su propia historia (HU-005). Se puede postergar sin trabar la épica.

> 2026-10-14: la antigua HU-003 se partió en back (HU-003) y front (HU-004), por la regla nueva de historias separadas para backend y frontend.

## Decisiones y pendientes
- 2026-10-14: el bug de la pestaña se arregla en la v2 (HU-003), no en la v1.
- 2026-10-14: con varias pestañas y sin la pedida, se crea la pestaña nueva y se avisa.
- 2026-10-14: **conexión decidida (modelo mixto):** login con el Gmail del usuario; lectura y escritura del Sheet con la cuenta de servicio `tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com`; sincronización inmediata. La clave ya está en `config/google-service-account.json` (la colocó la dueña). Detalle y Sheets de QA y de pruebas en `estado.md`, Google, punto 7.
- 2026-10-14: QA E2E en su propia historia (HU-005), contra el Sheet de QA. HU-003 se valida con tests de API y con Postman de la dueña; HU-004, en el navegador sobre su Sheet de pruebas.

## Para retomar
La próxima es **HU-003** (Configuración · backend). Con `/agente-lider continuar HU-003`, el líder arma el brief desde esta épica, espera el OK, crea la rama `v2/feature/HU-003-<tema>` desde `v2/dev` y llama a `backend-java`.
- **Flujos:** ya están documentados (CFG-F01 a F06 y CFG-A01 a A09, en `diseñado · HU-001 → HU-004`). HU-003 los reusa, sin llamar al analista.
- **Validación de HU-003:** tests de API, más la dueña con Postman sobre su Sheet de pruebas (`1vSk7…`).
