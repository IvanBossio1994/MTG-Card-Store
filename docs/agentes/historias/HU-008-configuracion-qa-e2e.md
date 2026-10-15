# HU-008: Configuración v2 · QA E2E

- **Estado:** aceptada
- **Épica:** EP-001
- **Tipo:** QA
- **Cubre:** `HU-002, HU-003, HU-004, HU-005, HU-006, HU-007`
- **Módulo:** configuracion
- **Rama:** `v2/feature/HU-008-configuracion-qa-e2e`
- **QA elegido:** feliz; alcance `@HU-004 or @HU-007 or @critico` (casos de error en otra vuelta)
- **Funcional documentado:** reusa los de HU-004 a HU-007
- **Flujos funcionales:** CFG-F01, CFG-F02, CFG-F03, CFG-F04, CFG-F05, CFG-F06

## Historia
**Como** dueño del comercio
**quiero** que la pantalla de Configuración se pruebe sola, de punta a punta, en un navegador real
**para** saber que sigue funcionando cada vez que se cambia algo, sin tener que probarla a mano

## Descripción
El arnés E2E nunca se armó: se postergó en HU-002 y hoy `pom.xml` no tiene Cucumber ni Playwright y no hay ningún `.feature`. Esta HU lo arma desde cero según `docs/agentes/qa-guia.md` y `docs/agentes/estructura-carpetas.md` (`PantallaBase` liviana, `MarcoApp`; carpetas `features/`, `e2e/paginas/`, `e2e/localizadores/`, `e2e/pasos/`), con un smoke `@critico`. Después, `qa-camino-feliz` escribe los escenarios de los flujos CFG-F01 a CFG-F06.
Los escenarios corren contra el **Sheet de QA** (`1wWH5bJV3kqXHyLK4zorxnGcGsZ_bRHrKXmn4DGgrG2I`) con la cuenta de servicio, sin login de Gmail (`estado.md`, Google, punto 7). Locators: `docs/v2/locators.md`; textos: `docs/v2/textos.md`.

## Criterios de aceptación
**CA1: Smoke automático**
- **Given** el arnés de Cucumber y Playwright
- **When** se corre `mvn.cmd -q test -Dcucumber.filter.tags="@critico"`
- **Then** la app arranca sola, abre "Configuración" en el navegador y el escenario pasa

**CA2: Sheet de QA limpio**
- **Given** un escenario que crea o renombra pestañas en el Sheet de QA
- **When** termina (pase o falle)
- **Then** el Sheet de QA queda como estaba antes, y el escenario siguiente no depende del anterior

**CA3: Caminos felices de Configuración**
No se copian acá. QA lee de las historias de pantalla qué probar y, al pasarlo al `.feature`, puede reescribir o ajustar los criterios si hace falta (por ejemplo, para que un escenario sea independiente o para usar el texto vigente). Se completan con los flujos CFG-F01 a F06 del documento funcional:
- `HU-004-configuracion-frontend.md` → escenarios con `@HU-004`;
- `HU-007-configuracion-frontend-pestanas.md` → escenarios con `@HU-007` (manda sobre HU-004 donde cambiaron textos o el bloque del Sheet).
Las HU de back (HU-003, HU-006) ya están cubiertas por sus tests de API; las de diseño y base técnica no llevan escenarios propios.

## Fuera de alcance
- Casos de error (sesión de Google vencida, Sheet sin permiso, validaciones): otra vuelta de QA.
- Cambios de funcionalidad en back o front (si aparecen, van como `DEF-XXX`).

## Tareas (en orden)
| # | Agente | Tarea | Listo cuando |
|---|---|---|---|
| 1 | backend-java | Arnés E2E: dependencias de Cucumber y Playwright Java en `pom.xml` (sin romper `mvn test` de back y front), runner, arranque de la app para los E2E, `PantallaBase` y `MarcoApp`, acceso al Sheet de QA y limpieza antes de cada escenario (CA2), y el smoke `@critico` (CA1). Documentar cómo correrlo en `qa-guia.md` | Compila, pasan los tests existentes y pasa `@critico` |
| 2 | qa-camino-feliz | Escenarios a partir de los CA de HU-004 y HU-007 (CA3) y los flujos CFG-F01 a F06, en `features/configuracion.feature`, con el tag de la HU de origen. Ejecutar `@HU-004 or @HU-007 or @critico` | Veredicto y defectos |

## Consumo
| Agente | Llamada | Tokens |
|---|---|---|
| backend-java | Tarea 1 (arnés + smoke) | 156.944 |
| qa-camino-feliz (agente general, Sonnet) | Tarea 2 (camino feliz) | 160.864 |
| **Total** | | **317.808** |

## Registro
| Fecha | Paso | Resultado |
|---|---|---|
| 2026-10-15 | Brief aprobado | Arnés + camino feliz. Hoy solo el arnés (sesión al 60%); el camino feliz, en la próxima ventana |
| 2026-10-15 | Tarea 1 entregada (backend-java) | Arnés listo: Cucumber 7.23 + Playwright 1.63 con perfil Maven `escenarios-e2e` (se activa con `-Dcucumber.filter.tags`); la app arranca sola contra el Sheet de QA; `SheetDeQa` lo restaura después de cada escenario (CA2). Usa el Chrome instalado. Smoke `@critico` pasa (verificado también por el líder) y `mvn test` completo OK. Pendientes: la capa de carga no tiene `data-testid` (propuesta `app-capa-carga`); el Sheet de QA hoy es una sola "Hoja 1" vacía |
| 2026-10-15 | Ajuste del líder | `data-testid="app-capa-carga"` en `CapaCarga.tsx`; el arnés lo usa en lugar del gatito; sumado a `locators.md`. Smoke pasa |
| 2026-10-15 | Tarea 2 lanzada | `qa-camino-feliz` no está cargado en la sesión: se lanza un agente general (Sonnet) que sigue `.claude/agents/qa-camino-feliz.md`. Alcance `@HU-004 or @HU-007 or @critico` |
| 2026-10-15 | Tarea 2 entregada | ✅ cumple: 11 escenarios (CFG-F01 a F06, con el smoke) pasan; sin DEF. Arnés ajustado por el límite de Google (60 lecturas por minuto): reintento ante 429 en `SheetDeQa` y mínimo 15 s por escenario (`GanchosE2E`); corrida ≈ 2,5 min. Sin cubrir: la capa "Trayendo las cartas…" (transitoria) y títulos de bloque sin `data-testid` (se ubican por rol). El líder re-corrió los E2E y `mvn test` completo: OK. Pasa al cierre |
| 2026-10-15 | **Aceptada** | La dueña cierra acá (sesión al 81%). Los casos de error pasan a HU-009 (planificada en EP-001) |
