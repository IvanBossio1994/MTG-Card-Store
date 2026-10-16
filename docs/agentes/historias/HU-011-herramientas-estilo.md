# HU-011: Herramientas de estilo en el build

- **Estado:** aceptada
- **Épica:** EP-002
- **Tipo:** base técnica
- **Módulo:** compartido
- **Rama:** `v2/feature/HU-011-herramientas-estilo`
- **Funcional documentado:** no aplica
- **Flujos funcionales:** —

## Historia
**Como** persona a cargo del proyecto
**quiero** que el build revise y corrija solo el estilo del código de la v2
**para** que todo se escriba igual, sin importar qué agente lo programe

## Descripción
La guía `docs/agentes/estilo-codigo.md` (HU-010) ya define el estilo, y su sección 4 lista las herramientas. Esta HU las suma al build de Maven, solo para la v2 (`legacy` queda afuera):
- **Java:** Spotless + palantir-java-format (`apply` en `package`, `check` en `verify`), ArchUnit (test de estructura) y `.editorconfig` (imports `*` desde 5 clases, para IntelliJ).
- **Front:** ESLint (typescript-eslint + reglas de hooks) y Prettier, que corren desde `frontend-maven-plugin` antes de `vite build`.
No hay refactor: lo que hoy rompa una regla y necesite un refactor se marca como excepción con el comentario `HU-012` (back) o `HU-013` (front), y se arregla en esas historias. Archivos: `pom.xml`, `frontend/package.json`, configuraciones nuevas en la raíz y en `frontend/`, y un test de ArchUnit en `src/test/java/com/tcg/bot/`.

## Criterios de aceptación
**CA1: el build formatea el Java de la v2**
- **Given** un archivo Java de la v2 con un formato distinto al de la guía
- **When** se ejecuta `mvn package`
- **Then** el archivo queda con el formato de palantir-java-format
- **And** ningún archivo de `com.tcg.bot.legacy` cambia

**CA2: la verificación falla si el formato no cumple**
- **Given** un archivo Java de la v2 sin formatear
- **When** se ejecuta `mvn verify` sin pasar antes por `apply`, o `mvn spotless:check`
- **Then** el build falla e indica el archivo

**CA3: ArchUnit hace cumplir la estructura**
- **Given** una clase de la v2 que importa algo de `legacy`, usa `@Autowired` en un campo o se llama `*Utils`, `*Helper` o `*Manager`, o un módulo con dos controllers
- **When** se corren los tests
- **Then** el test de arquitectura falla y nombra la regla y la clase

**CA4: ESLint y Prettier corren en el build del front**
- **Given** un archivo del front con un error de hooks o mal formateado
- **When** se ejecuta `mvn package`
- **Then** Prettier corrige el formato
- **And** ESLint hace fallar el build si hay un error

**CA5: el proyecto queda en verde**
- **Given** el código actual de la v2, ya formateado y con las excepciones marcadas `HU-012`/`HU-013`
- **When** se ejecuta `mvn.cmd -q package`
- **Then** compila y pasan los tests de back y Vitest

## Fuera de alcance
- La v1 (`legacy`): no se formatea ni se revisa.
- Refactors de Configuración (HU-012 back, HU-013 front) y librerías nuevas del front (TanStack Query, `user-event`, MSW).
- Estilo de QA (HU-014).

## Tareas (en orden)
Un agente nuevo por tarea.

| # | Agente | Tarea | Listo cuando |
|---|---|---|---|
| 1 | backend-java | Spotless + palantir-java-format (solo la v2), `.editorconfig` en la raíz (Java y front, imports `*` desde 5) y una pasada de formato al Java de la v2, sin cambios de lógica | `mvn.cmd -q package` en verde; CA1 y CA2 probados con un archivo de prueba que después se descarta |
| 2 | backend-java | Test de ArchUnit con las reglas de CA3 (excepciones actuales marcadas `HU-012`) | Tests en verde; cada regla probada a mano para ver que falla |
| 3 | backend-java | ESLint (typescript-eslint + react-hooks) y Prettier en `frontend/`, con scripts `lint` y `format` corriendo desde Maven antes de `vite build`; formato aplicado al front actual (excepciones marcadas `HU-013`) | `mvn.cmd -q package` en verde; CA4 probado |
| 4 | líder | Verificar CA5 y ajustar la sección 4 de `estilo-codigo.md` si algo no se aplicó tal cual | Guía al día |

## Consumo
| Agente | Llamada | Tokens | % ventana (inicio → fin) |
|---|---|---|---|
| backend-java | 1 (tarea 1: Spotless) | 42.425 | 11% → 11% (aviso con atraso) |
| backend-java | 1 (tarea 2: ArchUnit) | 56.225 | 13% → 16% |
| backend-java | 1 (tarea 3: ESLint + Prettier) | 76.804 | 16% → 20% |
| líder | tarea 4 (verificación y guía) | — | 20% → 21% |
| **Total** | | 175.454 | 11% → 21% (~10 puntos) |

## Registro
| Fecha | Paso | Resultado |
|---|---|---|
| 2026-10-15 | Brief aprobado | HU armada desde EP-002, 3 tareas de `backend-java` + verificación del líder |
| 2026-10-15 | Tarea 1 (backend-java) | Spotless 3.10.4 + palantir-java-format 2.102.0 (apply en `process-sources`, check en `verify`; `-Dspotless.apply.skip=true` para revisar sin corregir), `.editorconfig`, 37 Java de la v2 formateados, legacy sin cambios. CA1 y CA2 probados con archivo temporal (borrado). `mvn -q package` en verde |
| 2026-10-15 | Tarea 2 (backend-java) | ArchUnit 1.5.1 (`archunit-junit5`) + `ArquitecturaV2Test` (solo producción de la v2, sin legacy ni tests): 4 reglas (no usa legacy, sin inyección por campo, sin `*Utils`/`*Helper`/`*Manager`, un controller por módulo). Sin excepciones `HU-012`: el código actual cumple. Cada regla probada a mano con clases temporales (borradas): falla nombrando regla y clase. `mvn -q clean package` en verde |
| 2026-10-15 | Tarea 3 (backend-java) | ESLint 10.12 (flat, typescript-eslint 8.71 `recommendedTypeChecked`, react-hooks 7.1 `flat.recommended`, eslint-comments 4.8: solo `eslint-disable-next-line` con motivo, `reportUnusedDisableDirectives: error`) + Prettier 3.9 (`.prettierrc.json` = `.editorconfig`: 2 espacios, 100 col.); `format` y `lint` en Maven antes de `build`. Node 22.12 → 22.20 (ESLint 10 lo pide). 21 archivos del front formateados; 3 arreglos triviales (`alEnviar={() => void guardar()}`, 2 en el test) y 6 excepciones `HU-013`. CA4 probado con archivo temporal (borrado). `mvn -q package` en verde |
| 2026-10-15 | Tarea 4 (líder) | CA5: `mvn -q package` en verde (exit 0). Revisadas las 6 excepciones `HU-013` (todas por línea y con motivo). Guía, sección 4: versiones, Node 22.20, `spotless:check` y regla de excepciones. Pasa a cierre |
| 2026-10-15 | Cierre | Aceptada por la persona a cargo. Commit, merge a `v2/dev` y push |
