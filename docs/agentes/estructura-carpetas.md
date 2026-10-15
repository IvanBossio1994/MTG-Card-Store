# Estructura de carpetas (regla para todos los agentes)

> **Lo nuevo va en las carpetas de la v2. `legacy` es solo de referencia: se lee para entender cómo funciona hoy, pero no se le agrega ni se le corrige código.**
> La versión vieja sigue funcionando (en `/`) hasta la fase de corte del roadmap. En esa fase se borra todo lo que está en `legacy`.

## Mapa

```
src/main/java/com/tcg/bot/
  TcgBotApplication.java, DesktopLauncher.java   arranque (compartido v1 y v2)
  compartido/      v2 · lo común: ManejadorErroresApi (ProblemDetail), ErrorDeNegocioException, EstadoAppController,
                   FrontendConfig (/app), ModoPruebaConfig (perfil e2e) y la base de acceso al Sheet
  google/          v2 · OAuth (GoogleOAuthService) y cliente de Sheets con la cuenta de servicio
  inventario/      v2 · un paquete por módulo, con:
  precios/              XController   (@RestController, /api/v1/..., uno por módulo)
  reservas/             XService      (solo si hay reglas de negocio)
  caja/                 XRepository   (interfaz) + XSheetsRepository + XEnMemoriaRepository
  movimientos/          dto/          (solo si un record lo usa más de una clase; si no, va adentro del controller)
  importacion/
  configuracion/
  legacy/          v1 · SOLO REFERENCIA (controller, service, model, dto, config)

src/main/resources/
  application.properties     compartido
  templates/legacy/          v1 · pantallas Thymeleaf (spring.thymeleaf.prefix apunta acá)
  static/legacy/css, js/     v1 · app.css y app.js (se sirven en /legacy/css y /legacy/js)
  static/images/             compartido · mascota (cat-*, tutorial-cat-*) y logo por defecto

frontend/                    v2 · React + TypeScript + Vite (se sirve en /app). Mapa objetivo: cada carpeta se crea con su primer archivo (regla 7)
  src/app/                   arranque: main.tsx, rutas y MarcoApp (barra lateral)
  src/compartido/            lo que usan todos los módulos
    componentes/             piezas del sistema de diseño: Tarjeta, Campo, Boton, MensajeBloque, CapaCarga…
    api/                     clienteApi.ts (fetch base y manejo de ProblemDetail) y tipos generados desde OpenAPI
    textos/                  textos.ts: la biblioteca de docs/v2/textos.md, con las mismas claves
    estilos/                 tokens del sistema de diseño (Tailwind)
    hooks/                   hooks generales
  src/modulos/<modulo>/      UNA carpeta por módulo (configuracion, inventario, reservas, caja…)
    <Modulo>Pagina.tsx       la pantalla del módulo (ej.: ConfiguracionPagina.tsx)
    componentes/             piezas propias del módulo (ej.: BloqueTienda.tsx, BloqueSheet.tsx)
    <modulo>Api.ts           TODAS las llamadas a la API del módulo en un solo archivo (ej.: configuracionApi.ts)
    *.test.tsx               tests Vitest del módulo, al lado de lo que prueban
  src/assets/                recursos propios del front (la mascota NO se copia: se usa desde /images/)

src/test/java/com/tcg/bot/
  <modulo>/                  v2 · tests unitarios y de API, en el mismo paquete que la clase
  e2e/soporte/               v2 · hooks, arranque de la app y del navegador (ver qa-guia.md)
  e2e/compartido/            v2 · PantallaBase y piezas comunes (MarcoApp, CapaCarga)
  e2e/<modulo>/              v2 · por módulo: <Modulo>Pasos.java (steps) y <Modulo>Pantalla.java (Page Object)
  legacy/                    v1 · tests de la versión vieja (se mantienen verdes, no se agregan)
src/test/resources/features/<modulo>/<modulo>.feature   escenarios Gherkin del módulo

config/        claves locales, ignoradas por git: google-oauth.properties y google-service-account.json · compartido
docs/          documentación · data/ NO se toca
```

## Reglas
1. **Código nuevo, solo en v2.** Cada clase va en el paquete de su módulo. Si no está claro a qué módulo pertenece, preguntale al líder.
2. **`legacy` se usa como ejemplo.** Para migrar algo, se lee la lógica en `legacy/` (y el documento funcional) y se reescribe en el módulo v2 con la arquitectura nueva. No se copian clases enteras ni se importa nada de `legacy` desde la v2.
   - Hay una sola excepción pendiente: `google/GoogleOAuthService` usa `legacy.service.StoreSettingsService` para ubicar la carpeta del token. Se resuelve al migrar `configuracion` (HU-003).
3. **Lo que usan las dos versiones** (arranque, `google/`, `config/`, `static/images/`) no se mueve a `legacy`.
4. **Al migrar un módulo**, lo viejo queda como está hasta el corte. No se borra ni se modifica `legacy` durante el rework, salvo un arreglo urgente que pida la persona a cargo.
5. **Tests:** cada clase nueva lleva sus tests en `src/test/java/com/tcg/bot/<modulo>/`. Los tests de `legacy/` tienen que seguir pasando.
6. **Todo se divide por módulo, no por endpoint ni por método** (backend, frontend y QA):
   - **Backend:** un `XController` por módulo, con todos sus endpoints (por ejemplo, `ConfiguracionController` tiene GET y los PUT de tienda, sheet, precios y puntos). No se arma un controller por endpoint.
   - **Frontend:** una carpeta por módulo, con su página, sus componentes y **un único archivo `<modulo>Api.ts`** con todas las llamadas del módulo. No se arma un archivo por llamada ni una página por método.
   - **QA:** un `.feature` por módulo (los escenarios de cada HU se suman ahí, con sus tags), un archivo de pasos y un Page Object por módulo, con todas las acciones de esa pantalla.
   - No hay un límite fijo de líneas. Un archivo se parte solo si hay sub-pantallas de verdad o si el líder lo consulta y la persona a cargo lo aprueba; se decide caso por caso.
7. **Nada de archivos ni carpetas "porque sí":** cada archivo nuevo tiene que aportar algo.
   - **Service** solo si hay reglas de negocio o datos que buscar. Si el endpoint solo arma una respuesta, la lógica va en el controller (ejemplo: `EstadoAppController`).
   - **DTO en `dto/`** solo si lo usa más de una clase. Si lo usa un solo endpoint, va como `record` adentro del controller.
   - **Frontend:** nada de archivos que solo envuelven a otro componente. Un hook, una función o un componente va en su propio archivo solo si se reutiliza; si no, queda en el archivo que lo usa.
   - **Carpetas:** se crean cuando entra el primer archivo, nunca vacías "para después".
   - Los tests de una clase van juntos en un solo archivo de test.
8. **Lo común se reutiliza por composición, no por herencia** (salvo las dos bases indicadas):
   - **QA:**
     - **Sí** una `PantallaBase` liviana (`e2e/compartido/`) con lo que comparten todas las pantallas: abrir un módulo, esperar que termine la capa de carga, leer el mensaje de un bloque y el error de un campo.
     - Más un objeto por pieza común que espeja al front (`MarcoApp`, `CapaCarga`). Cada `<Modulo>Pantalla` extiende `PantallaBase`.
   - **Frontend:** nada de clases base. Lo común son componentes y hooks en `compartido/` (por ejemplo, `useGuardarBloque` para "Guardando…", el mensaje y los errores de cualquier bloque, y `clienteApi` para todas las llamadas).
   - **Backend:**
     - nada de `BaseController` ni `BaseService`;
     - lo común va en piezas centrales (`ManejadorErroresApi`, `ErrorDeNegocioException`);
     - la **única** base es la del acceso al Sheet (`compartido/` o `google/`): leer columnas por encabezado, verificar la clave de la fila antes de escribir y usar lock. Todos los `XSheetsRepository` la usan.
9. **Código moderno:**
   - **Java 21:** `record` para datos, inyección por constructor, `switch` con patrones, `Optional` en lugar de devolver `null`, `var` cuando el tipo es obvio y excepciones específicas (nunca `catch (Exception)` genérico).
   - **React + TS:** solo componentes de función con hooks, TypeScript `strict` sin `any`, tipos generados desde OpenAPI y estado local antes que librerías globales.
   - **QA:** Playwright con `getByTestId` y esperas automáticas; nunca `Thread.sleep`.
10. **Nombres que se entiendan:** archivos, clases, componentes y funciones con nombres en español que digan qué son o qué hacen (`BloqueTienda`, `guardarPrecios`, `CapaCarga`). Nada de `Utils`, `Helper`, `Manager`, `data2` ni abreviaturas.
