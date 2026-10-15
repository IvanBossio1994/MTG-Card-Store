# Estado del proyecto: memoria del equipo de agentes

> Todos los agentes leen este archivo al empezar. El líder lo actualiza cuando termina cada tarea: backlog, decisiones y aprendizajes.
> Última actualización: 2026-10-15 (revisión de coherencia del equipo de agentes). Próxima: HU-003 (EP-001).

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
| **v2 (en construcción)** | `/app` (React, marco con barra lateral), `GET /api/v1/estado-app`, `/swagger-ui`, perfil `e2e`. Épica en curso: `docs/agentes/epicas/EP-001-configuracion-v2.md` |

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
| P1 | Cuenta de servicio en la v2 | Decidido (Google, punto 7). **Se implementa en HU-003.** |
| P1 | Escritura por fila | `GoogleSheetsService.updateQuantity` (~240-268) escribe por `rowIndex` sin verificar que la fila siga siendo esa carta. Si alguien ordena o borra filas a mano, escribe en otra carta. Lectura y escritura sin lock. **Se resuelve con la base de acceso al Sheet de la v2 (HU-003).** |
| P1 | Token vencido | Detectar `invalid_grant` y pedir reconexión con un mensaje claro. **HU-003.** |
| P1 | Cache Card Kingdom | La descarga no está sincronizada y la escritura no es atómica (`CardKingdomApiService`). Si hay más de un resultado posible para una carta, devuelve `null` y no actualiza el precio. Fase 3 (precios). |
| P1 | Documento funcional base | Documentar los módulos de la v1 que faltan (INV, PRE, RES, CAJ, MOV, IMP) para llegar a 1.0.0. Conviene hacerlo al arrancar cada épica de módulo. |
| P2 | E2E con Sheet temporal | Hoy QA usa un Sheet fijo de QA. Mejora: que cada corrida cree un Sheet nuevo, lo use y lo borre al terminar. Va en otra rama (pedido de la dueña, 2026-10-14). |
| P2 | Publicar la app | Decidir opción A/B/C de la guía de Google para abrir a otros comercios, y resolver el riesgo de la clave de la cuenta de servicio (Google, punto 7). |
| P2 | OAuth prolijo | Agregar `state` anti-CSRF y revocar el token al desconectar. |
| P2 | Controller gigante | `DashboardController` tiene ~9000 líneas. Extraer servicios por área (reservas, caja/reportes, importación), de a una. |
| P2 | Errores | 51 `catch (Exception)` genéricos. Si la cantidad no es numérica, se convierte en 0 sin avisar (`GoogleSheetsService` ~255). |
| P2 | Tests | No hay tests de Card Kingdom, escritura en Sheets ni reservas de punta a punta. |
| P3 | Código sin uso | Confirmar si `PriceComparisonService` y `PricingSettingsService` se usan. |
| P3 | Ruta por defecto | Si falta `app.storage-dir`, `StoreSettingsService` usa `D:/TCG-inventory/data` (`application.properties` ya define `./data`). |

## Decisiones tomadas
> Las decisiones vigentes ya están en `CLAUDE.md` y en las reglas. Esta lista es el historial; si una entrada vieja choca con una nueva, manda la nueva.

- 2026-10-14: **v2 planificada** (ver `roadmap-v2.md`): API REST `/api/v1` en español, React + TS + Vite, OpenAPI, repositorios Sheets/EnMemoria y E2E con Cucumber + Playwright Java. Migración por módulos en ramas propias. El negocio va en español y los sufijos de Spring en inglés.
- 2026-10-14: equipo: `analista-funcional`, `ux-disenador`, `backend-java`, `frontend`, `google-integracion`, `qa-camino-feliz` y `qa-casos-error`. El líder trabaja con historias de usuario. La aceptación final la da la persona a cargo; QA da el veredicto técnico.
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
- 2026-10-14: JDK 21 (Temurin) en `C:\Users\sofii\.jdks\temurin-21.0.12.1`. El proyecto compila.

## Glosario
El glosario se movió a `docs/funcional/documento-funcional.md`, sección "Glosario".

## Aprendizajes
_(Anotar acá lo que se descubra trabajando: trampas del Sheet, comportamientos raros, preferencias de la persona a cargo del proyecto.)_
- 2026-10-14: `spring-boot:run` arranca `DesktopLauncher` por defecto: abre una ventana propia en un puerto al azar y apaga la app al cerrarla. Para levantarla sin ventana: `-Dapp.clase-principal=com.tcg.bot.TcgBotApplication` (propiedad del `pom.xml`, HU-002). Windows pide permiso de firewall para Java la primera vez.
- 2026-10-14: la extensión de Chrome no abre `file://`. Para ver un prototipo se sirve el repo con `jwebserver` del JDK (puerto 8765).
- 2026-10-14: HU-001 costó 349 mil tokens; el 92 % se fue en retomar al mismo agente UX en cada vuelta. Con un agente nuevo por vuelta y los ajustes chicos hechos por el líder, las últimas 8 vueltas no sumaron tokens de agentes.
- 2026-10-14: estado base. `mvn -q test` pasa (exit 0). `BackendApplicationTests` levanta el contexto completo y descarga la pricelist real de Card Kingdom, así que necesita internet.
