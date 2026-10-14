# Estructura de carpetas (regla para todos los agentes)

> **Lo nuevo va en las carpetas de la v2. `legacy` es solo de referencia: se lee para entender cómo funciona hoy, pero no se le agrega ni se le corrige código.**
> La versión vieja sigue funcionando (en `/`) hasta la fase de corte del roadmap. En esa fase se borra todo lo que está en `legacy`.

## Mapa

```
src/main/java/com/tcg/bot/
  TcgBotApplication.java, DesktopLauncher.java   arranque (compartido v1 y v2)
  compartido/      v2 · errores (ProblemDetail), utilidades, OpenAPI
  google/          v2 · OAuth (GoogleOAuthService) y cliente de Sheets de bajo nivel
  inventario/      v2 · un paquete por módulo, con:
  precios/              XController   (@RestController, /api/v1/...)
  reservas/             XService      (reglas de negocio)
  caja/                 XRepository   (interfaz) + XSheetsRepository + XEnMemoriaRepository
  movimientos/          dto/          (records con campos en español)
  importacion/
  configuracion/
  legacy/          v1 · SOLO REFERENCIA (controller, service, model, dto, config)

src/main/resources/
  application.properties     compartido
  templates/legacy/          v1 · pantallas Thymeleaf (spring.thymeleaf.prefix apunta acá)
  static/legacy/css, js/     v1 · app.css y app.js (se sirven en /legacy/css y /legacy/js)
  static/images/             compartido · mascota (cat-*, tutorial-cat-*) y logo por defecto

frontend/                    v2 · React + TypeScript + Vite (se sirve en /app)
  src/api/                   cliente de la API y tipos generados desde OpenAPI
  src/pages/                 una carpeta por pantalla (InventarioPage, ReservasPage, ...)
  src/components/            componentes reutilizables
  src/hooks/                 hooks compartidos
  src/styles/                tokens y estilos del sistema de diseño
  src/assets/                recursos propios del front (la mascota NO se copia: se usa desde /images/)

src/test/java/com/tcg/bot/
  <modulo>/                  v2 · tests unitarios y de API, en el mismo paquete que la clase
  e2e/pasos, paginas, soporte  v2 · Cucumber + Playwright (ver qa-guia.md)
  legacy/                    v1 · tests de la versión vieja (se mantienen verdes, no se agregan)
src/test/resources/features/<modulo>/   escenarios Gherkin

config/        claves locales (google-oauth.properties, ignorado por git) · compartido
docs/          documentación · data/ NO se toca
```

## Reglas
1. **Código nuevo, solo en v2.** Cada clase va en el paquete de su módulo. Si no está claro a qué módulo pertenece, preguntale al líder.
2. **`legacy` se usa como ejemplo.** Para migrar algo, se lee la lógica en `legacy/` (y el documento funcional) y se reescribe en el módulo v2 con la arquitectura nueva. No se copian clases enteras ni se importa nada de `legacy` desde la v2.
   - Hay una sola excepción pendiente: `google/GoogleOAuthService` usa `legacy.service.StoreSettingsService` para ubicar la carpeta del token. Se resuelve cuando se migre `configuracion` (fase 2).
3. **Lo que usan las dos versiones** (arranque, `google/`, `config/`, `static/images/`) no se mueve a `legacy`.
4. **Al migrar un módulo**, lo viejo queda como está hasta el corte. No se borra ni se modifica `legacy` durante el rework, salvo un arreglo urgente que pida la persona a cargo.
5. **Tests:** cada clase nueva lleva sus tests en `src/test/java/com/tcg/bot/<modulo>/`. Los tests de `legacy/` tienen que seguir pasando.
