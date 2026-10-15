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
| [HU-003](../historias/HU-003-configuracion-backend.md) | Configuración v2 · backend: contrato `docs/v2/api/configuracion.md`, `ConfiguracionController`, arreglo de la pestaña (renombrar o crear) y sesión vencida (401) | aceptada | HU-002 |
| [HU-004](../historias/HU-004-configuracion-frontend.md) | Configuración v2 · frontend: pantalla según el prototipo, contra la API de HU-003. Se prueba en el navegador sobre el Sheet de pruebas de la dueña | aceptada | HU-001, HU-003 |
| [HU-005](../historias/HU-005-diseno-textos-y-pestanas.md) | Configuración v2 · diseño: tono impersonal y en singular de toda la biblioteca de textos, y bloque del Sheet con "crear pestaña" y "renombrar pestaña" (lista desplegable + nombre nuevo) por separado, y "Traer cartas de" | aceptada | HU-004 |
| [HU-006](../historias/HU-006-configuracion-backend-pestanas.md) | Configuración v2 · backend: contrato y endpoints para listar pestañas (con `sugerida` y `?idOLink` opcional, confirmado) y renombrar una pestaña; `PUT /sheet` con `traerCartasDe`: al crear la pestaña del inventario trae las cartas de la de origen en el formato de la app (columnas por alias, desconocidas al final; la de origen no se toca) y responde `cartasTraidas`; `PUT /sheet` deja de renombrar; textos del back con el tono nuevo. Base: `spec.md` 7.1 | aceptada | HU-005 |
| [HU-007](../historias/HU-007-configuracion-frontend-pestanas.md) | Configuración v2 · frontend: textos nuevos y bloque del Sheet rediseñado, contra la API de HU-006 | aceptada | HU-005, HU-006 |
| [HU-008](../historias/HU-008-configuracion-qa-e2e.md) | Configuración v2 · QA E2E. **Cubre** HU-002 a HU-007: arnés Cucumber + Playwright (`PantallaBase`, `MarcoApp`; carpetas `features/`, `e2e/paginas/`, `e2e/localizadores/`, `e2e/pasos/`), smoke `@critico` y escenarios de los flujos CFG, contra el Sheet de QA | aceptada | HU-007 |
| HU-009 | Configuración v2 · QA E2E de **casos de error** (`qa-casos-error`). **Cubre** HU-004 y HU-007, con CFG-A01 a A09: sesión de Google vencida, Sheet sin permiso, validaciones. Reusa el arnés de HU-008 (ver el límite de Google en `qa-guia.md`) | planificada | HU-008 |

**Progreso:** 8 de 9 historias aceptadas.

> 2026-10-15: al probar HU-004 la dueña pidió el tono impersonal de los textos y separar "crear" y "renombrar" pestaña. Se suman HU-005 (diseño), HU-006 (backend) y HU-007 (frontend); el QA pasa de HU-005 a **HU-008**, al final, para probar todo junto.

> 2026-10-14: QA pasa a su propia historia (HU-005). Se puede postergar sin trabar la épica.

> 2026-10-14: la antigua HU-003 se partió en back (HU-003) y front (HU-004), por la regla nueva de historias separadas para backend y frontend.

## Decisiones y pendientes
- 2026-10-14: el bug de la pestaña se arregla en la v2 (HU-003), no en la v1.
- 2026-10-14: con varias pestañas y sin la pedida, se crea la pestaña nueva y se avisa.
- 2026-10-14: **conexión decidida (modelo mixto):** login con el Gmail del usuario; lectura y escritura del Sheet con la cuenta de servicio `tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com`; sincronización inmediata. La clave ya está en `config/google-service-account.json` (la colocó la dueña). Detalle y Sheets de QA y de pruebas en `estado.md`, Google, punto 7.
- 2026-10-14: QA E2E en su propia historia (HU-005), contra el Sheet de QA. HU-003 se valida con tests de API y con Postman de la dueña; HU-004, en el navegador sobre su Sheet de pruebas.

## Para retomar
Sigue: **HU-009** (QA de casos de error, planificada; falta el archivo: brief + OK). HU-008 aceptada el 2026-10-15: arnés E2E (Cucumber + Playwright) y 11 escenarios del camino feliz en verde contra el Sheet de QA. Antes, HU-007: pantalla con textos nuevos y bloque del Sheet en dos filas; todos los flujos CFG están `vigente` (documento funcional 0.5.0).
- **Tono aprobado (2026-10-15):** impersonal, en singular, sin hablarle al usuario; sigue diciendo cómo va el campo. Ej.: "El nombre de la tienda es obligatorio.", "El logo no debe superar los 2 MB.", "No se pudo cargar la configuración.", "Se acepta la URL completa o solo el ID.".
- **Bloque del Sheet aprobado en idea:** arriba "ID o URL del Sheet" + "Pestaña del inventario" (se crea si no existe; la primera, si el Sheet no tiene ninguna); abajo "Pestaña a renombrar" (lista desplegable con las pestañas del Sheet) + "Nombre nuevo" con su botón.
- **Flujos:** CFG-F01..F06 y CFG-A01..A09 están `vigente` (HU-004). HU-005 llama al analista para ajustar CFG-F02, CFG-F03 y CFG-A07 y sumar el flujo de renombrar.
