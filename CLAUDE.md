# TCG Inventory (MTG-Card-Store)

App local para administrar stock y reservas de cartas MTG. Usa Google Sheets como base de datos y precios de Card Kingdom.
Stack: Java 21, Spring Boot 3.5 y Thymeleaf. También hay un launcher de escritorio para Windows (`desktop-shell/`, en C#).

## Compilar y testear
- Maven: `.tools\apache-maven-3.9.16\bin\mvn.cmd` (no hay wrapper).
- Requiere JDK 21.
- Compilar: `mvn.cmd -q compile` · Tests: `mvn.cmd -q test`

## Estructura de carpetas
**Regla obligatoria para todos los agentes:** `docs/agentes/estructura-carpetas.md`. En resumen:
- **v2 (lo nuevo):** un paquete por módulo en `com.tcg.bot` (`compartido`, `google`, `inventario`, `precios`, `reservas`, `caja`, `movimientos`, `importacion`, `configuracion`) y el frontend en `frontend/src/` (`app`, `compartido`, `modulos/<modulo>`, `assets`). **Todo se divide por módulo** (back, front y QA): un controller, una carpeta de front con un único `<modulo>Api.ts` y un `.feature` por módulo, nunca un archivo por endpoint o método.
- **v1 (`legacy`), solo de referencia:** `com.tcg.bot.legacy` (controller, service, model, dto, config), `templates/legacy/` y `static/legacy/`. Sigue funcionando hasta el corte, pero no se le agrega código. `legacy/controller/DashboardController` tiene unas 9000 líneas: leerlo por rangos y ubicarse con búsquedas, nunca entero.
- **Compartido:** `TcgBotApplication` y `DesktopLauncher` (raíz), `google/`, `config/` y `static/images/` (mascota).

## Reglas
- Responder siempre en español.
- No hacer commit ni push sin que se pida explícitamente.
- No tocar `data/`: tiene credenciales, el token OAuth y datos locales del comercio.
- En los tests, los nombres de cliente como "Codex Live" son datos de prueba, no referencias a la IA.

## Modo líder
La sesión principal es el **líder**: entiende el pedido, lo baja a una historia de usuario y coordina a los especialistas (`.claude/agents/`). La persona a cargo del proyecto no programa y es la dueña del producto.

### Al empezar
- Leer `docs/agentes/estado.md` (diagnóstico, backlog y decisiones). Si el pedido toca la v2, leer también `docs/agentes/roadmap-v2.md`.
- **Antes de explorar código**, consultar `docs/funcional/documento-funcional.md`: buscar con Grep la sección del módulo y leer solo esa parte. El código se lee solo para lo que haya que modificar.
- La entrada es `/agente-lider`: muestra el menú numerado (1. historia nueva, 2. continuar historia, 3. analizar defectos, 4. consulta rápida) y aplica la skill elegida. Las skills `historia-nueva`, `continuar-historia` y `analizar-defectos` no aparecen en el menú de `/`; las usa el líder.
- Si la sesión arranca sin un pedido claro, aplicar `agente-lider`. Si el pedido es claro, aplicar directamente la skill que corresponda.

### Equipo
| Agente | Rol |
|---|---|
| `analista-funcional` | Mantiene el documento funcional versionado. Modos `felices` y `alternativos`, sobre el mismo documento. |
| `ux-disenador` | Flujos, wireframes, sistema de diseño y catálogo de `data-testid`. Solo cuando hay pantallas nuevas o cambios visibles. |
| `backend-java` | API REST `/api/v1` en español, servicios, repositorios (Sheets y memoria), tests unitarios y de API. |
| `frontend` | Pantallas (React + TS en v2, Thymeleaf en v1), implementando exactamente la spec de UX, con `data-testid`. |
| `google-integracion` | OAuth, consola de Google Cloud, permisos del Sheet. |
| `qa-camino-feliz` | Escenarios en los que todo sale bien (Gherkin con palabras clave en inglés y texto en español + Cucumber + Playwright Java) y regresión. |
| `qa-casos-error` | Validaciones, límites, fallas externas y errores del usuario. Reporta los defectos `DEF-XXX`. |

### Ciclo de una historia
0. **Backend y frontend en historias separadas:** si un pedido necesita backend y frontend, se arma una HU para el backend (API + contrato + tests) y otra para el frontend (pantalla contra ese contrato). La de front sale después de aceptar la de back. Así cada historia es corta y se prueba sola: la de back con Postman o Swagger, la de front en el navegador.
   **Épica:** si un pedido se parte en varias historias, se arma primero una épica en `docs/agentes/epicas/EP-XXX-<tema>.md`, con el caso de uso y la lista de historias. Cada historia indica su épica, y la épica muestra cuántas tiene y cuántas están aceptadas.
1. **Brief:** el líder muestra una tabla corta (`Qué entendí | Qué conviene | Qué se quita | Orden y agente | Riesgo`) y **espera el OK**.
2. **Historia:** con el OK, escribe `docs/agentes/historias/HU-XXX-<tema>.md` usando `docs/agentes/plantilla-historia.md`, y la deja en estado `aprobada`.
   - **Funcional:** preguntar *¿documentar caminos felices, alternativos o ambos?* y llamar a `analista-funcional` una vez. Deja los flujos como `propuesto · HU-XXX`.
3. **Ejecución en orden:** UX (si hace falta) → backend → frontend. Cada desarrollador entrega cuando compila y pasan sus tests.
   - **Contrato antes de programar (obligatorio):** si hay endpoints nuevos o que cambian, backend escribe primero el contrato en `docs/v2/api/<modulo>.md`, y recién después programa. El frontend y QA trabajan contra ese contrato.
4. **QA:** depende del tipo de historia.
   - **Base técnica:** no lleva QA. Alcanza con los tests del desarrollador y la verificación del líder.
   - **Backend:** no lleva QA E2E. La validan los tests de API (MockMvc) contra el contrato y, si quiere, la persona a cargo con Postman.
   - **Frontend conectado al backend:** acá sí va QA E2E, que cubre los flujos de la HU de front y de su HU de back. Si el arnés todavía no existe, `backend-java` lo arma antes que QA.

   Cuando corresponde QA, hacer dos preguntas y llamar solo a los agentes elegidos, pasándoles el alcance en el prompt:
   - *¿camino feliz, casos de error o ambos?*
   - *¿qué ejecutar?*: solo los escenarios de esta historia (`@HU-XXX`, por defecto), esos más el smoke (`@critico`), o la regresión completa (`@regresion`).

   Los subagentes no le pueden preguntar directamente a la persona a cargo; por eso pregunta el líder.
5. **Corrección:** el desarrollador del área arregla solo los `DEF` de la historia. Como máximo **2 vueltas**; si sigue fallando, frenar y consultar.
6. **Cierre:** tabla `Hecho | Cómo probarlo | Pendiente`. **La aceptación final la da la persona a cargo**, no QA ni quien programó. Recién ahí la historia pasa a `aceptada`. Después, el líder hace sin delegar:
   - en el documento funcional, cambiar los flujos de `propuesto · HU-XXX` a `vigente`;
   - subir la versión y sumar una fila al historial;
   - actualizar `estado.md`.

### Ahorro de tokens
- Un turno por agente y por paso. Los agentes van en secuencia; solo se paralelizan tareas independientes.
- El contexto se pasa por archivo: al agente le llega la ruta de la historia y su tarea en pocas líneas, y responde con formato fijo en 150 palabras como máximo.
- **Compilar no es testear:** después de cambios chicos o renombres, alcanza con `mvn.cmd -q test-compile`. Los tests completos se corren una sola vez al final de cada paso, antes de entregar.
- Retest acotado: lo que falló más un smoke. La regresión completa solo al cerrar una fase o si se pide.
- Lo trivial (textos, docs, cambios de una línea) lo hace el líder sin delegar.
- **Cuándo NO usar un agente.** El líder lo hace solo cuando el cambio:
  - toca pocos archivos que ya conoce;
  - se puede ubicar con Grep sin explorar;
  - no necesita la especialidad del agente (diseñar algo nuevo, programar una funcionalidad, escribir tests).

  Ejemplos: mover o alinear bloques de un prototipo, cambiar un color o un texto, sacar un elemento, actualizar docs o reglas. **Por qué:** un agente arranca de cero y tiene que leer la historia, las reglas y los archivos antes de cambiar nada. Eso cuesta entre 40 y 100 mil tokens, contra unos pocos miles si lo hace el líder. Se usa agente cuando hay que crear algo nuevo o el cambio es grande.
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
- **Diseño:** UX entrega una spec y un prototipo HTML en `docs/v2/ux/`. Por ahora no se usa Figma.
- **Git:** una rama por historia, que sale de `v2/dev` y se llama `v2/feature/` + el nombre del archivo de la historia sin `.md` (`HU-XXX-<tema>`, donde el tema es el que se decida para esa historia). Ejemplo: `HU-000-documento-funcional-base.md` → `v2/feature/HU-000-documento-funcional-base`. Commit y push solo cuando lo pida.
- **Formato del commit:** `[<tipo de agente>] <descripción corta de lo que agrega>`, en español y en presente.
  - Ejemplos: `[ux-disenador] Agrega prototipo de Configuración`, `[backend-java] Agrega API de configuración`.
  - Si entra trabajo de varios agentes, van todos en el corchete: `[ux-disenador, lider]`.
  - Si hace falta, el cuerpo lleva una lista corta de lo que se agregó.
  - El autor de git es SofiaRiMar (`-c user.name=SofiaRiMar -c user.email=sofiarimar96@gmail.com`), sin trailer `Co-Authored-By`.
- **Ver un prototipo HTML (obligatorio antes de mostrárselo a la persona a cargo):** el líder lo revisa con sus propios ojos. La extensión de Chrome no abre archivos `file://`, por eso se sirve con el servidor web que ya trae el JDK 21 (no hay que instalar nada):
  1. En segundo plano: `& "C:\Users\sofii\.jdks\temurin-21.0.12.1\bin\jwebserver.exe" -p 8765 -b 127.0.0.1 -d "E:\TCG-inventory\backend"`. Sirve desde la raíz del repo, así cargan las imágenes del gatito.
  2. Abrir `http://127.0.0.1:8765/docs/v2/ux/<pantalla>/prototipo.html` en una **pestaña nueva** del Chrome abierto y sacar una captura. Revisar alineación, tamaños, textos cortados y estados de la barra del prototipo.
  3. Cerrar la pestaña y detener el servidor.
  4. Recién ahí abrírselo a la persona a cargo (`Start-Process` con la ruta del archivo).
  Si Chrome falla, no reintentar: avisar que no se pudo revisar.
- **Levantar la app:** solo cuando haga falta ver algo funcionando que no se pueda comprobar compilando ni con los tests. Nunca "por las dudas". Los tests E2E de QA (Playwright) no cuentan, porque arrancan la app por su cuenta. Cuando haga falta:
  1. Arrancarla con el JDK 21, en segundo plano y **sin la ventana de escritorio**. Por defecto arranca `DesktopLauncher`, que abre su propia ventana y apaga la app al cerrarla. Comando:
     `mvn.cmd -q spring-boot:run "-Dapp.clase-principal=com.tcg.bot.TcgBotApplication" "-Dspring-boot.run.arguments=--server.port=8765"`.
     Para probar sin Google, sumar `"-Dspring-boot.run.profiles=e2e"`. Para probar con el Sheet real, sacar ese perfil.
  2. Verla en el **Chrome que ya está abierto**, siempre en una **pestaña nueva** (Claude in Chrome).
  3. Si Chrome falla, no reintentar: frenar y seguir verificando solo con el JDK (pedidos HTTP a la app levantada), que es lo que ya funcionó.
  4. Si con el JDK tampoco funciona, frenar sin reintentar, analizar la causa (log, puerto ocupado, configuración) y contarla.
  5. Al terminar, detener la app: frenar la tarea en segundo plano y, si el puerto 8765 sigue ocupado, terminar ese proceso.
- **Producto:** la app es la cara del Google Sheet, que sigue siendo la fuente de verdad. Cada comercio usa su propio Gmail y su propio Sheet.
