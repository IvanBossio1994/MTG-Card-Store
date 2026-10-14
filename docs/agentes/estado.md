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

## Google: por qué no funciona el login
1. Faltan las variables `GOOGLE_OAUTH_CLIENT_ID` y `GOOGLE_OAUTH_CLIENT_SECRET` en esta PC, así que el login no arranca.
2. **Plan:** la persona a cargo del proyecto creó un Gmail nuevo para el proyecto. Con esa cuenta hay que:
   - crear un proyecto en Google Cloud y habilitar la Sheets API;
   - configurar la pantalla de consentimiento (modo Prueba) y agregar usuarios de prueba;
   - crear un cliente OAuth de tipo **aplicación de escritorio**;
   - definir las variables de entorno.
3. **Modo Prueba:** solo pueden entrar los Gmail cargados como testers, y la sesión vence a los 7 días.
4. **Para varios comercios** hay que verificar la app ante Google (`spreadsheets` es un scope sensible) o migrar a `drive.file` con Google Picker. Decisión pendiente.

## Backlog
| Prio | Tema | Detalle |
|---|---|---|
| P0 | Credenciales OAuth | Pasos del punto anterior. Responsable: `google-integracion`. |
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
- 2026-10-14: JDK 21 (Temurin) en `C:\Users\sofii\.jdks\temurin-21.0.12.1`. El proyecto compila.

## Glosario
El glosario se movió a `docs/funcional/documento-funcional.md`, sección "Glosario".

## Aprendizajes
_(Anotar acá lo que se descubra trabajando: trampas del Sheet, comportamientos raros, preferencias de la persona a cargo del proyecto.)_
- 2026-10-14: estado base. `mvn -q test` pasa (exit 0). `BackendApplicationTests` levanta el contexto completo y descarga la pricelist real de Card Kingdom, así que necesita internet.
