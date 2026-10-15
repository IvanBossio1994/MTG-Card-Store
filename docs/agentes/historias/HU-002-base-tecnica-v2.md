# HU-002: Base técnica v2

- **Estado:** aceptada
- **Épica:** [EP-001](../epicas/EP-001-configuracion-v2.md)
- **Tipo:** mejora técnica
- **Módulo:** compartido
- **Rama:** `v2/feature/HU-002-base-tecnica-v2`
- **QA elegido:** (lo completa el líder al llegar a QA)
- **Funcional documentado:** no aplica (no cambia flujos de negocio; el marco de la app ya está en el sistema de diseño)
- **Flujos funcionales:** —

## Historia
**Como** dueña del comercio
**quiero** que la v2 tenga su base lista (pantalla nueva en `/app`, API documentada y pruebas automáticas que corren sin Google)
**para** poder programar Configuración (HU-003) y los demás módulos sobre algo sólido, sin romper la v1

## Descripción
Hoy `pom.xml` tiene solo web, Thymeleaf y Google. No hay React, OpenAPI ni Cucumber/Playwright, y `frontend/src/` tiene solo carpetas vacías. Esta HU arma la fase 1 del roadmap (`roadmap-v2.md`):
- **Backend:**
  - errores estándar `ProblemDetail` en español;
  - springdoc (`/swagger-ui`);
  - `GET /api/v1/estado-app`;
  - perfil `e2e` sin Google ni internet;
  - servir el frontend en `/app`.
- **Frontend:**
  - Vite + React + TypeScript en `frontend/`, compilado por Maven con `frontend-maven-plugin` (no hace falta tener Node instalado);
  - Tailwind + shadcn/ui + Lucide con los tokens de `docs/v2/ux/sistema-diseno.md`;
  - el **marco de la app** (barra lateral) de la pantalla de referencia;
  - el archivo de textos;
  - Vitest.
- **E2E:**
  - arnés Cucumber + Playwright Java según `docs/agentes/qa-guia.md`;
  - 1 smoke `@critico`.

La v1 sigue igual en `/`. `DesktopLauncher` no cambia.

## Criterios de aceptación
**CA1: Compila sin Node instalado**
- **Given** una PC con JDK 21 y sin Node
- **When** se corre `mvn.cmd -q package`
- **Then** el build termina OK y el `.jar` incluye el frontend compilado

**CA2: Marco de la app en /app**
- **Given** la app levantada con el perfil `e2e`
- **When** abro `/app`
- **Then** veo la barra lateral con el logo, el nombre de la tienda y el menú Inventario, Reservas, Caja, Movimientos y Configuración
- **And** el estilo coincide con el prototipo de referencia (`docs/v2/ux/configuracion/prototipo.html`): colores, tipografía, medidas e íconos

**CA3: La v1 sigue andando**
- **Given** la app levantada
- **When** abro `/`
- **Then** veo la pantalla actual de la v1, sin cambios

**CA4: EstadoApp de la API**
- **Given** la app levantada
- **When** pido `GET /api/v1/estado-app`
- **Then** responde 200 con `{ "estado": "ok", "version": "...", "modo": "prueba" | "normal" }`

**CA5: Error estándar en español**
- **Given** la app levantada
- **When** pido una ruta que no existe, por ejemplo `GET /api/v1/no-existe`
- **Then** responde 404 con un `ProblemDetail` cuyo `detail` está en español

**CA6: API documentada**
- **Given** la app levantada
- **When** abro `/swagger-ui`
- **Then** veo `GET /api/v1/estado-app` documentado, con ejemplo

**CA7: Modo de prueba sin Google ni internet**
- **Given** la app levantada con el perfil `e2e`
- **When** arranca
- **Then** no se conecta a Google ni descarga la lista de precios de Card Kingdom

**CA8: Smoke automático** (pasa a la HU de QA de la épica, HU-005, junto con el arnés)
- **Given** el arnés de Cucumber y Playwright
- **When** corro `mvn.cmd -q test -Dcucumber.filter.tags="@critico"`
- **Then** el escenario de humo abre `/app`, encuentra el menú lateral por `data-testid` y pasa

## Locators (`data-testid`)
Ya están en el catálogo (desde HU-001); acá se implementan por primera vez:

| data-testid | Elemento |
|---|---|
| `app-enlace-inventario` | Menú lateral: Inventario |
| `app-enlace-reservas` | Menú lateral: Reservas |
| `app-enlace-caja` | Menú lateral: Caja |
| `app-enlace-movimientos` | Menú lateral: Movimientos |
| `app-enlace-configuracion` | Menú lateral: Configuración |

## Contrato de API
El contrato completo lo escribe `backend-java` en `docs/v2/api/compartido.md`, **antes de programar**.

| Método | Ruta | Request | Respuesta OK | Errores |
|---|---|---|---|---|
| GET | `/api/v1/estado-app` | — | 200 `{ estado: "ok", version, modo }` | — |
| (cualquiera) | ruta inexistente bajo `/api/v1` | — | — | 404 `ProblemDetail` en español |

## Fuera de alcance
- Endpoints y pantallas de negocio: Configuración va en HU-003.
- Tocar la v1, el `DesktopLauncher` o el build de Windows.
- Generar los tipos del front desde OpenAPI (`openapi-typescript`): queda preparado el script; se usa desde HU-003.
- Sacarle a `BackendApplicationTests` (v1) la descarga real de Card Kingdom.

## Tareas por agente (en orden)
| # | Agente | Tarea | Listo cuando |
|---|---|---|---|
| 1 | backend-java | Contrato en `docs/v2/api/compartido.md`. En `com.tcg.bot.compartido`: `ProblemDetail` en español, springdoc y `GET /api/v1/estado-app`. Perfil `e2e` (`application-e2e.properties`: sin Google, sin sincronización programada ni descarga de CK). Servir `/app/**` con fallback a `index.html`. Tests MockMvc de CA4 y CA5. (El arnés E2E se postergó: ver tarea 3.) | Compila; pasan los tests unitarios, los de API y los de la v1 |
| 2 | frontend | Estructura por módulos de `docs/agentes/estructura-carpetas.md`: `app/`, `compartido/` y `modulos/`; borrar las carpetas vacías viejas (`api`, `pages`, `components`, `hooks`, `styles`). Proyecto Vite + React + TS en `frontend/`, conectado al build de Maven con `frontend-maven-plugin`. Tailwind + shadcn/ui + Lucide con los tokens del sistema de diseño. Componente **Marco de app** (barra lateral, locators `app-enlace-*`) igual al prototipo de referencia. `frontend/src/compartido/textos/` con las claves `menu.*` de `docs/v2/textos.md`. Vitest con 1 test del marco. | `mvn.cmd -q package` OK sin Node instalado; Vitest OK; `/app` muestra el marco |
| 3 | — | **Se pasa a HU-004** (regla nueva: QA E2E solo en historias de frontend conectado). El texto queda como referencia: **Solo si la dueña decide ejecutar QA.** Primero `backend-java` arma el arnés Cucumber + Playwright según `qa-guia.md` (runner, hooks que levantan la app con `@SpringBootTest` en puerto al azar y perfil `e2e`, navegador). Después QA escribe el smoke `@HU-002 @critico @regresion` (abrir `/app` y ver el menú lateral) y un escenario de API para `/api/v1/estado-app`. Si no se ejecuta, la HU queda en `en QA` con este contexto guardado. | Veredicto y defectos reportados |

## Consumo
| Agente | Llamada | Tokens |
|---|---|---|
| backend-java | Tarea 1 (sin arnés) | 99.003 |
| frontend | Tarea 2 | 88.492 |
| **Total** | | **187.495** |

## Registro
| Fecha | Paso | Resultado |
|---|---|---|
| 2026-10-14 | Brief aprobado | Base técnica de la épica EP-001. Sin documento funcional: no hay flujos de negocio. Gherkin con palabras clave en inglés. |
| 2026-10-14 | Alcance | La dueña posterga el arnés E2E: se arma recién cuando decida ejecutar QA (tarea 3). CA8 depende de eso. |
| 2026-10-14 | Backend | Contrato `docs/v2/api/compartido.md`, errores ProblemDetail en español, springdoc, perfil `e2e` (sin legacy ni Google) y `/app` con fallback. Compila y pasan los tests (v1 incluida). |
| 2026-10-14 | Ajuste (líder) | Renombre `/api/v1/salud` → `/api/v1/estado-app` (`EstadoAppController`, `EstadoAppService`, `EstadoAppRespuesta`), por nombres descriptivos. Tests OK. Reglas nuevas: contrato primero, nombres descriptivos y Sheet real nunca en pruebas. Backlog: E2E con Sheet temporal. |
| 2026-10-14 | Frontend | Vite, React, TS, Tailwind 4, shadcn, Lucide y Sonner; `frontend-maven-plugin` (Node 22). `MarcoApp` igual al prototipo y estructura `app/`, `compartido/`, `modulos/`. `mvn package` OK y Vitest 4/4. Se suman 2 textos a `textos.md`. |
| 2026-10-14 | Verificación (líder) | App en perfil `e2e`, puerto 8765: `/app/configuracion` muestra el marco; `/api/v1/estado-app` responde 200; `/api/v1/no-existe` responde 404 con ProblemDetail en español; `/swagger-ui` muestra "Estado de la app". Pasa a `en QA`. |
| 2026-10-14 | QA postergado | La dueña deja la HU en `en QA` hasta que haya agentes de QA disponibles (arnés + smoke + escenario de `estado-app`). |
| 2026-10-14 | Ajuste (líder) | `pom.xml`: propiedad `app.clase-principal`, para levantar la app sin la ventana de `DesktopLauncher` (antes se abría y apagaba la app al cerrarse). Regla nueva: HU separadas para backend y frontend; la vieja HU-003 se parte en HU-003 (back) y HU-004 (front). |
| 2026-10-14 | Simplificación (líder) | Back: se elimina `EstadoAppService` (sin reglas de negocio) y el `dto/`; la lógica y el `record` quedan en `EstadoAppController`, y los tests se juntan en un solo archivo. Front: `App.tsx` se integra en `main.tsx` y se borran 5 carpetas vacías. `mvn package` OK. Regla 7 nueva en `estructura-carpetas.md`: nada de archivos ni carpetas "porque sí". |
| 2026-10-14 | Reglas (líder) | Reglas 8 y 9 de `estructura-carpetas.md`: lo común por composición (en QA, `PantallaBase` liviana; en back, solo la base de acceso al Sheet) y código moderno en Java 21, React + TS y QA. Cuando se arme el arnés, QA arranca con `PantallaBase` y `MarcoApp`. |
| 2026-10-14 | Ajuste (líder) | En `estado-app`, el campo `perfil` (término de Spring) pasa a `modo`: `"prueba"` o `"normal"`. `PerfilE2EConfig` y `PerfilE2ETest` pasan a `ModoPruebaConfig` y `ModoPruebaTest`. Contrato actualizado; tests OK. |
| 2026-10-14 | Regla QA | Base técnica y backend no llevan QA E2E; se hace en la HU de frontend conectado. El arnés y el smoke (CA8) pasan a HU-004. HU-002 va al cierre. |
| 2026-10-14 | Cierre | **Aceptada por la dueña.** Commit, push y merge a `v2/dev` autorizados. |
