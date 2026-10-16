# EP-002: Estilo de código común (back y front)

- **Estado:** abierta
- **Módulo:** compartido (se aplica primero a configuracion)
- **Fase del roadmap:** base técnica transversal, antes de seguir con los demás módulos (ver `roadmap-v2.md`)

## Objetivo
Acortar, modernizar y unificar el código de la v2 antes de sumar módulos nuevos, para que todo se escriba igual, sin importar qué agente lo haga. Queda una guía con ejemplos y herramientas que la hacen cumplir solas en el build.

## Caso de uso
**Actor:** la persona a cargo del proyecto (dueña del producto) y los agentes que programan.
1. Un agente va a escribir código: lee la guía `docs/agentes/estilo-codigo.md` y copia el ejemplo del tipo de archivo que le toca.
2. Al compilar, Maven formatea el código y corre las reglas (formato, estructura de carpetas, reglas de React). Si algo no cumple, el build falla y dice qué.
3. Configuración, el único módulo de la v2 hoy, queda escrito según la guía y sirve de referencia para los que siguen.

## Alcance
- **Entra:** guía de estilo de back y front; Spotless, ArchUnit, ESLint + Prettier y `.editorconfig`; refactor de Configuración (back y front) sin cambiar el comportamiento; arreglo de DEF-001 dentro del refactor de front; estilo de QA (HU-014), cuando llegue el ejemplo de la persona a cargo.
- **No entra:** la v1 (`legacy`), que no se formatea ni se refactoriza; React Hook Form y Zod (ver decisiones).

## Historias
Base técnica: no lleva HU de QA ni documento funcional. La red de seguridad son los tests actuales (unitarios, de API, Vitest) más el smoke E2E `@critico` de HU-008.

| HU | Tipo | Título | Estado | Depende de |
|---|---|---|---|---|
| HU-010 | base técnica | Guía de estilo de código (back y front), y los agentes apuntan a ella. Líder, sin agente | aceptada | — |
| HU-011 | base técnica | Herramientas: Spotless (palantir-java-format), ArchUnit, ESLint + Prettier en el build de Maven, `.editorconfig` (imports `*` desde 5 clases). Solo la v2. `backend-java` ([archivo](../historias/HU-011-herramientas-estilo.md)) | aceptada | HU-010 |
| HU-012 | base técnica | Refactor del back de Configuración: B1 a B5, T1 a T3, R1 y R2 de `investigacion-estilo-codigo.md`. `backend-java` | planificada | HU-011 |
| HU-013 | base técnica | Refactor del front de Configuración: TanStack Query (F1, F2), partir `BloqueSheet`, plurales (F4), tests con `user-event` + MSW (T4, T5) y **DEF-001** (F3). `frontend` | planificada | HU-012 |
| HU-014 | base técnica | Estilo de QA: sección 5 de la guía y refactor del E2E de Configuración (`PantallaBase` con acciones comunes, `Locator` como campo, pasos finos; ver sección 2 de `investigacion-estilo-codigo.md`). Espera el ejemplo de la persona a cargo (Page Object, BasePage y pasos) | planificada | Ejemplo de la persona a cargo |

**Progreso:** 2 de 5 historias aceptadas.

## Decisiones y pendientes
- 2026-10-15: orden **guía + herramientas primero, refactor después** (persona a cargo).
- 2026-10-15: **librerías del front:** TanStack Query sí (carga y guardado de datos del servidor; descarta solo las respuestas viejas). `user-event` y MSW sí, solo para tests. **React Hook Form + Zod no:** copiarían en el front las reglas que ya valida el back, y DEF-001 muestra que dos copias se desfasan. El front valida solo campos vacíos; el resto, el back. Se reevalúa si un módulo tiene formularios grandes.
- 2026-10-15: **Spotless no arma imports `*`**; solo ordena y saca los que no se usan. La regla de los 5 o más del mismo paquete va en `.editorconfig` (IntelliJ) y en la guía.
- 2026-10-15: **DEF-001** (link `/u/0/`, severidad alta) se arregla dentro de HU-013 (persona a cargo).
- Pendiente: **HU-009** (casos de error de EP-001) conviene hacerla después de definir el estilo de QA, para no escribirla en el estilo viejo.
- 2026-10-15: **bugs conocidos** (persona a cargo): tag `@HU-XXX-DEF-YYY` en el escenario; se sigue ejecutando y el reporte lo muestra como "bug conocido", y avisa si pasa (sacar el tag). Ficha del `DEF` con precondiciones, datos de prueba, "nació en" y "se arregla en"; en git solo la captura (`defectos/evidencias/`), no la traza. Reglas en `qa-guia.md`. **El aviso en el reporte (hook del arnés) va en HU-014.**
- 2026-10-15: el estilo de QA entra como **HU-014** (planificada), por la regla nueva de épicas: mismo objetivo, misma épica. Puede ir antes o después de HU-011 a HU-013, cuando llegue el ejemplo.

## Para retomar
HU-010 y HU-011 aceptadas (guía y herramientas en el build). La próxima es **HU-012** (refactor del back de Configuración, `backend-java`). Con `/agente-lider continuar HU-012`, el líder arma el brief desde esta épica. Investigación de base: `docs/agentes/investigacion-estilo-codigo.md`.
