package com.tcg.bot.configuracion;

import com.tcg.bot.configuracion.ConfiguracionRepository.LogoTienda;
import com.tcg.bot.configuracion.ConfiguracionRepository.PreciosBulk;
import com.tcg.bot.configuracion.ConfiguracionRepository.ReglaDePrecios;
import com.tcg.bot.configuracion.ConfiguracionService.Configuracion;
import com.tcg.bot.configuracion.ConfiguracionService.PestanaDelSheet;
import com.tcg.bot.configuracion.ConfiguracionService.PreciosGuardados;
import com.tcg.bot.configuracion.ConfiguracionService.SheetConectado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * API de Configuración: leer la configuración y guardar cada bloque por separado (tienda, logo, Sheet, precios, puntos).
 * Contrato: docs/v2/api/configuracion.md. Solo HTTP: las reglas están en {@link ConfiguracionService}.
 */
@RestController
@RequestMapping(path = "/api/v1/configuracion", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Configuración", description = "Tienda, Google Sheet, regla de precio y puntos de clientes")
public class ConfiguracionController {

    static final String RUTA_LOGO = "/api/v1/configuracion/tienda/logo";
    private static final String EJEMPLO_ERROR_VALIDACION =
            "{\"type\":\"about:blank\",\"title\":\"Datos inválidos\",\"status\":400,"
                    + "\"detail\":\"Hay datos para corregir.\",\"instance\":\"/api/v1/configuracion/tienda\","
                    + "\"errores\":{\"nombre\":\"El nombre de la tienda es obligatorio.\"}}";
    private static final String EJEMPLO_SESION_VENCIDA =
            "{\"type\":\"about:blank\",\"title\":\"Sesión de Google vencida\","
                    + "\"status\":401,\"detail\":\"La sesión de Google venció.\",\"instance\":\"/api/v1/configuracion\","
                    + "\"accion\":\"volver-a-iniciar-sesion\",\"urlInicioSesion\":\"/configuracion/google/oauth/iniciar\"}";
    private static final String EJEMPLO_ORIGEN_INEXISTENTE =
            "{\"type\":\"about:blank\",\"title\":\"Datos inválidos\",\"status\":400,"
                    + "\"detail\":\"Hay datos para corregir.\",\"instance\":\"/api/v1/configuracion/sheet\","
                    + "\"errores\":{\"traerCartasDe\":\"La pestaña de origen ya no existe en el Sheet. Se actualizó la lista.\"},"
                    + "\"campo\":\"traer-cartas-de\",\"codigo\":\"ORIGEN_INEXISTENTE\"}";
    private static final String EJEMPLO_NOMBRE_REPETIDO =
            "{\"type\":\"about:blank\",\"title\":\"Datos inválidos\",\"status\":400,"
                    + "\"detail\":\"Hay datos para corregir.\",\"instance\":\"/api/v1/configuracion/sheet/pestanas/renombrar\","
                    + "\"errores\":{\"nombreNuevo\":\"Ya existe una pestaña con ese nombre.\"},"
                    + "\"campo\":\"nombre-nuevo\",\"codigo\":\"NOMBRE_REPETIDO\"}";

    private final ConfiguracionService servicio;

    /**
     * Crea el controller con su servicio. Ejemplo: {@code new ConfiguracionController(servicio)}.
     */
    public ConfiguracionController(ConfiguracionService servicio) {
        this.servicio = servicio;
    }

    /**
     * Devuelve los cuatro bloques guardados y el Gmail conectado.
     * Ejemplo: GET /api/v1/configuracion → 200 {@code {"tienda":{"nombre":"Codex Live",...},"sheet":{...},"precios":{...},"puntos":{...}}}.
     */
    @GetMapping
    @Operation(
            summary = "Ver la configuración",
            description = "Los cuatro bloques con sus valores guardados y el Gmail conectado (CFG-F06).")
    @ApiResponse(responseCode = "200", description = "Configuración guardada")
    @ApiResponse(
            responseCode = "401",
            description = "Venció la sesión de Google",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = EJEMPLO_SESION_VENCIDA)))
    public ConfiguracionRespuesta verConfiguracion() {
        Configuracion configuracion = servicio.leerConfiguracion();
        var sheet = configuracion.sheet();
        return new ConfiguracionRespuesta(
                new BloqueTienda(configuracion.nombreTienda(), logoUrl(configuracion.versionLogo())),
                BloqueSheet.de(sheet.sheetId(), sheet.pestanaInventario(), configuracion.gmail(), null, false, null),
                BloquePrecios.de(configuracion.precios(), null),
                new BloquePuntos(configuracion.pesosPorPunto()));
    }

    /**
     * Guarda el nombre de la tienda y, si se pide, quita el logo.
     * Ejemplo: PUT /tienda {@code {"nombre":"Codex Live Store"}} → 200 {@code {"nombre":"Codex Live Store","logoUrl":null}}.
     */
    @PutMapping(path = "/tienda", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Guardar la tienda",
            description = "Nombre de la tienda y, opcionalmente, quitar el logo (CFG-F01).")
    @ApiResponse(responseCode = "200", description = "Tienda guardada")
    @ApiResponse(
            responseCode = "400",
            description = "Nombre vacío",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = EJEMPLO_ERROR_VALIDACION)))
    public BloqueTienda guardarTienda(@Valid @RequestBody TiendaPedido pedido) {
        var tienda = servicio.guardarTienda(pedido.nombre(), Boolean.TRUE.equals(pedido.quitarLogo()));
        return new BloqueTienda(tienda.nombre(), logoUrl(tienda.versionLogo()));
    }

    /**
     * Sube el logo de la tienda (reemplaza al anterior).
     * Ejemplo: POST /tienda/logo con un PNG de 500 KB → 200 {@code {"logoUrl":"/api/v1/configuracion/tienda/logo?version=1760550000000"}}.
     */
    @PostMapping(path = "/tienda/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Subir el logo",
            description = "PNG, JPG o WEBP, hasta 2 MB. Se guarda en el momento (CFG-F01, CFG-A02).")
    @ApiResponse(responseCode = "200", description = "Logo guardado")
    @ApiResponse(
            responseCode = "400",
            description = "Pesa más de 2 MB o no es PNG, JPG ni WEBP",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    public LogoRespuesta subirLogo(@RequestParam("archivo") MultipartFile archivo) {
        try {
            return new LogoRespuesta(logoUrl(servicio.guardarLogo(archivo.getBytes())));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el logo subido.", e);
        }
    }

    /**
     * Devuelve la imagen del logo guardado.
     * Ejemplo: GET /tienda/logo → 200 con {@code Content-Type: image/png}; sin logo → 404.
     */
    @GetMapping(
            path = "/tienda/logo",
            produces = {
                MediaType.IMAGE_PNG_VALUE,
                MediaType.IMAGE_JPEG_VALUE,
                "image/webp",
                MediaType.APPLICATION_PROBLEM_JSON_VALUE
            })
    @Operation(summary = "Ver el logo", description = "La imagen del logo guardado. Es la dirección que trae logoUrl.")
    @ApiResponse(responseCode = "200", description = "La imagen")
    @ApiResponse(
            responseCode = "404",
            description = "La tienda no tiene logo",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<byte[]> verLogo() {
        LogoTienda logo = servicio.leerLogo();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.formato().tipoContenido()))
                .body(logo.contenido());
    }

    /**
     * Conecta el Google Sheet y deja lista la pestaña del inventario: la usa si existe; si no, la crea (con las cartas de
     * {@code traerCartasDe}, si viene) o usa la pestaña vacía de un Sheet nuevo. No renombra pestañas con datos.
     * Ejemplo: PUT /sheet {@code {"sheet":"1vSk…","pestanaInventario":"Stock 2026","traerCartasDe":"stock viejo"}} →
     * 200 con {@code "pestanaCreada":true,"cartasTraidas":{"origen":"stock viejo","cantidad":128,"hayColumnasExtra":true}}.
     */
    @PutMapping(path = "/sheet", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Conectar el Google Sheet",
            description =
                    "Verifica la sesión y el acceso de la cuenta de servicio; si la pestaña del inventario no existe la crea "
                            + "(trayendo las cartas de otra pestaña si se pide, en una sola escritura) o usa la pestaña vacía de un Sheet nuevo; "
                            + "crea Movimientos, Caja, Reservas y Clientes si faltan, y guarda (CFG-F02, CFG-A03, CFG-A04, CFG-A05, CFG-A07).")
    @ApiResponse(
            responseCode = "200",
            description = "Sheet conectado",
            content =
                    @Content(
                            schema = @Schema(implementation = BloqueSheet.class),
                            examples =
                                    @ExampleObject(
                                            value =
                                                    "{\"sheetId\":\"1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ\","
                                                            + "\"link\":\"https://docs.google.com/spreadsheets/d/1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ/edit\","
                                                            + "\"pestanaInventario\":\"Stock 2026\",\"gmail\":\"tienda@gmail.com\",\"aviso\":null,\"pestanaCreada\":true,"
                                                            + "\"cartasTraidas\":{\"origen\":\"stock viejo\",\"cantidad\":128,\"hayColumnasExtra\":true}}")))
    @ApiResponse(
            responseCode = "400",
            description = "Link o pestaña inválidos, o la pestaña de origen ya no existe (ORIGEN_INEXISTENTE)",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = EJEMPLO_ORIGEN_INEXISTENTE)))
    @ApiResponse(
            responseCode = "401",
            description = "Venció la sesión de Google",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = EJEMPLO_SESION_VENCIDA)))
    @ApiResponse(
            responseCode = "403",
            description = "El Sheet no está compartido con la cuenta de servicio",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    public BloqueSheet conectarSheet(@Valid @RequestBody SheetPedido pedido) {
        SheetConectado conectado =
                servicio.conectarSheet(pedido.sheet(), pedido.pestanaInventario(), pedido.traerCartasDe());
        var traidas = conectado.cartasTraidas();
        return BloqueSheet.de(
                conectado.sheetId(),
                conectado.pestanaInventario(),
                conectado.gmail(),
                conectado.aviso(),
                conectado.pestanaCreada(),
                traidas == null
                        ? null
                        : new CartasTraidasRespuesta(traidas.origen(), traidas.cantidad(), traidas.hayColumnasExtra()));
    }

    /**
     * Lista las pestañas del Sheet guardado o, con {@code idOLink}, las de otro Sheet (aunque no esté guardado).
     * Ejemplo: GET /sheet/pestanas → 200 {@code {"sheetGuardado":true,"pestanas":[{"nombre":"Inventario","enUso":true,"sugerida":true,"fija":false}]}}.
     */
    @GetMapping("/sheet/pestanas")
    @Operation(
            summary = "Ver las pestañas del Sheet",
            description =
                    "Para las listas \"Pestaña a renombrar\" y \"Traer cartas de\" (CFG-F02, CFG-F03). Solo lee. Sin Sheet guardado "
                            + "y sin idOLink responde sheetGuardado=false sin llamar a Google.")
    @ApiResponse(
            responseCode = "200",
            description = "Pestañas en el orden del Sheet",
            content =
                    @Content(
                            schema = @Schema(implementation = ListaPestanasRespuesta.class),
                            examples =
                                    @ExampleObject(
                                            value =
                                                    "{\"sheetGuardado\":true,\"pestanas\":["
                                                            + "{\"nombre\":\"Inventario\",\"enUso\":true,\"sugerida\":true,\"fija\":false},"
                                                            + "{\"nombre\":\"stock viejo\",\"enUso\":false,\"sugerida\":true,\"fija\":false},"
                                                            + "{\"nombre\":\"Caja\",\"enUso\":false,\"sugerida\":false,\"fija\":true}]}")))
    @ApiResponse(
            responseCode = "400",
            description = "idOLink no es un link ni un ID de Google Sheets (LINK_INVALIDO)",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Venció la sesión de Google",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = EJEMPLO_SESION_VENCIDA)))
    @ApiResponse(
            responseCode = "403",
            description = "El Sheet no está compartido con la cuenta de servicio",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ListaPestanasRespuesta verPestanas(
            @Parameter(
                            description =
                                    "Link o ID de otro Sheet (opcional). Sin él, se listan las del Sheet guardado.",
                            example =
                                    "https://docs.google.com/spreadsheets/d/1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ/edit")
                    @RequestParam(name = "idOLink", required = false)
                    String idOLink) {
        var lista = servicio.listarPestanas(idOLink);
        return new ListaPestanasRespuesta(lista.sheetGuardado(), pestanasRespuesta(lista.pestanas()));
    }

    /**
     * Renombra una pestaña del Sheet guardado; si es la del inventario, guarda también el nombre nuevo.
     * Ejemplo: PUT /sheet/pestanas/renombrar {@code {"nombreActual":"Hoja 1","nombreNuevo":"Stock 2026"}} →
     * 200 {@code {"nombreAnterior":"Hoja 1","nombreNuevo":"Stock 2026","eraPestanaInventario":false,...}}.
     */
    @PutMapping(path = "/sheet/pestanas/renombrar", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Renombrar una pestaña del Sheet",
            description =
                    "Cambia el nombre en el Sheet en el momento (CFG-F03). Si es la del inventario, guarda el nombre nuevo en "
                            + "la configuración. Movimientos, Caja, Reservas y Clientes no se pueden renombrar.")
    @ApiResponse(
            responseCode = "200",
            description = "Pestaña renombrada, con la lista actualizada",
            content =
                    @Content(
                            schema = @Schema(implementation = PestanaRenombradaRespuesta.class),
                            examples =
                                    @ExampleObject(
                                            value =
                                                    "{\"nombreAnterior\":\"Inventario\",\"nombreNuevo\":\"Stock 2026\","
                                                            + "\"eraPestanaInventario\":true,\"nombrePestanaInventario\":\"Stock 2026\",\"pestanas\":["
                                                            + "{\"nombre\":\"Stock 2026\",\"enUso\":true,\"sugerida\":true,\"fija\":false},"
                                                            + "{\"nombre\":\"Caja\",\"enUso\":false,\"sugerida\":false,\"fija\":true}]}")))
    @ApiResponse(
            responseCode = "400",
            description = "Validación (PESTANA_VACIA, PESTANA_FIJA, NOMBRE_VACIO, NOMBRE_LARGO, "
                    + "NOMBRE_IGUAL, NOMBRE_REPETIDO) o sin Sheet guardado (SIN_SHEET_GUARDADO)",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = EJEMPLO_NOMBRE_REPETIDO)))
    @ApiResponse(
            responseCode = "401",
            description = "Venció la sesión de Google",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = EJEMPLO_SESION_VENCIDA)))
    @ApiResponse(
            responseCode = "403",
            description = "El Sheet no está compartido con la cuenta de servicio",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "La pestaña ya no existe en el Sheet (PESTANA_INEXISTENTE)",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "500",
            description = "Error inesperado (si falló guardar la configuración, se deshace el renombrado)",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    public PestanaRenombradaRespuesta renombrarPestana(@RequestBody RenombrarPestanaPedido pedido) {
        var renombrada = servicio.renombrarPestana(pedido.nombreActual(), pedido.nombreNuevo());
        return new PestanaRenombradaRespuesta(
                renombrada.nombreAnterior(),
                renombrada.nombreNuevo(),
                renombrada.eraPestanaInventario(),
                renombrada.nombrePestanaInventario(),
                pestanasRespuesta(renombrada.pestanas()));
    }

    /**
     * Guarda la regla de precio y trata de sincronizar con Card Kingdom.
     * Ejemplo: PUT /precios {@code {"cotizacion":1350,"redondeo":500,"bulk":{...}}} → 200 con los valores nuevos y {@code "aviso":null}.
     */
    @PutMapping(path = "/precios", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Guardar la regla de precio",
            description = "Cotización, redondeo, precios bulk y carpeta de cache (CFG-F04, CFG-A06, CFG-A08, CFG-A09).")
    @ApiResponse(responseCode = "200", description = "Precios guardados (con aviso si falló Card Kingdom)")
    @ApiResponse(
            responseCode = "400",
            description = "Algún número vacío, menor o igual a 0, con centavos, o carpeta inexistente",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    public BloquePrecios guardarPrecios(@Valid @RequestBody PreciosPedido pedido) {
        var bulk = pedido.bulk();
        var regla = new ReglaDePrecios(
                pedido.cotizacion(),
                pedido.redondeo(),
                new PreciosBulk(bulk.common(), bulk.uncommon(), bulk.rare(), bulk.mythic(), bulk.tierraBasica()),
                pedido.carpetaCache());
        PreciosGuardados guardados = servicio.guardarPrecios(regla);
        return BloquePrecios.de(guardados.precios(), guardados.aviso());
    }

    /**
     * Guarda cuántos pesos vale un punto.
     * Ejemplo: PUT /puntos {@code {"pesosPorPunto":500}} → 200 {@code {"pesosPorPunto":500.0}}.
     */
    @PutMapping(path = "/puntos", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Guardar los puntos",
            description = "Pesos que tiene que gastar un cliente para sumar un punto (CFG-F05, CFG-A06).")
    @ApiResponse(responseCode = "200", description = "Puntos guardados")
    @ApiResponse(
            responseCode = "400",
            description = "Vacío o menor o igual a 0",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    public BloquePuntos guardarPuntos(@Valid @RequestBody PuntosPedido pedido) {
        return new BloquePuntos(servicio.guardarPuntos(pedido.pesosPorPunto()));
    }

    private static String logoUrl(Long version) {
        return version == null ? null : RUTA_LOGO + "?version=" + version;
    }

    private static List<PestanaRespuesta> pestanasRespuesta(List<PestanaDelSheet> pestanas) {
        return pestanas.stream()
                .map(pestana ->
                        new PestanaRespuesta(pestana.nombre(), pestana.enUso(), pestana.sugerida(), pestana.fija()))
                .toList();
    }

    // ---- Pedidos (request) ----

    /** Pedido de PUT /tienda. Ejemplo: {@code {"nombre":"Codex Live Store","quitarLogo":false}}. */
    @Schema(description = "Datos de la tienda")
    public record TiendaPedido(
            @Schema(
                    description = "Nombre de la tienda",
                    example = "Codex Live Store",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El nombre de la tienda es obligatorio.")
            String nombre,

            @Schema(description = "true para quitar el logo actual", example = "false", defaultValue = "false")
            Boolean quitarLogo) {}

    /** Pedido de PUT /sheet. Ejemplo: {@code {"sheet":"https://docs.google.com/spreadsheets/d/1vSk…/edit","pestanaInventario":"Stock 2026","traerCartasDe":"stock viejo"}}. */
    @Schema(description = "Sheet de la tienda y pestaña del inventario")
    public record SheetPedido(
            @Schema(
                    description = "Link completo del Google Sheet o solo su ID",
                    example =
                            "https://docs.google.com/spreadsheets/d/1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ/edit",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El ID o la URL del Sheet es obligatorio.")
            String sheet,

            @Schema(
                    description = "Nombre de la pestaña del inventario",
                    example = "Stock 2026",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El nombre de la pestaña es obligatorio.")
            String pestanaInventario,

            @Schema(
                    description =
                            "Pestaña de la que traer las cartas si la del inventario no existe. Null o vacío = no traer cartas",
                    example = "stock viejo",
                    nullable = true)
            String traerCartasDe) {}

    /** Pedido de PUT /sheet/pestanas/renombrar. Ejemplo: {@code {"nombreActual":"Inventario","nombreNuevo":"Stock 2026"}}. */
    @Schema(description = "Pestaña a renombrar y su nombre nuevo (lo valida el servicio, con campo y código)")
    public record RenombrarPestanaPedido(
            @Schema(
                    description = "Nombre actual de la pestaña",
                    example = "Inventario",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String nombreActual,

            @Schema(
                    description = "Nombre nuevo, hasta 100 caracteres",
                    example = "Stock 2026",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String nombreNuevo) {}

    /** Pedido de PUT /precios. Ejemplo: {@code {"cotizacion":1350,"redondeo":500,"bulk":{...},"carpetaCache":null}}. */
    @Schema(description = "Regla de precio")
    public record PreciosPedido(
            @Schema(
                    description = "ARS por USD de Card Kingdom",
                    example = "1350",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "La cotización del dólar es obligatoria.")
            @Positive(message = "La cotización debe ser mayor a 0.")
            Double cotizacion,

            @Schema(
                    description = "Múltiplo al que se redondea el precio",
                    example = "500",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "El múltiplo para redondear es obligatorio.")
            @Positive(message = "El múltiplo debe ser mayor a 0.")
            Integer redondeo,

            @Schema(description = "Precio bulk por rareza", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "El precio es obligatorio.")
            @Valid
            BulkPedido bulk,

            @Schema(
                    description = "Carpeta de la cache de Card Kingdom. Vacío o null = la de siempre",
                    example = "D:/TCG/cache",
                    nullable = true)
            String carpetaCache) {}

    /** Precios bulk del pedido. Ejemplo: {@code {"common":50,"uncommon":100,"rare":150,"mythic":150,"tierraBasica":200}}. */
    @Schema(description = "Precio bulk por rareza, en pesos y sin centavos")
    public record BulkPedido(
            @Schema(example = "50", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "El precio es obligatorio.")
            @Positive(message = "Debe ser mayor a 0.")
            @Digits(integer = 12, fraction = 0, message = "El precio debe ser un número entero, sin centavos.")
            Double common,

            @Schema(example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "El precio es obligatorio.")
            @Positive(message = "Debe ser mayor a 0.")
            @Digits(integer = 12, fraction = 0, message = "El precio debe ser un número entero, sin centavos.")
            Double uncommon,

            @Schema(example = "150", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "El precio es obligatorio.")
            @Positive(message = "Debe ser mayor a 0.")
            @Digits(integer = 12, fraction = 0, message = "El precio debe ser un número entero, sin centavos.")
            Double rare,

            @Schema(example = "150", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "El precio es obligatorio.")
            @Positive(message = "Debe ser mayor a 0.")
            @Digits(integer = 12, fraction = 0, message = "El precio debe ser un número entero, sin centavos.")
            Double mythic,

            @Schema(example = "200", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "El precio es obligatorio.")
            @Positive(message = "Debe ser mayor a 0.")
            @Digits(integer = 12, fraction = 0, message = "El precio debe ser un número entero, sin centavos.")
            Double tierraBasica) {}

    /** Pedido de PUT /puntos. Ejemplo: {@code {"pesosPorPunto":500}}. */
    @Schema(description = "Puntos de clientes")
    public record PuntosPedido(
            @Schema(
                    description = "Pesos que tiene que gastar un cliente para sumar un punto",
                    example = "500",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "Los pesos por punto son obligatorios.")
            @Positive(message = "Debe ser mayor a 0.")
            Double pesosPorPunto) {}

    // ---- Respuestas ----

    /** Respuesta de GET /api/v1/configuracion. Ejemplo: {@code {"tienda":{...},"sheet":{...},"precios":{...},"puntos":{...}}}. */
    @Schema(description = "Configuración completa, por bloques")
    public record ConfiguracionRespuesta(
            BloqueTienda tienda, BloqueSheet sheet, BloquePrecios precios, BloquePuntos puntos) {}

    /** Bloque tienda. Ejemplo: {@code {"nombre":"Codex Live","logoUrl":null}}. */
    @Schema(description = "Tienda")
    public record BloqueTienda(
            @Schema(example = "Codex Live") String nombre,

            @Schema(
                    description = "Dirección del logo, o null si no hay",
                    example = "/api/v1/configuracion/tienda/logo?version=1760550000000",
                    nullable = true)
            String logoUrl) {}

    /** Respuesta de POST /tienda/logo. Ejemplo: {@code {"logoUrl":"/api/v1/configuracion/tienda/logo?version=1760550000000"}}. */
    @Schema(description = "Logo guardado")
    public record LogoRespuesta(
            @Schema(example = "/api/v1/configuracion/tienda/logo?version=1760550000000")
            String logoUrl) {}

    /** Bloque sheet. Ejemplo: {@code {"sheetId":"1vSk…","link":"https://…","pestanaInventario":"Inventario","gmail":"tienda@gmail.com","aviso":null}}. */
    @Schema(description = "Google Sheet de la tienda")
    public record BloqueSheet(
            @Schema(example = "1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ", nullable = true)
            String sheetId,

            @Schema(
                    example =
                            "https://docs.google.com/spreadsheets/d/1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ/edit",
                    nullable = true)
            String link,

            @Schema(example = "Inventario") String pestanaInventario,

            @Schema(
                    description = "Gmail con el que se inició sesión, o null si Google no lo informa",
                    example = "tienda@gmail.com",
                    nullable = true)
            String gmail,

            @Schema(
                    description = "Solo en PUT /sheet: aviso si se creó la pestaña del inventario sin traer cartas",
                    example = "Se creó la pestaña «Inventario».",
                    nullable = true)
            String aviso,

            @Schema(
                    description =
                            "Solo en PUT /sheet: true si la pestaña del inventario no existía y se creó. En el GET, false",
                    example = "true")
            boolean pestanaCreada,

            @Schema(
                    description = "Solo en PUT /sheet, si se creó la pestaña trayendo cartas; si no, null",
                    nullable = true)
            CartasTraidasRespuesta cartasTraidas) {
        /** Arma el bloque con el link del Sheet. Ejemplo: {@code de("1Abc…", "Inventario", null, null, false, null)}. */
        static BloqueSheet de(
                String sheetId,
                String pestana,
                String gmail,
                String aviso,
                boolean pestanaCreada,
                CartasTraidasRespuesta cartasTraidas) {
            String link = sheetId == null ? null : "https://docs.google.com/spreadsheets/d/" + sheetId + "/edit";
            return new BloqueSheet(sheetId, link, pestana, gmail, aviso, pestanaCreada, cartasTraidas);
        }
    }

    /** Cartas traídas al crear la pestaña del inventario. Ejemplo: {@code {"origen":"stock viejo","cantidad":128,"hayColumnasExtra":true}}. */
    @Schema(description = "Cartas copiadas de otra pestaña")
    public record CartasTraidasRespuesta(
            @Schema(description = "Pestaña de origen", example = "stock viejo")
            String origen,

            @Schema(description = "Cartas (filas) copiadas", example = "128")
            int cantidad,

            @Schema(description = "true si quedaron al final columnas que la app no usa", example = "true")
            boolean hayColumnasExtra) {}

    /** Respuesta de GET /sheet/pestanas. Ejemplo: {@code {"sheetGuardado":false,"pestanas":[]}}. */
    @Schema(description = "Pestañas de un Sheet")
    public record ListaPestanasRespuesta(
            @Schema(description = "false solo si no vino idOLink y no hay Sheet guardado", example = "true")
            boolean sheetGuardado,

            @Schema(description = "En el orden del Sheet; vacía si sheetGuardado es false")
            List<PestanaRespuesta> pestanas) {}

    /** Una pestaña. Ejemplo: {@code {"nombre":"Caja","enUso":false,"sugerida":false,"fija":true}}. */
    @Schema(description = "Pestaña del Sheet")
    public record PestanaRespuesta(
            @Schema(example = "Inventario") String nombre,

            @Schema(description = "true si es la pestaña del inventario del Sheet guardado", example = "true")
            boolean enUso,

            @Schema(description = "true si su fila 1 tiene encabezados de inventario", example = "true")
            boolean sugerida,

            @Schema(
                    description = "true si es Movimientos, Caja, Reservas o Clientes (no se puede renombrar)",
                    example = "false")
            boolean fija) {}

    /** Respuesta de PUT /sheet/pestanas/renombrar. Ejemplo: {@code {"nombreAnterior":"Hoja 1","nombreNuevo":"Stock 2026",...}}. */
    @Schema(description = "Pestaña renombrada")
    public record PestanaRenombradaRespuesta(
            @Schema(example = "Inventario") String nombreAnterior,
            @Schema(example = "Stock 2026") String nombreNuevo,

            @Schema(
                    description =
                            "true si era la del inventario: la configuración ya quedó guardada con el nombre nuevo",
                    example = "true")
            boolean eraPestanaInventario,

            @Schema(description = "Pestaña del inventario después del cambio", example = "Stock 2026")
            String nombrePestanaInventario,

            @Schema(description = "Lista actualizada") List<PestanaRespuesta> pestanas) {}

    /** Bloque precios. Ejemplo: {@code {"cotizacion":1350.0,"redondeo":500,"bulk":{...},"carpetaCache":null,"aviso":null}}. */
    @Schema(description = "Regla de precio")
    public record BloquePrecios(
            @Schema(example = "1350") double cotizacion,
            @Schema(example = "500") int redondeo,
            BloqueBulk bulk,

            @Schema(description = "null si se usa la carpeta de siempre", example = "D:/TCG/cache", nullable = true)
            String carpetaCache,

            @Schema(
                    description = "Solo en PUT /precios: aviso si falló la sincronización con Card Kingdom",
                    example = "Guardado, pero no se pudo sincronizar con Card Kingdom. Se puede reintentar más tarde.",
                    nullable = true)
            String aviso) {
        /** Arma el bloque desde la regla guardada. Ejemplo: {@code de(regla, null)}. */
        static BloquePrecios de(ReglaDePrecios regla, String aviso) {
            var bulk = regla.bulk();
            return new BloquePrecios(
                    regla.cotizacion(),
                    regla.redondeo(),
                    new BloqueBulk(
                            Math.round(bulk.common()),
                            Math.round(bulk.uncommon()),
                            Math.round(bulk.rare()),
                            Math.round(bulk.mythic()),
                            Math.round(bulk.tierraBasica())),
                    regla.carpetaCache(),
                    aviso);
        }
    }

    /** Precios bulk en pesos, sin centavos. Ejemplo: {@code {"common":50,"uncommon":100,"rare":150,"mythic":150,"tierraBasica":200}}. */
    @Schema(description = "Precio bulk por rareza")
    public record BloqueBulk(
            @Schema(example = "50") long common,
            @Schema(example = "100") long uncommon,
            @Schema(example = "150") long rare,
            @Schema(example = "150") long mythic,
            @Schema(example = "200") long tierraBasica) {}

    /** Bloque puntos. Ejemplo: {@code {"pesosPorPunto":1000.0}}. */
    @Schema(description = "Puntos de clientes")
    public record BloquePuntos(@Schema(example = "1000") double pesosPorPunto) {}
}
