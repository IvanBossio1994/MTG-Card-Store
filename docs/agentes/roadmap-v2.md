# Roadmap v2

> Es una guía para los agentes. La v2 se construye en ramas propias, por módulos, y cada fase es una o varias historias de usuario. Mientras tanto, la v1 sigue funcionando.

## Por qué
- `DashboardController` tiene unas 9000 líneas y mezcla pantallas con lógica.
- `GoogleSheetsService` tiene unas 2100.
- `app.js` (3200) y `app.css` (4100) son un solo archivo cada uno.
- No hay una API clara ni locators para automatizar.

**Objetivo:** API REST real en español, frontend moderno, contratos claros y pruebas automatizadas de los flujos críticos.

## Decisiones técnicas
| Tema | Decisión | Por qué |
|---|---|---|
| Frontend | React + TypeScript + Vite en `frontend/` | Es el estándar en el mercado y forma parte del ecosistema Node. |
| Node en el local | No hace falta: `frontend-maven-plugin` descarga Node al compilar y el build queda dentro del `.jar`. | La app de Windows y `DesktopLauncher` siguen igual (`localhost:8080`). |
| API | REST `/api/v1/...` en español, DTOs `record` + `@Valid`, errores `ProblemDetail` | Contratos reales. |
| Contrato | springdoc-openapi (`/swagger-ui`). El front genera sus tipos con `openapi-typescript`. | Si cambia el contrato, el front deja de compilar. |
| Datos | Un `XRepository` por módulo, con implementación Sheets (real) y EnMemoria (perfil `e2e`) | QA no depende de Google ni de internet, y se puede desarrollar sin credenciales. |
| E2E | Cucumber-JVM (Gherkin en español) + Playwright Java + JUnit 5, con Maven | Mismo stack que conoce la persona a cargo (Java, Maven, data-testid). Espera sola a los elementos, así hay menos pruebas que fallan al azar que con Selenium. |
| Capas de test | Unitarias (servicios) → API (MockMvc) → E2E (flujos críticos) → Vitest (front) | Pirámide de tests. |
| Locators | `data-testid="<pantalla>-<elemento>[-<id>]"` (`docs/v2/locators.md`) | Contrato entre UX, front y QA. |
| Nombres | El negocio en español; los sufijos de Spring en inglés | Decisión de la persona a cargo. |

## Arquitectura backend (`com.tcg.bot`)
```
compartido/    errores (ProblemDetail), utilidades, configuración OpenAPI
google/        OAuth + cliente de Sheets de bajo nivel
inventario/    InventarioController, InventarioService, InventarioRepository (+Sheets, +EnMemoria), dto/
precios/       Card Kingdom (cache sincronizado y escrito de forma atómica)
reservas/      reservas, retiros, pedido masivo, clientes
caja/          ventas y reportes mensuales
movimientos/   historial de stock y acceso con clave
importacion/   importar lista
configuracion/ comercio, logo, tutorial
```

- **Patrón:** `Controller` (HTTP) → `Service` (negocio) → `Repository` (datos).
- **En la migración se corrigen los P1:**
  - verificar la clave antes de escribir en una fila;
  - lock en las escrituras;
  - descarga de Card Kingdom sincronizada, con archivo temporal y movimiento atómico.
- **Convivencia:** la v1 sigue en `/` y la v2 en `/app`. En el corte, la v2 pasa a `/` y se borra lo viejo.

## Fases
| # | Fase | Resultado |
|---|---|---|
| 0 | HU-000: documento funcional base | `analista-funcional` documenta todo lo que hace la v1 (los 37 endpoints y sus flujos felices y alternativos) en `docs/funcional/documento-funcional.md`, que pasa a la versión 1.0.0. Es la base de la regresión. |
| 1 | Base | Paquetes, OpenAPI, errores, perfil `e2e`, `frontend/` integrado a Maven, harness Cucumber + Playwright con 1 smoke y sistema de diseño UX. |
| 2 | Configuración + sesión Google | API y pantalla. Detecta el token vencido. |
| 3 | Inventario + búsqueda + precios CK | El núcleo del mostrador. |
| 4 | Reservas + clientes + retiros + pedido masivo | |
| 5 | Movimientos + caja + reportes mensuales | |
| 6 | Importar lista + tutorial | |
| 7 | Corte | Borrar la v1, `/app` pasa a `/`, actualizar el build de Windows y correr la regresión completa. |

## Criterios de la fase 1 (para cuando se haga)
- `mvn -q test` corre las pruebas unitarias, de API y el smoke de Cucumber + Playwright (abre `/app` con el perfil `e2e` y encuentra `app-titulo`).
- `/swagger-ui` muestra la API.
- El `.jar` sirve el frontend sin Node instalado.
