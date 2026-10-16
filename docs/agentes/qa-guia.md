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
- **Si la rama todavía no tiene el arnés** (Cucumber y Playwright en `pom.xml`, runner, hooks y `PantallaBase`), avisale al líder: armarlo es la primera tarea de la HU de QA y la hace `backend-java`.

## Cómo correr los E2E (arnés armado en HU-008)
- **Comando:** `.tools\apache-maven-3.9.16\bin\mvn.cmd -q test "-Dcucumber.filter.tags=@critico"` (smoke). Para otras corridas, cambiar el filtro: `"-Dcucumber.filter.tags=@HU-008 or @critico"`, `"-Dcucumber.filter.tags=@regresion"`. En PowerShell, todo el `-D...` va entre comillas.
- **Los E2E corren solo si se pasa `-Dcucumber.filter.tags`.** Ese parámetro activa el perfil Maven `escenarios-e2e`, que corre únicamente el runner `e2e/soporte/EscenariosE2ETest` (ni los tests de back ni Vitest, pero sí compila el frontend). Sin el parámetro, `mvn.cmd -q test` corre back y Vitest como siempre, sin E2E.
- **Arranque:** no hay que levantar la app. Los hooks (`e2e/soporte/GanchosE2E`) la levantan una vez por corrida, en un puerto libre y en el mismo proceso, con el perfil `e2e` (sin la v1 ni el login de Gmail; sesión simulada "prueba@tcg-inventory.local") pero con el **Sheet de QA real** vía la cuenta de servicio. La configuración se guarda en `target/e2e-datos` (nunca en `data/`) y arranca con el Sheet de QA y la pestaña "Inventario".
- **Navegador:** por defecto usa el **Chrome instalado** (canal `chrome`), sin ventana y **sin descargar nada**. La primera corrida igual baja de Maven el jar de Playwright (~200 MB, una sola vez). Opciones:
  - `-De2e.ver-navegador=true`: muestra el navegador en pantalla.
  - `-De2e.navegador=chromium`: usa el Chromium de Playwright (para una PC sin Chrome); la primera vez lo descarga solo a `%LOCALAPPDATA%\ms-playwright` (unos 150 MB, necesita internet).
- **Requisito:** la clave `config/google-service-account.json` tiene que existir (no se abre). Si falta, la corrida frena al arrancar con un mensaje que lo dice.
- **Evidencias:** si un escenario falla, quedan la captura (`target/evidencias/<escenario>.png`, también adjunta al reporte) y la traza de Playwright (`<escenario>-traza.zip`, se abre con `npx playwright show-trace` o en trace.playwright.dev). Reporte HTML de la corrida: `target/evidencias/reporte-e2e.html`.

### Sheet de QA limpio (CA2 de HU-008)
- Al arrancar la corrida, `SheetDeQa` saca una foto del Sheet de QA (pestañas, orden y valores). **Después de cada escenario, pase o falle**, lo devuelve a esa foto en una sola escritura: borra las pestañas nuevas, devuelve nombres y orden, recrea las borradas y reescribe solo las pestañas cuyos valores cambiaron. Los formatos de celda no se tocan.
- Además, antes de cada escenario se borra la configuración guardada de la app: cada escenario arranca con los valores por defecto ("Inventory Manager", Sheet de QA, pestaña "Inventario"). Ningún escenario depende de otro.
- Hoy la foto del Sheet de QA es **una sola pestaña "Hoja 1" vacía** (un Sheet "nuevo"). Si un escenario necesita otro punto de partida, lo arma en el `Background` o en un `Given`, y la limpieza lo deshace.
- En los pasos, `SheetDeQa` se recibe por constructor (Cucumber lo inyecta) y ofrece:
  - `pestanas()`: nombres en orden.
  - `valores(pestana)` y `encabezados(pestana)`: lo que hay en la pestaña, como lo lee la app.
  - `dejarConPestanas(mapa)`: deja el Sheet exactamente con esas pestañas y valores (por ejemplo, `Map.of("Hoja 1", List.of())`).
  - Constantes `SheetDeQa.ID` y `SheetDeQa.LINK` para pegar en Configuración.

### Piezas del arnés
- `e2e/soporte/`: `EscenariosE2ETest` (runner), `GanchosE2E` (hooks), `AppParaE2E` (app), `NavegadorE2E` (Playwright; se inyecta en pantallas y pasos) y `SheetDeQa`.
- `e2e/compartido/`: `PantallaBase` (abrir el módulo, esperar que esté lista, `leerMensajeDeBloque`, `leerErrorDeCampo`), `MarcoApp` (menú lateral) y `CapaCarga`.
- Inyección: las clases de pasos y las pantallas reciben por constructor lo que necesitan (`NavegadorE2E`, `SheetDeQa`, `<Modulo>Pantalla`); Cucumber crea una instancia nueva por escenario (picocontainer).
- Pasos comunes ya hechos (`pasos/AppPasos`): `abre la app`, `elige "<módulo>" en el menú`, `visualiza el módulo de "<módulo>"`.
- La capa de carga se ubica por `app-capa-carga` (`CapaCarga`).
- **Límite de Google:** 60 lecturas por minuto por proyecto, compartidas entre la app y la restauración del Sheet de QA. Por eso `SheetDeQa` reintenta ante un 429 y cada escenario dura al menos 15 s (`GanchosE2E`). Un 429 suelto no es un defecto de la app. La corrida de Configuración tarda unos 2,5 min.

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
- `@HU-XXX-DEF-YYY`: **bug conocido**. El escenario falla por el defecto `DEF-YYY`, que nació en la funcionalidad de `HU-XXX` (ej.: `@HU-008-DEF-001`). Se agrega al reportar el defecto, si el arreglo no va en la misma HU, y se saca cuando se arregla.
  - El escenario **se sigue ejecutando**. Si falla, el reporte lo muestra como "bug conocido (DEF-YYY)" y no como falla nueva. Si pasa, avisa que el defecto parece arreglado y que hay que sacar el tag.
  - Mientras ese aviso no esté en el arnés (llega en HU-014), el escenario falla normal y QA lo informa como bug conocido en su respuesta.

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
- **Encontrado en:** HU-XXX (la HU de QA, o dónde apareció) · **Nació en:** HU-YYY (la funcionalidad) · **Se arregla en:** HU-ZZZ | a definir
- **Escenario:** [Pantalla] <nombre> · **Tag:** `@HU-YYY-DEF-XXX` · **Tipo:** feliz | error
- **Severidad:** crítica | alta | media | baja · **Área:** backend | frontend | google | datos
- **Estado:** abierto
## Precondiciones
- <estado de la app y del Sheet de QA antes de empezar>
## Datos de prueba
- <solo si hacen falta: valores cargados, pestañas, links>
## Pasos para reproducir
1. ...
## Resultado esperado
...
## Resultado obtenido
...
## Evidencia
- Captura: `evidencias/DEF-XXX-<paso>.png` (copiada desde `target/evidencias/`)
- Salida del test (líneas relevantes):
```

- Además se agrega una fila al índice `docs/agentes/defectos/README.md`.
- **Evidencia que se guarda en git:** solo la captura del paso que falla, copiada a `docs/agentes/defectos/evidencias/` (`target/` se borra en cada build). La traza de Playwright no se versiona: pesa varios MB y se vuelve a generar corriendo el escenario. La captura sale del Sheet de QA; si muestra datos personales (un Gmail, por ejemplo), se recorta.

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
