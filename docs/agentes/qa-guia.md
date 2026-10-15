# Guía común de QA

La usan `qa-camino-feliz` y `qa-casos-error`, siempre dentro de la **HU de QA** de una épica, que prueba las HU listadas en su campo "Cubre".

## Stack de automatización
- **Gherkin con palabras clave en inglés y texto en español**: `Feature`, `Background`, `Scenario`, `Scenario Outline`, `Given`, `When`, `Then`, `And`, `But` y `Examples`. Los archivos no llevan `# language: es`. Ver "Cómo se escribe el Gherkin", más abajo.
- **Cucumber-JVM + JUnit Platform**: los pasos se implementan en Java.
- **Playwright para Java** maneja el navegador. Se ubican los elementos **solo** con `page.getByTestId("...")`; nada de CSS ni XPath, salvo que no haya otra opción, y en ese caso se reporta como deuda.
- **Sheet de QA:** los E2E corren contra un Sheet real, solo para QA: `1wWH5bJV3kqXHyLK4zorxnGcGsZ_bRHrKXmn4DGgrG2I`.
  - Se accede con la cuenta de servicio (`config/google-service-account.json`), sin login de Gmail.
  - **Nunca** se usa el Sheet del local ni el de pruebas de la dueña.
  - Cada escenario deja el Sheet de QA como lo encontró, o lo prepara en el `Background`.
- Los escenarios que no necesitan Google pueden correr con el perfil `e2e` (datos en memoria), que es más rápido.
- Comando: `.tools\apache-maven-3.9.16\bin\mvn.cmd -q test`, filtrando por tag (`-Dcucumber.filter.tags="@HU-003 or @HU-004"`).
- **Si la rama todavía no tiene el arnés** (Cucumber y Playwright en `pom.xml`, runner, hooks y `PantallaBase`), avisale al líder: armarlo es la primera tarea de la HU de QA y la hace `backend-java`.

## Estructura
```
Cuatro carpetas por tipo; adentro, un archivo por módulo (pedido de la dueña, 2026-10-15):
src/test/resources/features/<modulo>.feature                  escenarios del módulo; cada HU suma los suyos con sus tags
src/test/java/com/tcg/bot/e2e/paginas/<Modulo>Pantalla.java   Page Object: TODOS los métodos de la pantalla (escribir, guardar, leer mensajes)
src/test/java/com/tcg/bot/e2e/localizadores/<Modulo>Localizadores.java   los data-testid de la pantalla, como constantes (de docs/v2/locators.md)
src/test/java/com/tcg/bot/e2e/pasos/<Modulo>Pasos.java        steps del módulo: solo llaman a la Pantalla, sin locators ni lógica
src/test/java/com/tcg/bot/e2e/compartido/                     PantallaBase (abrir módulo, esperar carga, leer mensajes) y objetos de piezas comunes (MarcoApp, CapaCarga); cada <Modulo>Pantalla extiende PantallaBase
src/test/java/com/tcg/bot/e2e/soporte/                         hooks, arranque de la app y del navegador, evidencias
target/evidencias/                                    capturas y trazas de Playwright (no se versionan)
```

## Cómo se escribe el Gherkin
- **Tercera persona** (el usuario): "inicia sesión", "visualiza", "hace clic en". Nunca "yo", "estoy" ni "veo".
- **Nombre del escenario:** `[<Pantalla o módulo>] <qué hace>`, en presente y en tercera persona.
- **`Scenario Outline`** cuando el mismo comportamiento se ejecuta con varios datos. El nombre lleva el parámetro entre `< >`, así cada ejecución muestra su dato.
- **Pasos:** dicen lo que el usuario ve y hace, con el texto visible entre comillas (`hace clic en "Guardar tienda"`, `visualiza el mensaje "Tienda guardada."`). Nada de selectores, `data-testid`, CSS ni detalles técnicos: eso va en el Page Object.
- **`Then`** valida algo observable: un mensaje, un dato en pantalla o el dato en el Sheet de QA.

```gherkin
@HU-003 @CFG-F02 @feliz @critico
Scenario: [Configuracion] Sincroniza el Google Sheet
  Given inicia sesión en la app
  And visualiza la pantalla de "Inicio"
  When hace clic en "Configuración"
  And visualiza el módulo de "Configuración"
  And pega el link del Sheet de QA
  And hace clic en "Guardar inventario"
  Then visualiza el mensaje "Inventario guardado."
  And valida que el Sheet de QA tenga la pestaña "Inventario"

@HU-003 @CFG-A06 @error
Scenario Outline: [Configuracion] Rechaza la cotización <cotizacion>
  Given visualiza el módulo de "Configuración"
  When escribe "<cotizacion>" en "Cotización (ARS por USD)"
  And hace clic en "Guardar precios"
  Then visualiza el error "<mensaje>"

  Examples:
    | cotizacion | mensaje                                 |
    |            | Ingresá la cotización del dólar.        |
    | 0          | La cotización tiene que ser mayor a 0.  |
```

## Base funcional
- Los escenarios salen de los criterios de aceptación de la HU y de los **flujos del documento funcional** (`docs/funcional/documento-funcional.md`, leé solo la sección del módulo).
- Cada escenario lleva el tag del flujo que cubre, por ejemplo `@INV-F01`.
- **Flujos a cubrir:** los del campo "Flujos funcionales" de cada HU que cubre la HU de QA (en el documento funcional, `propuesto · HU-XXX`, `diseñado · … → HU-XXX` o `vigente`). **Cada uno tiene que tener al menos un escenario.** Si alguno queda sin cubrir, se informa como hueco.
- Si la HU tiene diseño, sumá también:
  - los estados de la spec UX (`docs/v2/ux/<pantalla>/spec.md`: cargando, guardando, éxito, aviso, error);
  - los textos exactos de `docs/v2/textos.md`;
  - los locators de `docs/v2/locators.md`.
- La regresión se arma recorriendo los flujos `vigente` del documento. Si un flujo vigente no tiene escenario, es un hueco de cobertura y se informa.

## Tags
- `@HU-XXX`: la historia que originó la funcionalidad (por ejemplo `@HU-003`), no la HU de QA.
- `@<MOD>-F0X` o `@<MOD>-A0X`: flujo funcional que cubre.
- `@feliz` o `@error`: tipo de escenario.
- `@critico`: va en la regresión mínima (smoke).
- `@regresion`: va en la regresión completa.

## Reglas
- **Un escenario = un comportamiento.** Se escribe como en "Cómo se escribe el Gherkin": tercera persona y texto visible, sin selectores.
- Reutilizar pasos y Page Objects antes de crear nuevos.
- Cada escenario es independiente y deja los datos como los encontró.
- Si falta un `data-testid`, no inventes uno: reportalo como defecto (severidad baja, área frontend).
- **Alcance de ejecución:** lo indica el líder en el prompt. Puede ser:
  - `historias`: las HU que cubre, `-Dcucumber.filter.tags="@HU-003 or @HU-004"`;
  - `historias+smoke`: `"@HU-003 or @HU-004 or @critico"`;
  - `regresion`: `"@regresion"`.

  **Nunca ejecutes más de lo indicado.** Si el prompt no trae alcance, corré solo las HU que cubre y aclaralo en la respuesta. En un retest después de una corrección, corré solo los escenarios que fallaron (por nombre o por tag) más lo que indique el alcance.
- Solo escribís en `src/test/**` y `docs/agentes/defectos/**`. No tocás código de la app. No hagas commit.

## Reporte de defectos
- Un archivo por bug: `docs/agentes/defectos/DEF-XXX-<tema>.md`. El número sigue al último del índice.
- Formato:

```markdown
# DEF-XXX: <título corto>
- **HU de QA:** HU-XXX · **Funcionalidad de:** HU-YYY · **Escenario:** [Pantalla] <nombre> · **Tipo:** feliz | error
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
- **Veredicto:** ✅ cumple / ❌ no cumple los criterios de las historias que cubre.
- **Escenarios:** X pasaron / Y fallaron (con el nombre de los que fallaron).
- **Defectos:** lista de `DEF-XXX` con su severidad.
- **Archivos `.feature` creados o modificados.**
