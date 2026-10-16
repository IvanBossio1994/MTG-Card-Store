# Estado del proyecto: memoria del equipo de agentes

> Todos los agentes leen este archivo al empezar. El líder lo actualiza cuando termina cada tarea: backlog, decisiones y aprendizajes.
> Última actualización: 2026-10-15 (HU-011 aceptada: Spotless, ArchUnit, ESLint + Prettier en el build). Sigue: HU-012 (EP-002, refactor del back de Configuración). HU-009 (EP-001, QA de casos de error) queda para después del estilo de QA.

## Producto
- **Qué es:** app local, en Windows, para comercios de cartas Magic: The Gathering. Es la "cara" del Google Sheet del comercio, y el Sheet sigue siendo la fuente de verdad.
- **Usuarios:** cada comercio entra con su propio Gmail y usa su propio Sheet, compartido con la cuenta de servicio (Google, punto 7). El piloto es el local de un amigo de quien lleva el proyecto; si funciona, se lleva a más comercios.
- **Funciones:** buscar cartas, vender, cargar stock, reservas y pedidos, ver quién pidió qué primero, movimientos, caja y resúmenes mensuales.

## Qué existe hoy
| Área | Dónde |
|---|---|
| Búsqueda e inventario | `GET /` → `dashboard.html`; `/api/cartas/sugerencias`; `POST /inventory/quantity`, `/inventory/cards` |
| Precios Card Kingdom | `POST /actualizar`, sincronización `@Scheduled` cada ~1 h, `POST /configuracion/precio` |
| Reservas y pedidos | `GET /reservas` → `reservations.html`: crear, entregar, separar, retiro, pedido masivo, alertas |
| Movimientos, caja y reporte mensual | `GET /movimientos` (con clave) → `movements.html` |
| Importar lista | `/importar-lista` → `import-list.html` |
| Configuración y Google | `GET /configuracion` → `store.html`; OAuth en `/configuracion/google/oauth/*` |
| Tutorial | `/tutorial/completar`, `/tutorial/reset` |
| App de escritorio | `DesktopLauncher`, `desktop-shell/` (C#), `scripts/build-windows-app.ps1` |
| **v2 (en construcción)** | `/app` (React, marco con barra lateral), `GET /api/v1/estado-app`, `/swagger-ui`, perfil `e2e`, E2E Cucumber + Playwright (`mvn.cmd -q test "-Dcucumber.filter.tags=@critico"`, guía en `qa-guia.md`). Épica en curso: `docs/agentes/epicas/EP-001-configuracion-v2.md` |

**Pestañas del Sheet:**
- **Inventario** (A:L): Cantidad, Nombre, Código de set, Set Name, Card Number, Condición, Printing, Idioma, Precio Local, Precio CK USD, Acción.
- **Movimientos** (A:K).
- **Caja** (A:K).
- **Reservas** (A:P).
- **Clientes** (A:D).

Las columnas se ubican por alias del encabezado (`GoogleSheetsService.java` ~2524).

## Google
1. Proyecto **TCG Inventory** en Google Cloud con el Gmail del proyecto `TCGStorehelper@gmail.com` (dueño y usuario de prueba). Cliente OAuth "App de escritorio".
2. Las claves van en `config/google-oauth.properties` (ignorado por git; ejemplo en `config/google-oauth.properties.example`). Las variables de entorno siguen teniendo prioridad. La contraseña del Gmail no se guarda en ningún lado.
3. Guía paso a paso: `docs/google/guia-credenciales-oauth.md`.
4. **Modo Prueba:** solo pueden entrar los Gmail cargados como testers, y la sesión vence a los 7 días.
5. **Para varios comercios:** publicar sin verificar (tope de 100 usuarios), verificar `spreadsheets` o migrar a `drive.file` con Google Picker (sección 8 de la guía). Decisión pendiente.
6. El login de Google no se puede automatizar en E2E. Por eso QA no inicia sesión: accede al Sheet de QA con la cuenta de servicio (punto 7).
7. **Cuenta de servicio, DECIDIDO 2026-10-14 (modelo mixto):**
   - **Login:** el usuario entra a la app con su Gmail (OAuth, como hoy). Lo hace a mano, una vez; la contraseña nunca se guarda ni la escribe un agente.
   - **Lectura y escritura del Sheet:** las hace la cuenta de servicio `tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com`. Cada Sheet se comparte con ese mail como **editor**.
   - **Sincronización inmediata:** cada acción de la app se escribe en el Sheet en el momento.
   - **Clave del robot:** archivo JSON en `config/google-service-account.json`. Lo ignora git (`*service-account*.json`) y lo coloca la dueña; los agentes no leen su contenido.
   - **Sheets:**
     - **QA (E2E):** `1wWH5bJV3kqXHyLK4zorxnGcGsZ_bRHrKXmn4DGgrG2I`. Lo usan solo las pruebas automáticas.
     - **Pruebas de la dueña:** `1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ`. Lo usa ella con la app normal.
     - **Sheet del local:** ninguna prueba lo toca.
   - **Riesgo a resolver antes de distribuir a otros comercios:** la clave del robot dentro de la app instalada da acceso a todos los Sheets compartidos con él.

## Backlog
| Prio | Tema | Detalle |
|---|---|---|
| P1 | Cuenta de servicio en la v2 | Hecho en HU-003 (`compartido/AccesoSheet`, `google/AccesoSheetCuentaServicio`). Falta que la usen los demás módulos. |
| P1 | Escritura por fila | `GoogleSheetsService.updateQuantity` (~240-268) escribe por `rowIndex` sin verificar que la fila siga siendo esa carta. Si alguien ordena o borra filas a mano, escribe en otra carta. Lectura y escritura sin lock. **Se resuelve en la épica de Inventario, sobre la base de acceso al Sheet que arma HU-003** (decidido 2026-10-15). |
| P1 | Token vencido | Hecho en la v2 (HU-003): 401 "Tu sesión de Google venció." con el link para volver a entrar. La v1 sigue igual. |
| P1 | Cache Card Kingdom | La descarga no está sincronizada y la escritura no es atómica (`CardKingdomApiService`). Si hay más de un resultado posible para una carta, devuelve `null` y no actualiza el precio. Fase 3 (precios). |
| P1 | Documento funcional base | Documentar los módulos de la v1 que faltan (INV, PRE, RES, CAJ, MOV, IMP) para llegar a 1.0.0. Conviene hacerlo al arrancar cada épica de módulo. |
| P2 | E2E con Sheet temporal | Hoy QA usa un Sheet fijo de QA. Mejora: que cada corrida cree un Sheet nuevo, lo use y lo borre al terminar. Va en otra rama (pedido de la dueña, 2026-10-14). |
| P2 | Publicar la app | Decidir opción A/B/C de la guía de Google para abrir a otros comercios, y resolver el riesgo de la clave de la cuenta de servicio (Google, punto 7). |
| P2 | OAuth prolijo | Agregar `state` anti-CSRF y revocar el token al desconectar. |
| P2 | Controller gigante | `DashboardController` tiene ~9000 líneas. Extraer servicios por área (reservas, caja/reportes, importación), de a una. |
| P2 | Errores | 51 `catch (Exception)` genéricos. Si la cantidad no es numérica, se convierte en 0 sin avisar (`GoogleSheetsService` ~255). |
| P2 | Tests | No hay tests de Card Kingdom, escritura en Sheets ni reservas de punta a punta. |
| P3 | Aviso tras el login | La dueña pide sacar el cartel "Google conectado. Ya podés usar la app." después de iniciar sesión: es obvio que conectó. Tenerlo en cuenta al diseñar el login de la v2 (2026-10-15). |
| P3 | Código sin uso | Confirmar si `PriceComparisonService` y `PricingSettingsService` se usan. |
| P3 | Ruta por defecto | Si falta `app.storage-dir`, la v1 (`StoreSettingsService`, `PricingSettingsService`) usa `D:/TCG-inventory/data` (`application.properties` ya define `./data`). En la v2 se corrigió a `./data` (HU-003). La cache de Card Kingdom de esta PC está en `C:\TCG-inventory\cache` (pedido de la dueña). |

## Decisiones tomadas
> Las decisiones vigentes ya están en `CLAUDE.md` y en las reglas. Esta lista es el historial; si una entrada vieja choca con una nueva, manda la nueva.

- 2026-10-14: **v2 planificada** (ver `roadmap-v2.md`): API REST `/api/v1` en español, React + TS + Vite, OpenAPI, repositorios Sheets/EnMemoria y E2E con Cucumber + Playwright Java. Migración por módulos en ramas propias. El negocio va en español y los sufijos de Spring en inglés.
- 2026-10-14: equipo: `analista-funcional`, `ux-disenador`, `backend-java`, `frontend`, `google-integracion`, `qa-camino-feliz` y `qa-casos-error`. El líder trabaja con historias de usuario. La aceptación final la da la persona a cargo; QA da el veredicto técnico.
- 2026-10-15: se elimina `google-integracion`. Su conocimiento pasa a `backend-java` (sección Google) y las guías de la consola de Google las escribe el líder. Del plugin Superpowers se adaptan, sin instalarlo, la verificación antes de entregar y la depuración sistemática de `DEF` (`backend-java`, `frontend`, `analizar-defectos`).
- 2026-10-14: entrada única `/agente-lider`, un menú que llama a las skills de historias. Ramas: `v2/feature/HU-XXX-<tema>`, que salen de `v2/dev` y se llaman igual que el archivo de la historia.
- 2026-10-14: **v1 aislada en `legacy`** (`com.tcg.bot.legacy`, `templates/legacy/`, `static/legacy/`). Sigue andando hasta el corte y sirve de referencia. Ya existen los paquetes v2 y `frontend/src/`. `GoogleOAuthService` pasó a `google/` (v2). Pendiente: sacarle la dependencia de `legacy.service.StoreSettingsService` en la fase 2. Regla: `estructura-carpetas.md`.
- 2026-10-14: se suma `analista-funcional` con un documento funcional único y versionado (`docs/funcional/documento-funcional.md`, v0.1.0). Primera historia sugerida: HU-000, base de la v1 → 1.0.0.
- 2026-10-14: UX trabaja con spec y prototipos HTML, sin Figma. El gatito es la mascota oficial y no se modifica.
- 2026-10-14: locators con `data-testid="<pantalla>-<elemento>[-<id>]"` (`docs/v2/locators.md`).
- 2026-10-14: HU-000 aceptada. Credenciales de Google en archivo local ignorado; login con `TCGStorehelper@gmail.com` funcionando.
- 2026-10-14: **Reglas de código y contratos** (pedido de la dueña): Javadoc corto con ejemplo en todo método público (TSDoc en el front) y contrato escrito de cada endpoint en `docs/v2/api/<modulo>.md`, que lo hace backend antes de programar y lo usa QA. Está en los agentes `backend-java`, `frontend` y `qa-*`.
- 2026-10-14: **HU-001 aceptada:** diseño de Configuración v2, primera historia de la épica EP-001. Próximas: HU-002 (base técnica) y HU-003 (Configuración funcionando).
  - Quedan en `docs/v2/ux/configuracion/` (spec y prototipo), el sistema de diseño, `docs/v2/textos.md` y `docs/v2/locators.md`.
  - Reglas de diseño: estilo cozy con acento azul y barra lateral; nada se mueve; columnas alineadas; tamaños fijos; regla de espera; errores específicos.
  - Reglas del equipo: épicas, consumo de tokens por HU, agente nuevo para cada vuelta de cambios, cuándo no usar agente y revisar los prototipos en Chrome con `jwebserver`.
- 2026-10-14: **HU-002 aceptada:** base técnica v2.
  - **Backend:** `compartido` con `EstadoAppController` (`/api/v1/estado-app`), `ManejadorErroresApi` (ProblemDetail en español), `ModoPruebaConfig` (perfil `e2e`) y `FrontendConfig` (`/app`), más springdoc.
  - **Frontend:** React, TS, Tailwind 4, shadcn y Lucide en `frontend/`, compilado por Maven (Node 22), con `MarcoApp`.
  - **Reglas nuevas:** todo por módulo, sin archivos "porque sí", composición en lugar de herencia, código moderno, nombres descriptivos, contrato primero, compilar no es testear y commits con el formato `[agente] descripción`.
- 2026-10-14: **una HU por tipo de trabajo** (diseño, backend, frontend y QA) y **una sola HU de QA por épica**, al final. Nadie prueba sobre el Sheet del local: QA usa el Sheet de QA y la dueña, su Sheet de pruebas (Google, punto 7). EP-001 queda con 5 HU.
- 2026-10-15: revisión de coherencia de `CLAUDE.md`, agentes, skills, plantillas y guías. Gherkin en tercera persona con nombre `[Pantalla] acción` y `Scenario Outline` con el parámetro en el nombre (`qa-guia.md`).
- 2026-10-15: **la v1 es solo machete** (decisión de la dueña): lo que tiene que funcionar es la v2; la v1 sirve para entender cómo era cada flujo y no se le hacen arreglos de convivencia. Si se guarda la configuración en la v2, la v1 la ve recién al reiniciar la app (HU-003). Hoy nadie usa la app en el local porque no está completa.
- 2026-10-15: **HU-003 aceptada:** API `/api/v1/configuracion` (contrato en `docs/v2/api/configuracion.md`), acceso al Sheet con la cuenta de servicio, arreglo de la pestaña (renombrar o crear), errores 400/401/403/500. Probada contra el Sheet de pruebas, que pasa a ser el configurado en esta PC. `TcgBotApplication` arranca en el 8765. Costó 197 mil tokens de agentes (una sola tarea).
- 2026-10-15: **HU-004 aceptada:** pantalla Configuración v2 en `/app` (React), con `configuracionApi.ts` y piezas comunes en `frontend/src/compartido/` (Tarjeta, Campo, Boton, MensajeBloque, CapaCarga, AvisoSalirSinGuardar, `useGuardarBloque`, `clienteApi`). Costó 305 mil tokens en 2 tareas (171 + 134 mil). Reglas nuevas de textos: los errores dicen cómo va el campo, todo campo lleva su ejemplo ("Ej.: …") y los errores van en letra chica sin negrita. Documento funcional 0.4.0.
- 2026-10-15: **Textos en tono impersonal y en singular** (pedido de la dueña): nada de "Ingresá…", "la creamos" ni "No pudimos…". Se reescribe toda la biblioteca en HU-005.
- 2026-10-15: **HU-005 aceptada:** biblioteca de textos en tono nuevo; bloque del Sheet en dos filas (conectar + "Traer cartas de" / renombrar con lista). "Traer cartas de": la app sugiere la pestaña con encabezados de inventario, crea la del inventario en el formato de la app con esas cartas y no toca la de origen (reemplaza a "copiar la actual"). Costó 216 mil tokens en 3 llamadas; desde esta HU se aplican las reglas de consumo (`docs/consumo-tokens.md`): la última vuelta de diseño midió 7 puntos de ventana y 1 de semana.
- 2026-10-15: **HU-006 aceptada:** `GET /sheet/pestanas[?idOLink]`, `PUT /sheet/pestanas/renombrar` y `PUT /sheet` con `traerCartasDe` (copia valores en una escritura; ya no renombra salvo la pestaña vacía de un Sheet nuevo). Alias de columnas copiados a `configuracion/ColumnasInventario`. Caja, Movimientos, Reservas y Clientes no se renombran ni se sugieren (`fija`). Si falla guardar después de renombrar, se deshace en el Sheet. Textos del back en tono nuevo. Probada contra el Sheet de pruebas. Costó 304 mil tokens en 2 tareas (78 + 226 mil); medición en `docs/consumo-tokens.md`.
- 2026-10-15: **HU-007 aceptada:** Configuración v2 con textos en tono nuevo y bloque del Sheet en dos filas ("Traer cartas de" con la sugerida preseleccionada; renombrar con lista, fijas deshabilitadas). `compartido/ListaDesplegable.tsx` (select nativo). Costó 232 mil tokens en 1 tarea (16 puntos de ventana, 2 de semana) más 3 puntos de la verificación del líder. Documento funcional 0.5.0. La carpeta de cache CK queda en Precios (se guarda con la regla de precio).
- 2026-10-15: **la v1 puede romperse** (la dueña): lo que importa es la v2; si algo queda raro, se arregla para mejor en la v2 sin arreglos de convivencia.
- 2026-10-15: **Carpetas de QA por tipo** (`features/`, `e2e/paginas/`, `e2e/localizadores/`, `e2e/pasos/`), con un archivo por módulo en cada una. Front y back siguen por módulo.
- 2026-10-15: **HU de backend en tareas:** contrato y riesgos primero (revisión de la dueña), infraestructura nueva aparte, hasta 6 endpoints por tarea (`CLAUDE.md`, ciclo, paso 3).
- 2026-10-15: **EP-002 · estilo de código común** (back y front; QA postergado hasta el ejemplo de la dueña): guía (HU-010) → herramientas (HU-011) → refactor de back (HU-012) → refactor de front (HU-013, con DEF-001); estilo de QA en HU-014 (planificada, espera el ejemplo). Front: TanStack Query sí; React Hook Form + Zod no (el front valida solo vacíos, el resto el back). Solo la v2.
- 2026-10-15: **HU-011 aceptada:** el build hace cumplir la guía en la v2: Spotless + palantir-java-format (corrige solo; `mvn spotless:check` para revisar), `ArquitecturaV2Test` (ArchUnit, 4 reglas, sin excepciones), ESLint 10 + Prettier 3 en el front (6 excepciones `HU-013`). Excepciones: solo por línea y con motivo (`estilo-codigo.md`, sección 4). Node 22.20: en otra PC, borrar `frontend/node`. Costó 175 mil tokens en 3 tareas (~10 puntos de ventana).
- 2026-10-15: **bugs conocidos** (la dueña): tag `@HU-XXX-DEF-YYY`, se ejecuta y se reporta como "bug conocido"; ficha del `DEF` con precondiciones y datos; en git solo la captura. Reglas en `qa-guia.md`; el aviso en el reporte va en HU-014.
- 2026-10-15: **regla de épicas ampliada** (`CLAUDE.md`): mismo objetivo = misma épica, aunque se pida en otro momento; antes de crear algo se revisan las épicas abiertas; una HU suelta que necesita otra se convierte en épica.
- 2026-10-15: **herramientas del equipo** (investigación en `investigacion-herramientas-agentes.md`): Node 24 LTS + Playwright CLI (skill en `.claude/skills/playwright-cli/`) para revisar prototipos en lugar de Chrome; permisos en `.claude/settings.json` (claves de `config/` bloqueadas, commit/push piden confirmación, `git commit -m` bloqueado); `maxTurns` en cada agente; backend y frontend entregan la línea de resultado de los tests como evidencia; `code-review` (`medium`, solo errores de corrección) antes de la verificación del líder. MCP de IntelliJ pendiente (lo activa la dueña). El modo automático no deja editar `.claude/settings.json`: se hace en modo manual. Pendiente con análisis aparte: achicar `CLAUDE.md` pasando "Modo líder" a la skill del líder.
- 2026-10-15: **revisión de privacidad del repo público** (GitHub `IvanBossio1994/MTG-Card-Store`, 125 commits, todas las ramas): ninguna clave ni token en la historia (OAuth, clave privada, tokens, API keys) y nunca se subió un archivo de claves. Sí son públicos: el Gmail del proyecto, el mail de la cuenta de servicio y los IDs de los Sheets de QA y de pruebas. No dan acceso solos; la protección real es que cada Sheet tenga "Acceso general: Restringido".
- 2026-10-14: el proyecto requiere JDK 21 (Temurin). La ruta de cada PC va en `CLAUDE.local.md`.
- 2026-10-15: **otra PC u otro usuario:** la primera vez, `/agente-lider` aplica `preparar-entorno`. Revisa e instala, con OK, JDK 21, Git y Maven (`.tools\`, que git no versiona). Verifica que estén las 2 claves de `config/` (la persona a cargo las pasa por un canal privado) y crea `CLAUDE.local.md`. Se usan los mismos usuarios de Google; `data/` no se pasa y cada uno inicia sesión una vez.

## Glosario
El glosario se movió a `docs/funcional/documento-funcional.md`, sección "Glosario".

## Aprendizajes
_(Anotar acá lo que se descubra trabajando: trampas del Sheet, comportamientos raros, preferencias de la persona a cargo del proyecto.)_
- 2026-10-14: `spring-boot:run` arranca `DesktopLauncher` por defecto: abre una ventana propia en un puerto al azar y apaga la app al cerrarla. Para levantarla sin ventana: `-Dapp.clase-principal=com.tcg.bot.TcgBotApplication` (propiedad del `pom.xml`, HU-002). Windows pide permiso de firewall para Java la primera vez.
- 2026-10-15: `data/google-oauth-token` da `AccessDeniedException` al leer su dueño (`FileDataStoreFactory.setPermissionsToOwnerOnlyWindows`), **también con la app levantada por la dueña fuera de Claude Code**: no era el sandbox, son los permisos de esa carpeta en esta PC. La carpeta estaba a nombre de otro usuario de Windows; se arregla, como administrador, con `takeown /F <carpeta> /R /D S` y `icacls <carpeta> /reset /T`. **La app nunca se levanta como administrador** (las carpetas que crea quedan a nombre del administrador): se usa la terminal del IDE. Al pegar comandos en la terminal se puede perder el último carácter: revisarlo antes de Enter. El líder puede hacer pedidos HTTP a `127.0.0.1:8765` (permitidos en `.claude/settings.local.json`).
- 2026-10-14: la extensión de Chrome no abre `file://`. Para ver un prototipo se sirve el repo con `jwebserver` del JDK (puerto 8765).
- 2026-10-14: HU-001 costó 349 mil tokens; el 92 % se fue en retomar al mismo agente UX en cada vuelta. Con un agente nuevo por vuelta y los ajustes chicos hechos por el líder, las últimas 8 vueltas no sumaron tokens de agentes.
- 2026-10-14: estado base. `mvn -q test` pasa (exit 0). `BackendApplicationTests` levanta el contexto completo y descarga la pricelist real de Card Kingdom, así que necesita internet.
