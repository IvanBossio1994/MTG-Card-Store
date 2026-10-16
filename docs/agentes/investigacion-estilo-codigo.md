# Investigación: estilo de código común (back, front y QA)

> Estado: **investigación terminada** (15/10). No se tocó código. **Próximo paso:** que la persona a cargo responda las preguntas 4 y 5 (y qué hacer con F3). Después, el brief de una épica o HU de **base técnica** con `/agente-lider` → "Historia nueva". **QA queda postergado** hasta que la persona a cargo pase su estilo: no incluirlo por ahora.
> Objetivo de la persona a cargo (QA automation sr): **acortar, modernizar y unificar** el código antes de seguir con otras épicas, y dejar una guía con ejemplos para todos los desarrollos.

## 1. Cómo está hoy (lo que se miró)

| Área | Hoy | Qué está bien | Qué se puede mejorar |
|---|---|---|---|
| **QA E2E** (`src/test/java/com/tcg/bot/e2e/`) | `PantallaBase` (base común), `localizadores/<Modulo>Localizadores` (constantes `String` con el `data-testid`), `paginas/<Modulo>Pantalla` (mapas "etiqueta visible → testid"), `pasos/<Modulo>Pasos` (Cucumber `@Given/@When/@Then`, inyección por constructor) | Ya existe base común y Page Object; los pasos no tienen locators ni lógica | Los locators son `String` y se resuelven en cada método (`porTestId(...)`), no como objetos listos; la base no tiene acciones comunes (`hacerClic`, `escribir`, `seleccionar`, `verTexto`); algunos pasos tienen lógica (conversión de celdas en `ConfiguracionPasos`); imports uno por uno |
| **Backend** (`com.tcg.bot.*`, v2) | Controller → Service → Repository, `ProblemDetail`, records en DTOs, tests MockMvc | Capas claras, en español, contrato antes de programar | Sin formateador ni reglas automáticas (no hay Spotless/Checkstyle/ArchUnit en el `pom.xml`); tests con `mockMvc.perform` clásico. *No se revisó en detalle el código de controller y service: hacerlo al armar la guía.* |
| **Frontend** (`frontend/src`, React 19, Vite 6, TS 5.8, Vitest 3) | `clienteApi.ts`, hook `useGuardarBloque`, `useState`/`useEffect` para cargar datos | Un `Api.ts` por módulo, textos centralizados, tipos desde OpenAPI | Carga de datos a mano con `useEffect` (12 usos solo en `BloqueSheet`); sin ESLint/Prettier configurados en el build; formularios a mano |

### Revisión del código de back y front (15/10, segunda pasada)
Leídos: `ConfiguracionController` (466 líneas), `ConfiguracionService` (453), `BloqueSheet.tsx` (495) y `useGuardarBloque.ts` (115).

**Bien:** inyección por constructor, `ProblemDetail`, javadoc/TSDoc con ejemplo, tipos generados desde OpenAPI, alias `@/`, uniones discriminadas en TS (`EstadoLista`), protección contra doble clic.

| # | Dónde | Hallazgo | Mejora |
|---|---|---|---|
| B1 | Controller | Gran parte de las 466 líneas son documentación OpenAPI (`@ApiResponse`, `@ExampleObject`) y ejemplos JSON armados con `+` | Text blocks (`"""`) para los JSON; mover la documentación a una interfaz `ConfiguracionApi` que el controller implementa (el controller queda con la lógica HTTP sola) |
| B2 | Controller | Fábricas con muchos `null` posicionales (`BloqueSheet.de(id, pestana, gmail, null, false, null)`) | Fábricas con nombre (`BloqueSheet.guardado(...)`, `BloqueSheet.conPestanaCreada(...)`) |
| B3 | Service | 15 mensajes de error como constantes en el service, y además en `docs/v2/textos.md` | Un `enum MensajeConfiguracion` (o un archivo de mensajes) por módulo; un solo lugar por texto |
| B4 | Service | `Optional<SincronizacionCardKingdom>` como parámetro del constructor | Implementación "no hace nada" por defecto o `ObjectProvider`; `Optional` solo como retorno |
| B5 | Service/Repository | Records anidados (`ConfiguracionService.Configuracion`, `ConfiguracionRepository.LogoTienda`) importados de clase en clase | Records del módulo en su propio archivo o en un único `ConfiguracionModelo` |
| F1 | `BloqueSheet.tsx` | 495 líneas, 10 `useState` y contadores en `useRef` para descartar respuestas viejas | TanStack Query (cancela y descarta respuestas viejas solo) y partir en `FilaConectarSheet` + `FilaRenombrarPestana` + hook `usePestanasDelSheet` |
| F2 | `useGuardarBloque.ts` | Reimplementa a mano "guardando", doble clic y estado de error | `useMutation` (`isPending`) y dejar en el hook solo el mapeo de errores a campos |
| F3 | Front vs back | **El patrón del link del Sheet es distinto:** el back acepta `…/spreadsheets/u/0/d/<id>` y el front no (`FORMATO_LINK`) | **Posible defecto:** un link con `/u/0/` (cuentas con varios Gmail) se rechaza en pantalla. Unificar, o validar solo en el back |
| F4 | `BloqueSheet.tsx` | Plural con `replace('1 cartas', '1 carta')` | `Intl.PluralRules` o claves de texto singular/plural en `textos.md` |

### Imports (decidido 15/10)
- **QA:** se define con el ejemplo de la persona a cargo (16/10).
- **Java (back):** `*` solo cuando hace falta: estáticos de aserciones y matchers en tests (`assertThat`, `MockMvcResultMatchers.*`) y 5 clases o más del mismo paquete (umbral de IntelliJ). Lo aplica Spotless.
- **TypeScript:** siempre imports con nombre (sin `* as`), porque así Vite descarta el código que no se usa.

## 2. Propuesta por área (cómo se escribe hoy)

### QA (Playwright Java + Cucumber)
- **"By" no existe en Playwright:** es de Selenium. El equivalente moderno es **`Locator` como campo final**, creado en el constructor (así lo recomiendan las guías actuales: [softwaretestpilot](https://softwaretestpilot.com/blog/automation-testing/page-object-model-locator-strategy), [skillselion](https://skillselion.com/skills/0xbigboss/claude-code/playwright-best-practices)). Se ubica por `getByTestId` (nuestra regla) y, si sirve, `getByRole`/`getByLabel`.
- **`PantallaBase` con acciones comunes** (como la BasePage de la persona a cargo): `hacerClic(Locator)`, `escribir(Locator, String)`, `seleccionar(Locator, String)`, `textoDe(Locator)`, `verQueSeVe(Locator)`. Usan las esperas automáticas de Playwright y `assertThat` web-first; nada de `waitForTimeout`.
- **Pasos finos:** solo llaman a la pantalla; cualquier conversión de datos va a un ayudante (`soporte/` o `DataTable` con `@DataTableType`).
- **Imports con `*`:** decisión de estilo (ver preguntas). Las guías de Google y Spotless por defecto los prohíben; se puede configurar al revés si se prefiere.

Ejemplo propuesto (borrador):
```java
public class ConfiguracionPantalla extends PantallaBase {
    private final Locator campoNombreTienda = porTestId("configuracion-campo-nombre-tienda");
    private final Locator botonGuardarTienda = porTestId("configuracion-boton-guardar-tienda");

    public void guardarTienda(String nombre) {
        escribir(campoNombreTienda, nombre);
        hacerClic(botonGuardarTienda);
    }
}
```
Pendiente decidir: ¿se mantienen las clases `<Modulo>Localizadores` o los locators viven solo en la pantalla?

### Backend (Java 21 + Spring Boot 3.5)
- **Java moderno:** `record` para DTOs y valores, `switch` con pattern matching, `var` en locales obvios, text blocks para JSON en tests, `Optional` solo en retornos.
- **Spring:** inyección por constructor sin `@Autowired`; `@ConfigurationProperties` con records; `RestClient` para Card Kingdom (en lugar de `RestTemplate`); en tests `@MockitoBean` (reemplaza a `@MockBean`, deprecado desde Boot 3.4) y **`MockMvcTester`** (Spring 6.2, aserciones AssertJ, más corto que `mockMvc.perform(...).andExpect(...)`).
- **Reglas automáticas:** **Spotless** (formato único al compilar) y **ArchUnit** (test que verifica las reglas de `estructura-carpetas.md`: v2 no importa `legacy`, un controller por módulo, etc.).

### Frontend (React 19 + TS)
- **Datos del servidor:** **TanStack Query** (`useQuery`/`useMutation`) reemplaza los `useEffect` + `useState` de carga, reintento y "guardando". *Es una librería nueva: hay que aprobarla.*
- **Formularios:** React Hook Form + Zod (validación declarativa y mensajes por campo). *También librerías nuevas.* Alternativa sin librerías: `useActionState` de React 19.
- **Reglas automáticas:** ESLint (reglas de hooks) + Prettier, corridos por Maven en el build.

## 3. Cómo se haría (propuesta de épica)
1. **HU base técnica — Guía de estilo:** `docs/agentes/estilo-codigo.md` con un ejemplo corto por tipo de archivo (pantalla, pasos, controller, service, test, componente, hook). Los agentes la leen en lugar de repetir reglas en su archivo.
2. **HU base técnica — Herramientas:** Spotless, ArchUnit, ESLint + Prettier en el build.
3. **HU por área — Refactor de Configuración** (el único módulo v2 hoy) a la guía: QA, back y front (una HU por área, sin cambiar comportamiento; los tests existentes son la red).
4. Actualizar `backend-java`, `frontend`, `qa-*` y `qa-guia.md` para que apunten a la guía.

Estimación: guía ~3-5 puntos (líder) · herramientas ~10-15 (backend-java) · cada refactor ~10-17 (un agente por área).

## Tercera pasada: tests y repositorios (15/10, hecha)
Leídos: `ConfiguracionControllerTest` (550 líneas), `ConfiguracionServiceTest` (504), `ConfiguracionPagina.test.tsx` (859), `AccesoSheetCuentaServicio` (273) y `ConfiguracionArchivosRepository` (245).

**Bien:** los tests usan dobles reales en memoria (`AccesoSheetEnMemoria`, `RepositorioQuePuedeFallar`) y no Mockito; ayudantes con nombre (`putJson`, `esErrorDeCampo`, `abrirPantalla`); text blocks ya en algunos JSON; nombres de test que se leen como frases. Los repositorios escriben con archivo temporal + movimiento atómico y lock, y la clave se carga recién en el primer uso.

| # | Dónde | Hallazgo | Mejora | Cuánto se acorta |
|---|---|---|---|---|
| T1 | `ConfiguracionControllerTest` | 224 líneas de `perform`/`andExpect`; el GET de la configuración tiene 17 `jsonPath` seguidos | **`MockMvcTester`** + comparar el JSON entero con un text block (`bodyJson().isLenientlyEqualTo("""…""")`). Ojo: el ahorro grande viene de comparar el JSON entero, no del cambio de API | ~30% (550 → ~380) |
| T2 | Tests de back | El armado del Sheet de prueba (5 Sheets, pestañas, filas) está **copiado** en el test del controller y en el del service; `RepositorioQuePuedeFallar` vive dentro de `ConfiguracionServiceTest` y el otro test lo importa de ahí | Una clase de datos de prueba por módulo (`DatosDePruebaConfiguracion`) con los Sheets y los dobles | ~40 líneas por test |
| T3 | `ConfiguracionServiceTest` | Ya es compacto; un `assertThat` por campo | Comparar el record entero (`isEqualTo(new …)` o `usingRecursiveComparison()`) | ~10% |
| T4 | `ConfiguracionPagina.test.tsx` | 81 `fireEvent` y **no** está `user-event`; `fetch` falso hecho a mano con un enrutador propio (`fetchFalso`/`listarFalso`) | **`@testing-library/user-event`** (simula teclear, salir del campo y hacer clic como una persona; es lo que recomienda Testing Library) y **MSW** (respuestas falsas por ruta, reutilizables entre tests) | ~25% (859 → ~650) |
| T5 | Test de front | Un solo archivo de 859 líneas para los 4 bloques | Un test por componente (`BloqueSheet.test.tsx`, …) + uno chico de la página | Mismo total, más fácil de leer |
| R1 | `AccesoSheetCuentaServicio` | Cada escritura termina en `return null;` porque usa el mismo lambda que las lecturas | Una segunda interfaz para acciones sin resultado (`AccionGoogle`) | Menor |
| R2 | `AccesoSheetCuentaServicio` | El id de la pestaña nueva se sortea a mano (`ThreadLocalRandom` + lista de ids usados) | Dejar que Google lo asigne y leerlo de la respuesta de `addSheet` | ~10 líneas |
| R3 | `ConfiguracionArchivosRepository` | 14 constantes de claves en inglés (`store.name`, …) | **Se mantienen:** son las del archivo que comparte con la v1. Solo agruparlas | — |

Si se suma TanStack Query (F1/F2), los tests de front necesitan envolver la pantalla en un `QueryClientProvider`: va en el mismo ayudante `abrirPantalla`.

### F3 confirmado (15/10)
Probado con los dos patrones: `…/spreadsheets/u/0/d/<id>/edit` → front **no**, back **sí**; `…/spreadsheets/d/abc/edit` (ID corto) → front **sí**, back **no**.
**Efecto real (menor que lo anotado):** el patrón del front solo se usa al salir del campo (`alSalirDelLink`). Con un link `/u/0/` **sí se puede guardar**; lo que falla es que la lista **"Traer cartas de" no se carga** para ese Sheet, así que no se pueden traer cartas de otra pestaña. Severidad media. Arreglo: el mismo patrón que el back (o que el front no valide y deje decidir al back). Va como `DEF` o dentro del refactor de front.

## 4. Preguntas para la persona a cargo (antes del brief)
1. *(Postergado, QA)* **Ejemplo propio:** pasar un archivo de pasos, una BasePage y una página de su código de QA para copiar el estilo (es lo más rápido para no adivinar).
2. ~~**Imports con `*`**~~ → decidido (ver "Imports").
3. *(Postergado, QA)* **Locators:** ¿campos `Locator` en la pantalla (propuesta) o clase aparte de locators como hoy?
4. **Librerías nuevas en el front:** ¿TanStack Query y React Hook Form + Zod, o seguir sin librerías? Para los tests: `user-event` y MSW (solo de desarrollo, no llegan a la app).
5. **Orden:** ¿guía + herramientas primero y refactor después, o empezar por QA?
