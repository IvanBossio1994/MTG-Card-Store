# Guía común de QA

La usan `qa-camino-feliz` y `qa-casos-error`.

## Stack de automatización
- **Gherkin en español** (`# language: es`): Característica / Antecedentes / Escenario / Esquema del escenario / Dado / Cuando / Entonces / Y / Ejemplos.
- **Cucumber-JVM + JUnit Platform**: los pasos se implementan en Java.
- **Playwright para Java** maneja el navegador. Se ubican los elementos **solo** con `page.getByTestId("...")`; nada de CSS ni XPath, salvo que no haya otra opción, y en ese caso se reporta como deuda.
- Se corre contra la app con el perfil `e2e`, que usa los repositorios en memoria con datos de ejemplo, así no depende de Google ni de internet.
- Comando: `.tools\apache-maven-3.9.16\bin\mvn.cmd -q test`, o filtrando por tag (`-Dcucumber.filter.tags="@HU-001"`).
- **Si la rama todavía no tiene el harness** (Cucumber y Playwright en `pom.xml`, runner y hooks), avisale al líder: armarlo es una tarea de `backend-java`, no de QA.

## Estructura
```
src/test/resources/features/<modulo>/<tema>.feature   escenarios
src/test/java/com/tcg/bot/e2e/pasos/                  step definitions (se reutilizan entre módulos)
src/test/java/com/tcg/bot/e2e/paginas/                Page Objects, uno por pantalla
src/test/java/com/tcg/bot/e2e/soporte/                hooks, arranque de la app y del navegador, evidencias
target/evidencias/                                    capturas y trazas de Playwright (no se versionan)
```

## Base funcional
- Los escenarios salen de los criterios de aceptación de la HU y de los **flujos del documento funcional** (`docs/funcional/documento-funcional.md`, leé solo la sección del módulo).
- Cada escenario lleva el tag del flujo que cubre, por ejemplo `@INV-F01`.
- La regresión se arma recorriendo los flujos `vigente` del documento. Si un flujo vigente no tiene escenario, es un hueco de cobertura y se informa.

## Tags
- `@HU-XXX`: historia de origen.
- `@<MOD>-F0X` o `@<MOD>-A0X`: flujo funcional que cubre.
- `@feliz` o `@error`: tipo de escenario.
- `@critico`: va en la regresión mínima (smoke).
- `@regresion`: va en la regresión completa.

## Reglas
- **Un escenario = un comportamiento.** Va en lenguaje de negocio, sin clics ni selectores en el texto del Gherkin.
- Reutilizar pasos y Page Objects antes de crear nuevos.
- Cada escenario es independiente y deja los datos como los encontró.
- Si falta un `data-testid`, no inventes uno: reportalo como defecto (severidad baja, área frontend).
- **Alcance de ejecución:** lo indica el líder en el prompt. Puede ser:
  - `historia`: `-Dcucumber.filter.tags="@HU-XXX"`;
  - `historia+smoke`: `"@HU-XXX or @critico"`;
  - `regresion`: `"@regresion"`.

  **Nunca ejecutes más de lo indicado.** Si el prompt no trae alcance, corré solo `@HU-XXX` y aclaralo en la respuesta. En un retest después de una corrección, corré solo los escenarios que fallaron (por nombre o por tag) más lo que indique el alcance.
- Solo escribís en `src/test/**` y `docs/agentes/defectos/**`. No tocás código de la app. No hagas commit.

## Reporte de defectos
- Un archivo por bug: `docs/agentes/defectos/DEF-XXX-<tema>.md`. El número sigue al último del índice.
- Formato:

```markdown
# DEF-XXX: <título corto>
- **Historia:** HU-XXX · **Escenario:** <nombre> · **Tipo:** feliz | error
- **Severidad:** crítica | alta | media | baja · **Área:** backend | frontend | google | datos
- **Estado:** abierto
## Pasos para reproducir
1. ...
## Resultado esperado
...
## Resultado obtenido
...
## Evidencia
- Captura o traza: `target/evidencias/...`
- Salida del test (líneas relevantes):
```

- Además se agrega una fila al índice `docs/agentes/defectos/README.md`.

**Severidades:**
- **Crítica:** se pierden o corrompen datos del Sheet, o el flujo principal no se puede usar.
- **Alta:** falla una función, pero hay una forma de hacerlo por otro lado.
- **Media:** error de validación o un mensaje equivocado.
- **Baja:** algo visual, un texto o un locator que falta.

## Respuesta al líder (150 palabras como máximo)
- **Veredicto:** ✅ cumple / ❌ no cumple los criterios de la historia.
- **Escenarios:** X pasaron / Y fallaron (con el nombre de los que fallaron).
- **Defectos:** lista de `DEF-XXX` con su severidad.
- **Archivos `.feature` creados o modificados.**
