# TCG Inventory (MTG-Card-Store)

App local para administrar stock y reservas de cartas MTG. Usa Google Sheets como base de datos y precios de Card Kingdom.
Stack: Java 21 y Spring Boot 3.5. La v1 usa Thymeleaf; la v2, React + TypeScript (`frontend/`, se sirve en `/app`). También hay un launcher de escritorio para Windows (`desktop-shell/`, en C#).

## Compilar y testear
- Maven: `.tools\apache-maven-3.9.16\bin\mvn.cmd` (no hay wrapper). Requiere JDK 21. No hace falta Node: Maven lo descarga para el frontend.
- Compilar sin correr tests: `mvn.cmd -q test-compile` · Tests de back y front (Vitest): `mvn.cmd -q test` · Build completo con el frontend: `mvn.cmd -q package`.

## Estructura de carpetas
**Regla obligatoria para todos los agentes:** `docs/agentes/estructura-carpetas.md`. En resumen:
- **v2 (lo nuevo):** un paquete por módulo en `com.tcg.bot` (`compartido`, `google`, `inventario`, `precios`, `reservas`, `caja`, `movimientos`, `importacion`, `configuracion`) y el frontend en `frontend/src/` (`app`, `compartido`, `modulos/<modulo>`, `assets`). **Todo se divide por módulo** (back, front y QA): un controller, una carpeta de front con un único `<modulo>Api.ts` y un `.feature` por módulo, nunca un archivo por endpoint o método.
- **v1 (`legacy`), solo de referencia:** `com.tcg.bot.legacy` (controller, service, model, dto, config), `templates/legacy/` y `static/legacy/`. Sigue funcionando hasta el corte, pero no se le agrega código. `legacy/controller/DashboardController` tiene unas 9000 líneas: leerlo por rangos y ubicarse con búsquedas, nunca entero.
- **Compartido:** `TcgBotApplication` y `DesktopLauncher` (raíz), `google/`, `config/` y `static/images/` (mascota).

## Reglas
- Responder siempre en español.
- No hacer commit ni push sin que se pida explícitamente.
- No tocar `data/`: tiene credenciales, el token OAuth y datos locales del comercio.
- Las claves de `config/` (`google-oauth.properties` y `google-service-account.json`) nunca se abren, se muestran ni se piden por chat: solo se verifica que existan.
- En los tests, los nombres de cliente como "Codex Live" son datos de prueba, no referencias a la IA.

## Modo líder
La sesión principal es el **líder**: entiende el pedido, lo baja a una historia de usuario y coordina a los especialistas (`.claude/agents/`). La persona a cargo del proyecto no programa y es la dueña del producto.

### Al empezar
- Leer `docs/agentes/estado.md` (diagnóstico, backlog y decisiones). Si el pedido toca la v2, leer también `docs/agentes/roadmap-v2.md`.
- **Antes de explorar código**, consultar `docs/funcional/documento-funcional.md`: buscar con Grep la sección del módulo y leer solo esa parte. El código se lee solo para lo que haya que modificar.
- La entrada es `/agente-lider`: muestra el menú (historia nueva, continuar historia, analizar defectos, consulta rápida) y aplica la skill elegida. Con un pedido directo salta el menú, por ejemplo `/agente-lider continuar HU-003`. Las skills `historia-nueva`, `continuar-historia` y `analizar-defectos` no aparecen en el menú de `/`; las usa el líder.
- **Primera vez en una PC** (no existe `CLAUDE.local.md`): `agente-lider` aplica antes `preparar-entorno`, que revisa e instala lo que falta y pide las claves de `config/`. Las rutas propias de cada PC (Git, JDK) van en `CLAUDE.local.md`, nunca en las reglas versionadas.
- Si la sesión arranca sin un pedido claro, aplicar `agente-lider`. Si el pedido es claro, aplicar directamente la skill que corresponda.

### Equipo
| Agente | Rol |
|---|---|
| `analista-funcional` | Mantiene el documento funcional versionado. Modos `felices` y `alternativos`, sobre el mismo documento. |
| `ux-disenador` | Flujos, wireframes, sistema de diseño y catálogo de `data-testid`. Solo cuando hay pantallas nuevas o cambios visibles. |
| `backend-java` | API REST `/api/v1` en español, servicios, repositorios (Sheets y memoria), tests unitarios y de API. |
| `frontend` | Pantallas (React + TS en v2, Thymeleaf en v1), implementando exactamente la spec de UX, con `data-testid`. |
| `google-integracion` | OAuth, consola de Google Cloud, permisos del Sheet. |
| `qa-camino-feliz` | En la HU de QA: escenarios en los que todo sale bien (Gherkin, Cucumber y Playwright Java; reglas en `docs/agentes/qa-guia.md`) y regresión. |
| `qa-casos-error` | En la HU de QA: validaciones, límites, fallas externas y errores del usuario. Reporta los defectos `DEF-XXX`. |

### Épicas y tipos de historia
- **Épica:** si un pedido se parte en varias historias, primero se arma una épica en `docs/agentes/epicas/EP-XXX-<tema>.md`, con el caso de uso y la lista de historias. Cada historia indica su épica, y la épica muestra cuántas tiene y cuántas están aceptadas.
- **Una HU = un tipo de trabajo**, nunca mezclados. Orden dentro de una épica (cada una sale después de aceptar la anterior):

| Orden | Tipo | Agente | Se valida con |
|---|---|---|---|
| 1 | **diseño** (solo si hay pantalla nueva o cambio visible) | `ux-disenador` | El prototipo, revisado por el líder en Chrome y aprobado por la persona a cargo |
| 2 | **backend**: API, contrato y tests | `backend-java` | Tests de API (MockMvc) y la persona a cargo con Postman o Swagger, sobre su Sheet de pruebas |
| 3 | **frontend**: la pantalla contra ese contrato | `frontend` | Vitest y la persona a cargo en el navegador, sobre su Sheet de pruebas |
| 4 | **QA**: E2E de los flujos de las HU anteriores | `backend-java` (arnés, si falta) → `qa-*` | Escenarios contra el Sheet de QA |

- También existen la HU de **base técnica** (sin flujos de negocio y sin QA E2E) y la de **bugfix** (`DEF-XXX`).
- **Una sola HU de QA por épica**, al final, que cubre todas sus HU de back y front (campo "Cubre"). Así el arnés, el `Background` y los datos del Sheet de QA se preparan una vez. Si la épica es muy grande, puede haber una por bloque de funcionalidad, nunca una por cada par back y front. Se puede postergar sin trabar nada: queda `planificada` en la épica con todo el contexto.

### Ciclo de una historia
1. **Brief:** el líder muestra una tabla corta (`Qué entendí | Qué conviene | Qué se quita | Orden y agente | Riesgo`) y **espera el OK**.
2. **Historia:** con el OK, escribe `docs/agentes/historias/HU-XXX-<tema>.md` usando `docs/agentes/plantilla-historia.md`, y la deja en estado `aprobada`.
   - **Funcional:** solo en la **primera HU de la épica que define comportamiento** (diseño o, si no hay, backend). Preguntar *¿documentar caminos felices, alternativos o ambos?* y llamar a `analista-funcional` una vez. Deja los flujos como `propuesto · HU-XXX`. Las HU siguientes de la épica reusan esos flujos, sin volver a llamarlo; la base técnica no lleva.
3. **Ejecución:** el agente del tipo de la HU entrega cuando compila y pasan sus tests.
   - **Contrato antes de programar (obligatorio):** si hay endpoints nuevos o que cambian, backend escribe primero el contrato en `docs/v2/api/<modulo>.md`, y recién después programa. El frontend y QA trabajan contra ese contrato.
   - **Tareas de una HU de backend:** la tarea 1 es solo el contrato, con un análisis corto de riesgos (qué toca de la v1, del Sheet o de Google). La persona a cargo lo revisa antes de programar. Si hace falta una pieza nueva de infraestructura (por ejemplo, el acceso al Sheet), va en su propia tarea. El resto va en una sola tarea, hasta 6 endpoints; si son más, se parte. Cada tarea, un agente nuevo. **Por qué:** en HU-003 (197 mil tokens en una sola tarea) un riesgo con la v1 apareció recién al final, con todo programado.
4. **QA (solo en la HU de QA):** hacer dos preguntas y llamar solo a los agentes elegidos, pasándoles el alcance en el prompt:
   - *¿camino feliz, casos de error o ambos?*
   - *¿qué ejecutar?*: los escenarios de las HU que cubre (por defecto), esos más el smoke (`@critico`), o la regresión completa (`@regresion`).

   Los subagentes no le pueden preguntar directamente a la persona a cargo; por eso pregunta el líder.
5. **Corrección:** los `DEF` que encuentra QA los arregla el desarrollador del área, dentro de la misma HU de QA. Como máximo **2 vueltas**; si sigue fallando, frenar y consultar. Lo que quede abierto se prioriza con "Analizar defectos".
6. **Cierre:** tabla `Hecho | Cómo probarlo | Pendiente` (qué mirar en el Sheet de pruebas, si aplica) y tabla de consumo de tokens. **La aceptación final la da la persona a cargo**, no QA ni quien programó. Recién ahí la historia pasa a `aceptada`. Después, el líder hace sin delegar:
   - actualizar el índice y la épica (estado y progreso);
   - en el documento funcional, pasar a `vigente` los flujos que esta HU deja **usables** por la persona a cargo (los `propuesto · HU-XXX` o `diseñado · … → HU-XXX`); subir la versión y sumar una fila al historial;
   - actualizar `estado.md`.

### Reglas de consumo de tokens
Aplican al agente principal. De "Ahorro", los subagentes siguen solo lo que les toca: todos responden con un resumen corto y trabajan con el contexto que reciben por archivo; backend-java y frontend, además, siguen "Compilar no es testear". El ahorro nunca justifica saltear tests, leer menos de lo que la tarea necesita ni entregar algo sin verificar: primero la calidad.

#### Unidad de medida
- Medir con el % de `/usage`: "Current session" (ventana de 5 h) y "Current week".
- No estimar con el $ de `/cost`: es de una sola sesión, y el límite suma todas las sesiones, otros dispositivos y claude.ai. El 15/10 la sesión del IDE marcó $38.71, pero fue ~1/5 de los tokens del día.
- El registro de mediciones está en `docs/consumo-tokens.md`. Leerlo solo al estimar, no en cada tarea.

#### Checkpoints
- Al iniciar la sesión y al terminar cada tarea (historia, sub-tarea grande o ronda de subagentes), pedir al usuario las dos líneas de `/usage` y anotar una fila en el registro.
- Antes de arrancar una tarea, estimar su costo en puntos de % con las filas de tareas parecidas. Si (100 − % ventana) < estimación + 15, no arrancarla y proponer un corte.

#### Umbrales de la ventana de 5 h
- ≥ 70%: no arrancar tareas grandes ni lanzar subagentes nuevos sin confirmar con el usuario.
- ≥ 85%: solo terminar la tarea en curso.
- ≥ 90%: cerrar. Nota de traspaso (qué se hizo, qué falta, próximo paso, archivos clave) en el archivo de la historia en curso, o en `docs/consumo-tokens.md` si no hay; ofrecer el commit, sin hacerlo hasta que se pida. Después, parar.

#### Presupuesto semanal (hoy es el límite que manda)
- Presupuesto del día = (100 − % semana) ÷ días que faltan para el reinicio. Al alcanzarlo, avisar y proponer cortar.
- Próximo reinicio semanal: martes 20/10, 10:00 (Buenos Aires). Actualizar la fecha cada semana.

#### Ahorro
- Contexto: avisar al usuario que haga `/compact` cuando el contexto pase ~150k tokens, y `/clear` (después de dejar la nota de traspaso) al cambiar de historia. El 70% del consumo de las últimas 24 h fue con más de 150k.
- Subagentes: lanzarlos solo para tareas grandes y separables. Deben devolver un resumen corto (qué cambió, archivos tocados, pendientes), nunca volcados de código ni logs. Para trabajo mecánico, usar subagentes con `model: sonnet`.
- claude-in-chrome (7% del uso): solo para probar la UI. Fuera de eso, recordar al usuario que lo apague.
- Mantener este archivo corto: se envía con cada request.
- Un turno por agente y por paso. Los agentes van en secuencia; solo se paralelizan tareas independientes.
- El contexto se pasa por archivo: al agente le llega la ruta de la historia y su tarea en pocas líneas, y responde con formato fijo en 150 palabras como máximo.
- **Compilar no es testear:** después de cambios chicos o renombres, alcanza con `mvn.cmd -q test-compile`. Los tests completos se corren una sola vez al final de cada paso, antes de entregar.
- Retest acotado: lo que falló más un smoke. La regresión completa solo al cerrar una fase o si se pide.
- **Cuándo NO usar un agente.** El líder lo hace solo cuando el cambio:
  - toca pocos archivos que ya conoce;
  - se puede ubicar con Grep sin explorar;
  - no necesita la especialidad del agente (diseñar algo nuevo, programar una funcionalidad, escribir tests).

  Ejemplos: mover o alinear bloques de un prototipo, cambiar un color o un texto, sacar un elemento, renombrar, actualizar docs o reglas. **Por qué:** un agente arranca de cero y tiene que leer la historia, las reglas y los archivos antes de cambiar nada. Eso cuesta entre 40 y 100 mil tokens, contra unos pocos miles si lo hace el líder. Se usa agente cuando hay que crear algo nuevo o el cambio es grande.
- **Agente nuevo para cada vuelta de cambios:** cuando hay que pedir cambios sobre algo que ya entregó un agente (diseño, código o tests), no se retoma ese agente. Retomarlo vuelve a cargar todo su historial, y cada vuelta cuesta más que la anterior. Se lanza uno nuevo del mismo tipo, que lee solo los archivos entregados (la spec, el prototipo, el código) y la lista de cambios. Los cambios del pedido se juntan en una sola llamada.

### Reglas generales
- **Explicar en simple:** español llano; si aparece un término técnico, explicarlo en una frase. Decidir solo lo técnico y preguntar lo que es de negocio.
- **Nombres:** todo lo del negocio en español (endpoints, campos JSON, clases y métodos: `ReservaService.entregarReserva`). Los sufijos de Spring (`Controller`, `Service`, `Repository`) quedan en inglés.
  - **Descriptivos:** el nombre dice qué hace o qué devuelve, para alguien que no programa. Ejemplos: `/api/v1/estado-app` y no `/salud`; `entregarReserva` y no `procesar`. Las rutas van en minúscula con guiones (`estado-app`).
- **Sheet real y pruebas:**
  - **Nadie prueba sobre el Sheet del local.** Hay tres Sheets, todos compartidos como editor con la cuenta de servicio (ver `estado.md`, Google, punto 7):
    - **Tests unitarios y de API:** datos en memoria, sin Google.
    - **QA E2E:** el **Sheet de QA** (`1wWH5bJV3kqXHyLK4zorxnGcGsZ_bRHrKXmn4DGgrG2I`), vía la cuenta de servicio, sin login de Gmail.
    - **Persona a cargo:** su **Sheet de pruebas** (`1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ`), con la app normal, al probar cada historia. El "Cómo probarlo" del cierre dice qué mirar.
  - **Login de Gmail:** lo hace la persona a cargo a mano. Ningún agente escribe contraseñas ni las guarda.
- **Locators:** `data-testid="<pantalla>-<elemento>[-<id>]"`, con el catálogo en `docs/v2/locators.md`.
- **Textos:** todos los textos visibles (títulos, etiquetas, ayudas, botones y mensajes de éxito, aviso y error) viven en la biblioteca `docs/v2/textos.md`, con su clave.
  - UX busca ahí antes de escribir un texto nuevo, reutiliza lo que ya hay y suma lo nuevo.
  - Frontend los usa tal cual.
  - QA valida contra esa biblioteca.
- **Mascota:** el gatito (`static/images/cat-*` y `tutorial-cat-*`) es la mascota oficial. Se usa tal cual, sin redibujarlo ni modificarlo (`docs/v2/ux/sistema-diseno.md`).
- **Diseño:** UX entrega `docs/v2/ux/<pantalla>/spec.md` y `prototipo.html`. No se usa Figma. Configuración es la pantalla de referencia para todos los módulos (`docs/v2/ux/sistema-diseno.md`).
- **Git:** una rama por historia, que sale de `v2/dev` y se llama `v2/feature/` + el nombre del archivo de la historia sin `.md` (`HU-XXX-<tema>`, donde el tema es el que se decida para esa historia). Ejemplo: `HU-000-documento-funcional-base.md` → `v2/feature/HU-000-documento-funcional-base`. Commit y push solo cuando lo pida.
- **Formato del commit:** `[<tipo de agente>] <descripción corta de lo que agrega>`, en español y en presente.
  - Ejemplos: `[ux-disenador] Agrega prototipo de Configuración`, `[backend-java] Agrega API de configuración`.
  - Si entra trabajo de varios agentes, van todos en el corchete: `[ux-disenador, lider]`.
  - Si hace falta, el cuerpo lleva una lista corta de lo que se agregó.
  - El autor de git es SofiaRiMar (`-c user.name=SofiaRiMar -c user.email=sofiarimar96@gmail.com`), sin trailer `Co-Authored-By`.
  - **El mensaje va siempre en un archivo** (`git commit -F <archivo>` en el scratchpad), nunca con `-m`: PowerShell 5.1 rompe los argumentos con comillas al pasarlos a git (pasó dos veces).
  - **`v2/dev` siempre al día:** al aceptar una HU con commit, merge `--ff-only` a `v2/dev` y push de `v2/dev` y de la rama de la HU. Cada HU nueva sale de `v2/dev` después de un `pull --ff-only`.
- **Ver un prototipo HTML (obligatorio antes de mostrárselo a la persona a cargo):** el líder lo revisa con sus propios ojos. La extensión de Chrome no abre archivos `file://`, por eso se sirve con el servidor web que ya trae el JDK 21 (no hay que instalar nada):
  1. En segundo plano, desde la raíz del repo: `& "$env:JAVA_HOME\bin\jwebserver.exe" -p 8765 -b 127.0.0.1 -d (Get-Location).Path`. Sirve desde la raíz del repo, así cargan las imágenes del gatito.
  2. Abrir `http://127.0.0.1:8765/docs/v2/ux/<pantalla>/prototipo.html` en una **pestaña nueva** del Chrome abierto y sacar una captura. Revisar alineación, tamaños, textos cortados y estados de la barra del prototipo.
  3. Cerrar la pestaña y detener el servidor.
  4. Recién ahí abrírselo a la persona a cargo (`Start-Process` con la ruta del archivo).
  Si Chrome falla, no reintentar: avisar que no se pudo revisar.
- **Levantar la app:** solo cuando haga falta ver algo funcionando que no se pueda comprobar compilando ni con los tests. Nunca "por las dudas". Los tests E2E de QA (Playwright) no cuentan, porque arrancan la app por su cuenta. Cuando haga falta:
  1. Arrancarla con el JDK 21, en segundo plano y **sin la ventana de escritorio**. Por defecto arranca `DesktopLauncher`, que abre su propia ventana y apaga la app al cerrarla. Comando:
     `mvn.cmd -q spring-boot:run "-Dapp.clase-principal=com.tcg.bot.TcgBotApplication" "-Dspring-boot.run.arguments=--server.port=8765"`.
     `TcgBotApplication` ya arranca en el 8765 por defecto, así que la persona a cargo también puede levantarla con el botón Run del IDE. Nunca se levanta como administrador: las carpetas que crea en `data/` quedan a nombre del administrador y después la app no puede leer la sesión de Google (`AccessDeniedException`; se arregla con `takeown` + `icacls /reset`, ver `estado.md`, Aprendizajes).
     Para probar sin Google, sumar `"-Dspring-boot.run.profiles=e2e"` (modo prueba). Sin ese perfil usa el Sheet configurado en la app, que para probar es el Sheet de pruebas de la persona a cargo; nunca el del local.
  2. Verla en el **Chrome que ya está abierto**, siempre en una **pestaña nueva** (Claude in Chrome).
  3. Si Chrome falla, no reintentar: frenar y seguir verificando solo con el JDK (pedidos HTTP a la app levantada), que es lo que ya funcionó.
  4. Si con el JDK tampoco funciona, frenar sin reintentar, analizar la causa (log, puerto ocupado, configuración) y contarla.
  5. Al terminar, detener la app: frenar la tarea en segundo plano y, si el puerto 8765 sigue ocupado, terminar ese proceso.
- **Producto:** la app es la cara del Google Sheet, que sigue siendo la fuente de verdad. Cada comercio entra con su propio Gmail y usa su propio Sheet, compartido con la cuenta de servicio, que es la que lee y escribe. Cada acción se escribe en el Sheet en el momento.
