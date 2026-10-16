# Guía de estilo de código (v2)

> **Regla para `backend-java` y `frontend`: leer esta guía antes de programar y copiar el ejemplo del tipo de archivo que toca.** Describe la forma a la que se quiere llegar (EP-002). Configuración se adapta en HU-012 (back) y HU-013 (front); mientras tanto, si el código existente no coincide, **manda esta guía** para lo nuevo y lo que se toque.
> Dónde va cada archivo: `estructura-carpetas.md`. Qué hacen las herramientas que la hacen cumplir: sección 4 (llegan en HU-011).
> Origen de cada regla: `investigacion-estilo-codigo.md` (códigos B, F, T y R).

## 1. Común (back y front)

- **Nombres en español y descriptivos** (`guardarTienda`, `BloqueSheet`, `pestanasDelSheet`); sufijos de Spring en inglés. Nada de `Utils`, `Helper`, `Manager`, `data2` ni abreviaturas.
- **Un comentario corto por cosa pública** (javadoc o TSDoc): qué hace, en una o dos líneas, y un ejemplo de entrada y salida. No se comenta lo obvio dentro de los métodos; sí el *por qué* de algo raro.
- **Más corto no es más críptico:** se acorta sacando repetición (ayudantes, datos de prueba compartidos, comparar objetos enteros), no apretando código.
- **Una sola fuente por regla o texto:** un mensaje vive en un solo lugar del código; una validación de formato vive en el back (el front solo revisa campos vacíos, ver 3.4).
- **Imports:**
  - **Java:** uno por línea, salvo dos casos con `*`: los estáticos de aserciones y matchers en tests (`Assertions.*`, `MockMvcResultMatchers.*`) y 5 clases o más del mismo paquete. Lo arma IntelliJ con `.editorconfig`; Spotless solo los ordena y saca los que no se usan.
  - **TypeScript:** siempre con nombre (`import { useQuery } from '@tanstack/react-query'`), nunca `import * as`; los tipos con `import type`. Rutas con el alias `@/`.

## 2. Backend (Java 21 + Spring Boot 3.5)

### 2.1 Controller: solo HTTP; la documentación, en la interfaz `<Modulo>Api`
La documentación OpenAPI (`@Operation`, `@ApiResponse`, ejemplos) va en una interfaz del módulo que el controller implementa. springdoc la lee de la interfaz. El controller queda con el mapeo HTTP y la conversión pedido → servicio → respuesta (B1). Los ejemplos JSON largos van en text blocks.

```java
/** Documentación OpenAPI de Configuración (contrato: docs/v2/api/configuracion.md). */
@Tag(name = "Configuración")
interface ConfiguracionApi {

    String EJEMPLO_SESION_VENCIDA = """
            {"type":"about:blank","title":"Sesión vencida","status":401,
             "detail":"La sesión de Google venció.","accion":"volver-a-iniciar-sesion"}""";

    @Operation(summary = "Guardar la tienda", description = "Nombre y, si se pide, quitar el logo (CFG-F01).")
    @ApiResponse(responseCode = "200", description = "Tienda guardada")
    @ApiResponse(responseCode = "400", description = "Nombre vacío", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    BloqueTienda guardarTienda(TiendaPedido pedido);
}
```
```java
@RestController
@RequestMapping("/api/v1/configuracion")
class ConfiguracionController implements ConfiguracionApi {

    private final ConfiguracionService servicio;

    ConfiguracionController(ConfiguracionService servicio) {
        this.servicio = servicio;
    }

    /** Guarda el nombre de la tienda. Ejemplo: PUT /tienda {"nombre":"Codex Live"} → 200 {"nombre":"Codex Live","logoUrl":null}. */
    @Override
    @PutMapping(path = "/tienda", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BloqueTienda guardarTienda(@Valid @RequestBody TiendaPedido pedido) {
        var tienda = servicio.guardarTienda(pedido.nombre(), Boolean.TRUE.equals(pedido.quitarLogo()));
        return BloqueTienda.de(tienda);
    }
}
```
- Un controller por módulo, con todos sus endpoints. Sin `@Autowired`: constructor único.
- La interfaz tiene solo documentación; el mapeo (`@PutMapping`, `@Valid`, `@RequestBody`) queda en el controller, donde se lee.

### 2.2 Records: datos del negocio y respuestas
- **Record de un solo uso** (pedido o respuesta de un endpoint): adentro del controller.
- **Record que usan varias clases** (`SheetGuardado`, `ReglaDePrecios`): en **su propio archivo** dentro del paquete del módulo, no anidado en el service o el repositorio para importarlo de clase en clase (B5).
- **Fábricas con nombre** en lugar de constructores con varios `null` o `false` posicionales (B2):

```java
record BloqueSheet(String sheetId, String link, String pestanaInventario, String gmail,
                   String aviso, boolean pestanaCreada, CartasTraidas cartasTraidas) {

    /** Sheet guardado, sin novedades. Ejemplo: BloqueSheet.guardado(sheet, "tienda@gmail.com"). */
    static BloqueSheet guardado(SheetGuardado sheet, String gmail) {
        return new BloqueSheet(sheet.sheetId(), linkDe(sheet.sheetId()), sheet.pestanaInventario(), gmail, null, false, null);
    }

    /** Recién conectado y con la pestaña creada. */
    static BloqueSheet conPestanaCreada(SheetGuardado sheet, String gmail) { … }
}
```

### 2.3 Service: reglas de negocio, mensajes en un `enum` del módulo
- Inyección por constructor. **`Optional` solo como retorno**, nunca como parámetro ni como campo (B4). Si una dependencia puede no estar (por ejemplo, la sincronización con Card Kingdom en el modo prueba), se registra una implementación que no hace nada, y el service la recibe siempre.
- **Los mensajes de error del módulo, en un `enum`** (B3). El texto tiene que ser idéntico al de `docs/v2/textos.md`, que es lo que valida QA:

```java
/** Mensajes de error de Configuración (los mismos de docs/v2/textos.md). */
enum MensajeConfiguracion {
    NOMBRE_VACIO("El nombre de la tienda es obligatorio."),
    LOGO_PESADO("El logo no debe superar los 2 MB.");

    private final String texto;

    MensajeConfiguracion(String texto) { this.texto = texto; }

    String texto() { return texto; }
}
```
```java
/** Guarda el nombre sin espacios en los bordes. Ejemplo: "  Codex Live " → "Codex Live"; "  " → 400. */
public Tienda guardarTienda(String nombre, boolean quitarLogo) {
    var limpio = nombre == null ? "" : nombre.strip();
    if (limpio.isEmpty()) {
        throw ErrorDeNegocioException.campoInvalido("nombre", NOMBRE_VACIO.texto());
    }
    repositorio.guardarNombreTienda(limpio);
    …
}
```
- Java moderno: `record`, `switch` con patrones, `var` cuando el tipo se ve a la derecha, `String.strip()`, `List.of`/`toList()`, text blocks. Excepciones específicas; nunca `catch (Exception)`.

### 2.4 Repositorio y acceso al Sheet
- `XRepository` (interfaz) + `XSheetsRepository` (real, con la cuenta de servicio) + `XEnMemoriaRepository` (tests y perfil `e2e`).
- Escrituras: con lock y, en archivos, escribiendo en un temporal y moviéndolo de una vez (como `ConfiguracionArchivosRepository`). En el Sheet, verificar la clave de la fila antes de escribir.
- En `AccesoSheetCuentaServicio`, las acciones sin resultado usan su propia interfaz funcional, sin `return null;` (R1); los ids nuevos los asigna Google (R2).

### 2.5 Errores
Todo error que ve la persona sale como `ProblemDetail` desde `compartido/ManejadorErroresApi`. Para los de negocio se usan las fábricas de `ErrorDeNegocioException`: `campoInvalido(campo, mensaje)`, `datosInvalidos(errores, campoPantalla, codigo)` y `deBloque(estado, titulo, detalle, codigo)`.

### 2.6 Test unitario del service
Con dobles reales en memoria (no Mockito para lo propio). Se compara el **resultado entero** cuando es un record (T3):

```java
@Test
void guardaElNombreDeLaTiendaSinEspaciosEnLosBordes() {
    var tienda = servicio.guardarTienda("  Codex Live Store ", false);

    assertThat(tienda).isEqualTo(new Tienda("Codex Live Store", null));
}

@Test
void leerConLaSesionVencidaDa401() {
    datos.vencerSesion();

    assertThatThrownBy(servicio::leerConfiguracion)
            .isInstanceOfSatisfying(ErrorDeNegocioException.class, error -> {
                assertThat(error.getEstado()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(error.getMessage()).isEqualTo("La sesión de Google venció.");
            });
}
```
- Nombre del test = frase que dice el resultado (`putTiendaConNombreVacioDa400YNoGuarda`).
- Estructura en tres bloques separados por una línea en blanco: preparar, hacer, verificar.

### 2.7 Test de API (MockMvc): `MockMvcTester` y JSON entero
`@WebMvcTest` + **`MockMvcTester`** (Spring 6.2, aserciones AssertJ). Se compara **el JSON entero** con un text block en lugar de un `jsonPath` por campo (T1). `isLenientlyEqualTo` ignora el orden y los campos que no se nombran.

```java
@WebMvcTest(ConfiguracionController.class)
@Import({ConfiguracionService.class, DatosDePruebaConfiguracion.Dobles.class})
class ConfiguracionControllerTest {

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private DatosDePruebaConfiguracion datos;

    @Test
    void getDevuelveLosCuatroBloquesConElGmail() {
        assertThat(mvc.get().uri("/api/v1/configuracion"))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"tienda":{"nombre":"Codex Live","logoUrl":null},
                         "sheet":{"sheetId":"%s","pestanaInventario":"Inventario","gmail":"tienda@gmail.com"},
                         "precios":{"cotizacion":1200,"redondeo":500},
                         "puntos":{"pesosPorPunto":1000}}""".formatted(SHEET_INVENTARIO));
    }

    @Test
    void putTiendaConNombreVacioDa400YNoGuarda() {
        assertThat(mvc.put().uri("/api/v1/configuracion/tienda")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"nombre":"  "}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errores.nombre").isEqualTo("El nombre de la tienda es obligatorio.");
        assertThat(datos.repositorio().leerNombreTienda()).isEqualTo("Codex Live");
    }
}
```
- **Datos de prueba compartidos por módulo** (T2): una clase `DatosDePrueba<Modulo>` en el paquete de test del módulo, con los Sheets de ejemplo, los dobles (`RepositorioQuePuedeFallar`, sesión de Google, Card Kingdom) y métodos con nombre (`vencerSesion()`, `fallarAlGuardarSheet()`). La usan el test del service y el del controller; no se copia el armado.
- Si hace falta reemplazar un bean por un mock: `@MockitoBean` (`@MockBean` está deprecado desde Boot 3.4). Se prefieren los dobles en memoria.
- Los métodos de test no declaran `throws Exception` (con `MockMvcTester` no hace falta).

## 3. Frontend (React 19 + TypeScript 5)

### 3.1 `<modulo>Api.ts`: todas las llamadas del módulo
Funciones con nombre que llaman a `pedirApi`/`enviarJson` de `compartido/api/clienteApi`, con los tipos generados desde OpenAPI (`tiposApi.ts`, nunca a mano). Reciben `signal` cuando son lecturas, para que TanStack Query pueda cancelarlas.

```ts
type Esquemas = components['schemas'];
export type Configuracion = Esquemas['ConfiguracionRespuesta'];

const BASE = '/api/v1/configuracion';

/**
 * Lee los cuatro bloques guardados y el Gmail conectado.
 * Ejemplo: `const configuracion = await leerConfiguracion()`
 */
export function leerConfiguracion(signal?: AbortSignal): Promise<Configuracion> {
  return pedirApi<Configuracion>(BASE, { signal });
}
```

### 3.2 Datos del servidor: TanStack Query
Toda lectura del servidor usa `useQuery` y todo guardado usa `useMutation` (F1, F2). Así se dejan de escribir a mano el "cargando", el reintento, el "Guardando…", la protección contra el doble clic y el descarte de respuestas viejas (`useRef` con contadores). Las claves de consulta de cada módulo se definen una vez, en su `<modulo>Api.ts`.

```ts
/** Claves de consulta de Configuración. */
export const clavesConfiguracion = {
  todo: ['configuracion'] as const,
  pestanas: (idOLink?: string) => ['configuracion', 'pestanas', idOLink ?? 'guardado'] as const,
};
```
```tsx
/** Lista las pestañas del Sheet pedido (o del guardado). Ejemplo: `const { data, isPending } = usePestanasDelSheet(link)` */
function usePestanasDelSheet(idOLink?: string) {
  return useQuery({
    queryKey: clavesConfiguracion.pestanas(idOLink),
    queryFn: ({ signal }) => listarPestanas(idOLink, signal),
  });
}
```
```tsx
const guardado = useMutation({
  mutationFn: () => conectarSheet(link, pestana, traerDe),
  onSuccess: () => consultas.invalidateQueries({ queryKey: clavesConfiguracion.todo }),
});
// <Boton guardando={guardado.isPending} onClick={() => guardado.mutate()}>
```
- El `useGuardarBloque` común queda solo para lo que es del sistema de diseño: el mensaje del bloque y pasar los errores del `ProblemDetail` a cada campo.
- Estado local (`useState`) solo para lo que es de la pantalla: lo que se escribe en un campo, lo que está abierto o cerrado. Lo que viene del servidor no se copia a `useState`.

### 3.3 Componentes chicos
- Un componente por bloque o fila con estado propio (`FilaConectarSheet`, `FilaRenombrarPestana`), no un archivo con diez `useState`. Partir un archivo se consulta con el líder (regla 6 de `estructura-carpetas.md`).
- Props con `interface <Componente>Props`; desestructuradas en la firma. Sin `any`, sin clases.
- Textos solo desde `textos.ts` (`textos[clave]` o `texto(clave, datos)`), nunca sueltos.
- **Plurales** con dos claves en `textos.md`, nunca con `replace` (F4):

```ts
const mensaje = texto(cantidad === 1 ? 'configuracion.sheet.exito.traida' : 'configuracion.sheet.exito.traidas', { cantidad, origen });
```

### 3.4 Formularios
- Inputs controlados. El front revisa **solo que no esté vacío**; el formato y las reglas los valida el back, que devuelve el error de cada campo en el `ProblemDetail` y se muestra con `ErrorCampo`. Así hay un solo patrón por regla (DEF-001 pasó por tener dos).
- Sin React Hook Form ni Zod (decisión de EP-002; se reevalúa si un módulo tiene formularios grandes).

### 3.5 Test de componente: `user-event` + MSW
- Un archivo de test por componente o bloque (`BloqueSheet.test.tsx`), más uno chico de la página (T5).
- Las interacciones con **`@testing-library/user-event`** (teclea, sale del campo y hace clic como una persona), no con `fireEvent` (T4).
- Las respuestas del servidor con **MSW**: un archivo de respuestas por módulo y `server.use(...)` en el test que necesita otra.
- Se busca por `data-testid` (es la regla del proyecto y lo que usa QA) y por rol para los títulos y estados (`getByRole('status')`).
- La pantalla se monta con un ayudante que la envuelve en un `QueryClientProvider` nuevo por test, sin reintentos.

```tsx
it('con un link /u/0/ carga la lista "Traer cartas de"', async () => {
  const usuario = userEvent.setup();
  await abrirPantalla();

  await usuario.clear(screen.getByTestId('configuracion-campo-sheet'));
  await usuario.type(screen.getByTestId('configuracion-campo-sheet'), LINK_CON_U0);
  await usuario.tab();

  expect(await screen.findByRole('option', { name: 'stock viejo' })).toBeInTheDocument();
});
```
```ts
// configuracionRespuestas.ts (respuestas falsas del módulo, solo para tests)
export const respuestasConfiguracion = [
  http.get('/api/v1/configuracion', () => HttpResponse.json(CONFIGURACION)),
  http.get('/api/v1/configuracion/sheet/pestanas', () => HttpResponse.json(PESTANAS)),
];
```
- **A revisar en HU-013:** en Node, `fetch` con una ruta relativa (`/api/...`) puede fallar antes de llegar a MSW. Si pasa, `clienteApi` arma la URL completa con `location.origin`. No se agregan arreglos sin probar.

## 4. Herramientas (HU-011)
| Herramienta | Qué hace | Cuándo corre |
|---|---|---|
| Spotless + palantir-java-format | Formato único del Java de la v2 (4 espacios, 120 columnas), ordena imports y saca los que no se usan | `mvn package` (aplica) y `mvn verify` (revisa) |
| ArchUnit | Test que hace cumplir `estructura-carpetas.md`: la v2 no usa `legacy`, un controller por módulo, sin inyección por campo, sin nombres `Utils`/`Helper`/`Manager` | Con los tests |
| ESLint 10 (typescript-eslint `recommendedTypeChecked`, react-hooks, eslint-comments) | Errores de React y TS que el compilador no ve; excepciones solo por línea y con motivo | `npm run lint`, desde Maven antes de `vite build` |
| Prettier 3 | Formato único del front (2 espacios, 100 columnas; `tiposApi.ts` queda afuera porque se genera) | `npm run format`, desde Maven antes de `lint` |
| `.editorconfig` (raíz) | Sangría, fin de línea e imports `*` (5 clases) en el IDE; coincide con Spotless y Prettier | Al escribir |

**Node:** el front usa Node 22.20 (lo pide ESLint 10). En una PC que ya tenía el Node anterior, borrar `frontend/node` antes de compilar; Maven lo baja de nuevo.

Solo la v2: `legacy` queda afuera de Spotless y de las reglas.

**Formato:** `mvn package`, `test` y `test-compile` lo corrigen solos (Spotless se aplica en `process-sources`). Por eso `mvn verify` a secas no falla por formato: para solo revisar, `mvn spotless:check` o `mvn verify "-Dspotless.apply.skip=true"`.

### Excepciones a una regla
Una regla se saltea solo si en ese caso no aplica, y nunca a escondidas:
- **Formato (Spotless, Prettier):** nunca. Lo corrige la herramienta.
- **ESLint:** solo en esa línea y con el motivo: `// eslint-disable-next-line <regla> -- <motivo>`. Nunca por archivo ni global. ESLint falla si falta el motivo o si la excepción ya no hace falta.
- **ArchUnit:** la excepción va en el test (`ArquitecturaV2Test`), nombrando la clase y el motivo.
- **Deuda conocida:** si arreglarlo requiere un refactor, el motivo empieza con la HU que lo arregla (`HU-013: …`).
- **Entrega:** el agente lista cada excepción nueva en su resumen; el líder las revisa y se muestran en el cierre. Si la misma excepción se repite, se propone cambiar la regla (en esta guía y en la herramienta), no sumar más excepciones.

## 5. QA (pendiente)
Se completa en **HU-014** (EP-002) con el ejemplo de la persona a cargo (Page Object, BasePage y pasos). Hasta entonces manda `qa-guia.md`.
