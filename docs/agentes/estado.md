# Estado del proyecto: memoria del equipo de agentes

> Todos los agentes leen este archivo al empezar. El líder lo actualiza cuando termina cada tarea: backlog, decisiones y aprendizajes.
> Última actualización: 2026-10-14 (diagnóstico inicial).

## Producto
- **Qué es:** app local, en Windows, para comercios de cartas Magic: The Gathering. Es la "cara" del Google Sheet del comercio, y el Sheet sigue siendo la fuente de verdad.
- **Usuarios:** cada comercio entra con su propio Gmail y su propio Sheet. El piloto es el local de un amigo de quien lleva el proyecto; si funciona, se lleva a más comercios.
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

**Pestañas del Sheet:**
- **Inventario** (A:L): Cantidad, Nombre, Código de set, Set Name, Card Number, Condición, Printing, Idioma, Precio Local, Precio CK USD, Acción.
- **Movimientos** (A:K).
- **Caja** (A:K).
- **Reservas** (A:P).
- **Clientes** (A:D).

Las columnas se ubican por alias del encabezado (`GoogleSheetsService.java` ~2524).

## Google: login funcionando (HU-000)
1. Proyecto **TCG Inventory** en Google Cloud con el Gmail del proyecto `TCGStorehelper@gmail.com` (dueño y usuario de prueba). Cliente OAuth "App de escritorio".
2. Las claves van en `config/google-oauth.properties` (ignorado por git; ejemplo en `config/google-oauth.properties.example`). Las variables de entorno siguen teniendo prioridad. La contraseña del Gmail no se guarda en ningún lado.
3. Guía paso a paso: `docs/google/guia-credenciales-oauth.md`.
4. **Modo Prueba:** solo pueden entrar los Gmail cargados como testers, y la sesión vence a los 7 días.
5. **Para varios comercios:** publicar sin verificar (tope de 100 usuarios), verificar `spreadsheets` o migrar a `drive.file` con Google Picker (sección 8 de la guía). Decisión pendiente.
6. El login de Google no se puede automatizar en E2E: se usa el token guardado en `data/`.

## Backlog
| Prio | Tema | Detalle |
|---|---|---|
| P1 | Documento funcional base | Documentar los módulos de la v1 que faltan (INV, PRE, RES, CAJ, MOV, IMP, CFG) para llegar a 1.0.0. |
| P2 | Publicar la app | Decidir opción A/B/C de la guía de Google para abrir a otros comercios. |
| P1 | Escritura por fila | `GoogleSheetsService.updateQuantity` (~240-268) escribe por `rowIndex` sin verificar que la fila siga siendo esa carta. Si alguien ordena o borra filas a mano, escribe en otra carta. Lectura y escritura sin lock. |
| P1 | Cache Card Kingdom | La descarga no está sincronizada y la escritura no es atómica (`CardKingdomApiService`). Si hay más de un resultado posible para una carta, devuelve `null` y no actualiza el precio. |
| P1 | Token vencido | Detectar `invalid_grant` y pedir reconexión con un mensaje claro. |
| P2 | OAuth prolijo | Agregar `state` anti-CSRF y revocar el token al desconectar. |
| P2 | Controller gigante | `DashboardController` tiene ~9000 líneas. Extraer servicios por área (reservas, caja/reportes, importación), de a una. |
| P2 | Errores | 51 `catch (Exception)` genéricos. Si la cantidad no es numérica, se convierte en 0 sin avisar (`GoogleSheetsService` ~255). |
| P2 | Tests | No hay tests de Card Kingdom, escritura en Sheets ni reservas de punta a punta. |
| P3 | Código sin uso | Confirmar si `PriceComparisonService` y `PricingSettingsService` se usan. |
| P3 | Ruta por defecto | Si falta `app.storage-dir`, `StoreSettingsService` usa `D:/TCG-inventory/data` (`application.properties` ya define `./data`). |

## Decisiones tomadas
- 2026-10-14: equipo con líder (sesión principal) y especialistas `backend-java`, `frontend-diseno`, `google-integracion` y `revisor-qa`.
- 2026-10-14: **v2 planificada** (ver `roadmap-v2.md`): API REST `/api/v1` en español, React + TS + Vite, OpenAPI, repositorios Sheets/EnMemoria, E2E con Cucumber (Gherkin en español) + Playwright Java. Migración por módulos en ramas propias. El negocio va en español y los sufijos de Spring en inglés.
- 2026-10-14: equipo reorganizado. Quedan `ux-disenador`, `backend-java`, `frontend`, `google-integracion`, `qa-camino-feliz` y `qa-casos-error`. El líder trabaja con historias de usuario (skills `/historia-nueva`, `/continuar-historia`, `/analizar-defectos`). La aceptación final la da la persona a cargo; QA da el veredicto técnico.
- 2026-10-14: se suma `analista-funcional` con un documento funcional único y versionado (`docs/funcional/documento-funcional.md`, v0.1.0). Primera historia sugerida: HU-000, base de la v1 → 1.0.0.
- 2026-10-14: UX trabaja con spec y prototipos HTML, sin Figma. El gatito es la mascota oficial y no se modifica.
- 2026-10-14: locators con `data-testid="<pantalla>-<elemento>[-<id>]"` (`docs/v2/locators.md`).
- 2026-10-14: HU-000 aceptada. Credenciales de Google en archivo local ignorado; login con `TCGStorehelper@gmail.com` funcionando. Commits sin co-autoría del agente.
- 2026-10-14: JDK 21 (Temurin) en `C:\Users\sofii\.jdks\temurin-21.0.12.1`. El proyecto compila.

## Glosario
El glosario se movió a `docs/funcional/documento-funcional.md`, sección "Glosario".

## Aprendizajes
_(Anotar acá lo que se descubra trabajando: trampas del Sheet, comportamientos raros, preferencias de la persona a cargo del proyecto.)_
- 2026-10-14: `spring-boot:run` arranca `DesktopLauncher`: abre una ventana propia en un puerto al azar (no 8080) y Windows pide permiso de firewall para Java la primera vez.
- 2026-10-14: estado base. `mvn -q test` pasa (exit 0). `BackendApplicationTests` levanta el contexto completo y descarga la pricelist real de Card Kingdom, así que necesita internet.
