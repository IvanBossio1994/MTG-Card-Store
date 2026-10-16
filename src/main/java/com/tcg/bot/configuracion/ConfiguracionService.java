package com.tcg.bot.configuracion;

import com.tcg.bot.compartido.AccesoSheet;
import com.tcg.bot.compartido.ErrorDeNegocioException;
import com.tcg.bot.compartido.SesionGoogle;
import com.tcg.bot.configuracion.ConfiguracionRepository.FormatoLogo;
import com.tcg.bot.configuracion.ConfiguracionRepository.LogoTienda;
import com.tcg.bot.configuracion.ConfiguracionRepository.ReglaDePrecios;
import com.tcg.bot.configuracion.ConfiguracionRepository.SheetGuardado;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Reglas de negocio de Configuración (CFG-F01 a CFG-F06): validar cada bloque, conectar el Sheet con la cuenta de
 * servicio (crear la pestaña del inventario, con o sin cartas de otra, y las pestañas que falten), listar y renombrar
 * pestañas, y guardar. Contrato: docs/v2/api/configuracion.md. Textos: docs/v2/textos.md.
 */
@Service
public class ConfiguracionService {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionService.class);

    static final long PESO_MAXIMO_LOGO = 2L * 1024L * 1024L;
    static final int LARGO_MAXIMO_PESTANA = 100;
    static final String MENSAJE_LOGO_PESADO = "El logo no debe superar los 2 MB.";
    static final String MENSAJE_LOGO_FORMATO = "El logo debe ser PNG, JPG o WEBP.";
    static final String MENSAJE_SHEET_INVALIDO = "Debe ser una URL de Google Sheets o solo el ID.";
    static final String MENSAJE_CARPETA = "La carpeta no existe. Se puede indicar otra o dejar el campo vacío.";
    static final String AVISO_FALLA_CARD_KINGDOM =
            "Guardado, pero no se pudo sincronizar con Card Kingdom. Se puede reintentar más tarde.";
    static final String MENSAJE_ORIGEN_INEXISTENTE =
            "La pestaña de origen ya no existe en el Sheet. Se actualizó la lista.";
    static final String MENSAJE_SIN_PESTANA = "La pestaña a renombrar es obligatoria.";
    static final String MENSAJE_PESTANA_FIJA = "Esa pestaña la usa la app y no se puede renombrar.";
    static final String MENSAJE_NOMBRE_VACIO = "El nombre nuevo es obligatorio.";
    static final String MENSAJE_NOMBRE_LARGO = "El nombre no debe superar los 100 caracteres.";
    static final String MENSAJE_NOMBRE_IGUAL = "El nombre nuevo es igual al actual.";
    static final String MENSAJE_NOMBRE_REPETIDO = "Ya existe una pestaña con ese nombre.";
    static final String MENSAJE_SIN_SHEET_GUARDADO = "Disponible cuando haya un Sheet guardado.";

    private static final Pattern LINK_DE_SHEET = Pattern.compile(
            "^https?://docs\\.google\\.com/spreadsheets/(?:u/\\d+/)?d/([A-Za-z0-9_-]{20,})(?:[/?#].*)?$");
    /** ID solo, o el ID con el final de la URL pegado (ej.: {@code 1vSk7…/edit?gid=0#gid=0}). */
    private static final Pattern ID_DE_SHEET = Pattern.compile("^([A-Za-z0-9_-]{20,})(?:[/?#].*)?$");

    /** Encabezados de la pestaña del inventario (los mismos que escribe la v1, columnas A:K). */
    static final List<String> ENCABEZADOS_INVENTARIO = ColumnasInventario.ENCABEZADOS;

    /** Pestañas fijas que la app necesita y busca por nombre, con sus encabezados (los mismos que escribe la v1). */
    static final Map<String, List<String>> PESTANAS_FIJAS = pestanasFijas();

    private final ConfiguracionRepository repositorio;
    private final AccesoSheet accesoSheet;
    private final SesionGoogle sesionGoogle;
    private final Optional<SincronizacionCardKingdom> sincronizacionCardKingdom;

    /**
     * Crea el servicio. La sincronización con Card Kingdom es opcional: hasta que exista el módulo de precios (fase 3)
     * no hay ninguna y guardar precios no sincroniza.
     * Ejemplo: {@code new ConfiguracionService(new ConfiguracionEnMemoriaRepository(), sheets, sesion, Optional.empty())}.
     */
    public ConfiguracionService(
            ConfiguracionRepository repositorio,
            AccesoSheet accesoSheet,
            SesionGoogle sesionGoogle,
            Optional<SincronizacionCardKingdom> sincronizacionCardKingdom) {
        this.repositorio = repositorio;
        this.accesoSheet = accesoSheet;
        this.sesionGoogle = sesionGoogle;
        this.sincronizacionCardKingdom = sincronizacionCardKingdom;
    }

    /**
     * Lee los cuatro bloques guardados y el Gmail conectado (verifica la sesión de Google).
     * Ejemplo: tienda "Codex Live" con cotización 1200 → {@code Configuracion("Codex Live", null, sheet, "tienda@gmail.com", precios, 1000)};
     * sesión vencida → 401.
     */
    public Configuracion leerConfiguracion() {
        String gmail = sesionGoogle.gmailConectado().orElse(null);
        return new Configuracion(
                repositorio.leerNombreTienda(),
                versionLogo(),
                repositorio.leerSheet(),
                gmail,
                repositorio.leerPrecios(),
                repositorio.leerPesosPorPunto());
    }

    /**
     * Guarda el nombre de la tienda (sin espacios en los bordes) y, si se pide, quita el logo.
     * Ejemplo: {@code guardarTienda(" Codex Live Store ", false)} → {@code Tienda("Codex Live Store", <versión del logo o null>)}.
     */
    public Tienda guardarTienda(String nombre, boolean quitarLogo) {
        repositorio.guardarNombreTienda(nombre.trim());
        if (quitarLogo) {
            repositorio.quitarLogo();
        }
        return new Tienda(repositorio.leerNombreTienda(), versionLogo());
    }

    /**
     * Valida y guarda el logo nuevo (PNG, JPG o WEBP, hasta 2 MB, según su contenido). Devuelve su versión.
     * Ejemplo: PNG de 500 KB → {@code 1760550000000}; archivo de 3 MB → 400 "El logo no debe superar los 2 MB.".
     */
    public long guardarLogo(byte[] contenido) {
        if (contenido.length > PESO_MAXIMO_LOGO) {
            throw ErrorDeNegocioException.campoInvalido("archivo", MENSAJE_LOGO_PESADO);
        }
        FormatoLogo formato = formatoDe(contenido)
                .orElseThrow(() -> ErrorDeNegocioException.campoInvalido("archivo", MENSAJE_LOGO_FORMATO));
        return repositorio.guardarLogo(contenido, formato);
    }

    /**
     * Devuelve el logo guardado. Ejemplo: tienda sin logo → 404 "La tienda no tiene logo.".
     */
    public LogoTienda leerLogo() {
        return repositorio
                .leerLogo()
                .orElseThrow(() ->
                        new ErrorDeNegocioException(HttpStatus.NOT_FOUND, "Sin logo", "La tienda no tiene logo."));
    }

    /**
     * Conecta el Sheet: verifica la sesión y el acceso, deja lista la pestaña del inventario, crea las pestañas fijas
     * que falten y recién ahí guarda. Nunca renombra pestañas con datos (CFG-F02, CFG-A07). Si la pestaña pedida no existe:
     * con {@code traerCartasDe} la crea con las cartas de esa pestaña en el formato de la app (una sola escritura, el
     * origen no se toca); si el Sheet es nuevo (una sola pestaña, vacía) le pone el nombre pedido; si no, la crea vacía.
     * Ejemplo: Sheet con "stock viejo" (128 cartas) + pestaña "Stock 2026" y {@code traerCartasDe = "stock viejo"} →
     * {@code SheetConectado(id, "Stock 2026", gmail, null, true, CartasTraidas("stock viejo", 128, true))}.
     */
    public SheetConectado conectarSheet(String referenciaSheet, String pestanaPedida, String traerCartasDe) {
        String sheetId = idDeSheet(referenciaSheet);
        String pestana = pestanaPedida.trim();
        String origen = traerCartasDe == null ? "" : traerCartasDe.trim();
        String gmail = sesionGoogle.gmailConectado().orElse(null);

        List<String> pestanas = accesoSheet.listarPestanas(sheetId);
        String aviso = null;
        CartasTraidas cartasTraidas = null;
        boolean pestanaCreada = !pestanas.contains(pestana);
        if (pestanaCreada) {
            if (!origen.isEmpty()) {
                cartasTraidas = crearConCartasDe(sheetId, pestanas, origen, pestana);
            } else if (esSheetNuevo(sheetId, pestanas)) {
                accesoSheet.renombrarPestana(sheetId, pestanas.get(0), pestana);
                aviso = avisoPestanaCreada(pestana);
            } else {
                accesoSheet.crearPestana(sheetId, pestana);
                aviso = avisoPestanaCreada(pestana);
            }
            pestanas = accesoSheet.listarPestanas(sheetId);
        }
        accesoSheet.asegurarPestana(sheetId, pestanas, pestana, ENCABEZADOS_INVENTARIO);
        for (var fija : PESTANAS_FIJAS.entrySet()) {
            if (!fija.getKey().equals(pestana)) {
                accesoSheet.asegurarPestana(sheetId, pestanas, fija.getKey(), fija.getValue());
            }
        }

        repositorio.guardarSheet(new SheetGuardado(sheetId, pestana));
        return new SheetConectado(sheetId, pestana, gmail, aviso, pestanaCreada, cartasTraidas);
    }

    /**
     * Lista las pestañas del Sheet guardado o, si viene {@code idOLink}, las de ese Sheet (aunque no esté guardado).
     * Marca la del inventario ({@code enUso}), las que tienen encabezados de inventario ({@code sugerida}) y las fijas.
     * Sin {@code idOLink} y sin Sheet guardado no llama a Google. Con Sheet, verifica la sesión (401) antes de leer.
     * Ejemplo: guardado ("1vSk…", "Inventario") con pestañas "Inventario" y "Caja" →
     * {@code ListaDePestanas(true, [("Inventario", true, true, false), ("Caja", false, false, true)])}.
     */
    public ListaDePestanas listarPestanas(String idOLink) {
        SheetGuardado guardado = repositorio.leerSheet();
        String sheetId;
        if (idOLink == null || idOLink.isBlank()) {
            if (guardado == null || guardado.sheetId() == null) {
                return new ListaDePestanas(false, List.of());
            }
            sheetId = guardado.sheetId();
        } else {
            sheetId = extraerIdDeSheet(idOLink)
                    .orElseThrow(() -> ErrorDeNegocioException.datosInvalidos(
                            Map.of("idOLink", MENSAJE_SHEET_INVALIDO), "link-sheet", "LINK_INVALIDO"));
        }
        sesionGoogle.gmailConectado();
        return new ListaDePestanas(true, pestanasDe(sheetId, guardado));
    }

    /**
     * Renombra una pestaña del Sheet guardado. Si es la del inventario, guarda también el nombre nuevo en la
     * configuración; si eso falla, le vuelve a poner el nombre anterior en el Sheet y responde 500 (D4).
     * No deja renombrar Movimientos, Caja, Reservas ni Clientes (400 {@code PESTANA_FIJA}).
     * Ejemplo: {@code renombrarPestana("Inventario", "Stock 2026")} con "Inventario" como pestaña del inventario →
     * {@code PestanaRenombrada("Inventario", "Stock 2026", true, "Stock 2026", [...])}.
     */
    public PestanaRenombrada renombrarPestana(String nombreActual, String nombreNuevo) {
        String actual = nombreActual == null ? "" : nombreActual.trim();
        String nuevo = nombreNuevo == null ? "" : nombreNuevo.trim();
        validarRenombrado(actual, nuevo);

        SheetGuardado guardado = repositorio.leerSheet();
        if (guardado == null || guardado.sheetId() == null) {
            throw ErrorDeNegocioException.deBloque(
                    HttpStatus.BAD_REQUEST, "Sin Sheet guardado", MENSAJE_SIN_SHEET_GUARDADO, "SIN_SHEET_GUARDADO");
        }
        String sheetId = guardado.sheetId();
        sesionGoogle.gmailConectado();

        List<String> pestanas = accesoSheet.listarPestanas(sheetId);
        if (!pestanas.contains(actual)) {
            throw AccesoSheet.pestanaInexistente();
        }
        if (pestanas.stream().anyMatch(otra -> !otra.equals(actual) && otra.equalsIgnoreCase(nuevo))) {
            throw ErrorDeNegocioException.datosInvalidos(
                    Map.of("nombreNuevo", MENSAJE_NOMBRE_REPETIDO), "nombre-nuevo", "NOMBRE_REPETIDO");
        }

        accesoSheet.renombrarPestana(sheetId, actual, nuevo);
        boolean eraPestanaInventario = actual.equals(guardado.pestanaInventario());
        SheetGuardado despues = guardado;
        if (eraPestanaInventario) {
            despues = new SheetGuardado(sheetId, nuevo);
            guardarOCompensar(despues, sheetId, actual, nuevo);
        }
        return new PestanaRenombrada(
                actual, nuevo, eraPestanaInventario, despues.pestanaInventario(), pestanasDe(sheetId, despues));
    }

    /**
     * Valida la carpeta de cache, guarda la regla de precio y después intenta sincronizar con Card Kingdom.
     * Ejemplo: cotización 1350 y CK caído → {@code PreciosGuardados(regla, "Guardado, pero no se pudo sincronizar con Card Kingdom. Se puede reintentar más tarde.")};
     * carpeta inexistente → 400 "La carpeta no existe. Se puede indicar otra o dejar el campo vacío." y no guarda nada.
     */
    public PreciosGuardados guardarPrecios(ReglaDePrecios pedido) {
        String carpeta = carpetaCacheValida(pedido.carpetaCache());
        var regla = new ReglaDePrecios(pedido.cotizacion(), pedido.redondeo(), pedido.bulk(), carpeta);
        repositorio.guardarPrecios(regla);

        String aviso = null;
        if (sincronizacionCardKingdom.isPresent() && !sincronizoSinErrores(sincronizacionCardKingdom.get())) {
            aviso = AVISO_FALLA_CARD_KINGDOM;
        }
        return new PreciosGuardados(repositorio.leerPrecios(), aviso);
    }

    /**
     * Guarda cuántos pesos vale un punto y devuelve el valor guardado.
     * Ejemplo: {@code guardarPuntos(500)} → {@code 500.0}.
     */
    public double guardarPuntos(double pesosPorPunto) {
        repositorio.guardarPesosPorPunto(pesosPorPunto);
        return repositorio.leerPesosPorPunto();
    }

    /**
     * Saca el ID de un link completo de Google Sheets o lo acepta si ya es un ID.
     * Ejemplo: {@code "https://docs.google.com/spreadsheets/d/1vSk…/edit#gid=0"} → {@code "1vSk…"}; {@code "https://example.com"} → 400.
     */
    static String idDeSheet(String referencia) {
        return extraerIdDeSheet(referencia)
                .orElseThrow(() -> ErrorDeNegocioException.campoInvalido("sheet", MENSAJE_SHEET_INVALIDO));
    }

    private static Optional<String> extraerIdDeSheet(String referencia) {
        String valor = referencia.trim();
        Matcher link = LINK_DE_SHEET.matcher(valor);
        if (link.matches()) {
            return Optional.of(link.group(1));
        }
        Matcher id = ID_DE_SHEET.matcher(valor);
        return id.matches() ? Optional.of(id.group(1)) : Optional.empty();
    }

    /** Reconoce el formato por los primeros bytes del archivo (no por la extensión ni el tipo declarado). */
    static Optional<FormatoLogo> formatoDe(byte[] contenido) {
        if (empiezaCon(contenido, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(FormatoLogo.PNG);
        }
        if (empiezaCon(contenido, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(FormatoLogo.JPG);
        }
        if (empiezaCon(contenido, 0, 'R', 'I', 'F', 'F') && empiezaCon(contenido, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(FormatoLogo.WEBP);
        }
        return Optional.empty();
    }

    private CartasTraidas crearConCartasDe(String sheetId, List<String> pestanas, String origen, String pestana) {
        if (!pestanas.contains(origen)) {
            throw ErrorDeNegocioException.datosInvalidos(
                    Map.of("traerCartasDe", MENSAJE_ORIGEN_INEXISTENTE), "traer-cartas-de", "ORIGEN_INEXISTENTE");
        }
        var convertidas = ColumnasInventario.convertir(accesoSheet.leerValores(sheetId, origen));
        accesoSheet.crearPestanaConValores(sheetId, pestana, convertidas.filas());
        return new CartasTraidas(origen, convertidas.cantidad(), convertidas.hayColumnasExtra());
    }

    /** Sheet recién creado por Google: una sola pestaña (no fija), sin ningún dato. Se detecta por el contenido, no por el nombre. */
    private boolean esSheetNuevo(String sheetId, List<String> pestanas) {
        if (pestanas.size() != 1 || PESTANAS_FIJAS.containsKey(pestanas.get(0))) {
            return false;
        }
        return accesoSheet.leerValores(sheetId, pestanas.get(0)).stream()
                .flatMap(List::stream)
                .allMatch(valor -> valor == null || String.valueOf(valor).isBlank());
    }

    private static String avisoPestanaCreada(String pestana) {
        return "Se creó la pestaña «" + pestana + "».";
    }

    private List<PestanaDelSheet> pestanasDe(String sheetId, SheetGuardado guardado) {
        String pestanaEnUso =
                guardado != null && sheetId.equals(guardado.sheetId()) ? guardado.pestanaInventario() : null;
        List<PestanaDelSheet> lista = new ArrayList<>();
        accesoSheet.leerEncabezadosDeTodas(sheetId).forEach((nombre, encabezados) -> {
            // Las fijas (Caja, Movimientos…) son de la app: tienen columnas parecidas, pero nunca son origen de cartas.
            boolean fija = PESTANAS_FIJAS.containsKey(nombre);
            lista.add(new PestanaDelSheet(
                    nombre,
                    nombre.equals(pestanaEnUso),
                    !fija && ColumnasInventario.tieneEncabezadosDeInventario(encabezados),
                    fija));
        });
        return List.copyOf(lista);
    }

    private static void validarRenombrado(String actual, String nuevo) {
        Map<String, String> errores = new LinkedHashMap<>();
        String campo = null;
        String codigo = null;
        if (actual.isEmpty()) {
            errores.put("nombreActual", MENSAJE_SIN_PESTANA);
            campo = "pestana-a-renombrar";
            codigo = "PESTANA_VACIA";
        } else if (PESTANAS_FIJAS.containsKey(actual)) {
            errores.put("nombreActual", MENSAJE_PESTANA_FIJA);
            campo = "pestana-a-renombrar";
            codigo = "PESTANA_FIJA";
        }

        String errorNuevo = null;
        String codigoNuevo = null;
        if (nuevo.isEmpty()) {
            errorNuevo = MENSAJE_NOMBRE_VACIO;
            codigoNuevo = "NOMBRE_VACIO";
        } else if (nuevo.length() > LARGO_MAXIMO_PESTANA) {
            errorNuevo = MENSAJE_NOMBRE_LARGO;
            codigoNuevo = "NOMBRE_LARGO";
        } else if (nuevo.equals(actual)) {
            errorNuevo = MENSAJE_NOMBRE_IGUAL;
            codigoNuevo = "NOMBRE_IGUAL";
        }
        if (errorNuevo != null) {
            errores.put("nombreNuevo", errorNuevo);
            if (campo == null) {
                campo = "nombre-nuevo";
                codigo = codigoNuevo;
            }
        }
        if (!errores.isEmpty()) {
            throw ErrorDeNegocioException.datosInvalidos(errores, campo, codigo);
        }
    }

    /** Guarda la pestaña del inventario renombrada; si falla, deshace el renombrado en el Sheet y responde 500 (D4). */
    private void guardarOCompensar(SheetGuardado sheet, String sheetId, String nombreAnterior, String nombreNuevo) {
        try {
            repositorio.guardarSheet(sheet);
        } catch (RuntimeException falla) {
            log.error(
                    "Se renombró «{}» a «{}» pero no se pudo guardar la configuración; se deshace el renombrado.",
                    nombreAnterior,
                    nombreNuevo,
                    falla);
            try {
                accesoSheet.renombrarPestana(sheetId, nombreNuevo, nombreAnterior);
            } catch (RuntimeException fallaAlDeshacer) {
                log.error(
                        "No se pudo volver a llamar «{}» a la pestaña «{}».",
                        nombreAnterior,
                        nombreNuevo,
                        fallaAlDeshacer);
            }
            throw AccesoSheet.errorInesperado();
        }
    }

    private static boolean empiezaCon(byte[] contenido, int desde, int... firma) {
        if (contenido.length < desde + firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if ((contenido[desde + i] & 0xFF) != firma[i]) {
                return false;
            }
        }
        return true;
    }

    private String carpetaCacheValida(String carpeta) {
        if (carpeta == null || carpeta.isBlank()) {
            return null;
        }
        try {
            var ruta = Paths.get(carpeta.trim()).normalize();
            if (Files.isDirectory(ruta)) {
                return ruta.toString();
            }
        } catch (InvalidPathException e) {
            // Una ruta con caracteres inválidos tampoco es una carpeta que exista.
        }
        throw ErrorDeNegocioException.campoInvalido("carpetaCache", MENSAJE_CARPETA);
    }

    private boolean sincronizoSinErrores(SincronizacionCardKingdom sincronizacion) {
        try {
            return sincronizacion.sincronizarPrecios();
        } catch (RuntimeException e) {
            log.warn("Precios guardados, pero falló la sincronización con Card Kingdom.", e);
            return false;
        }
    }

    private Long versionLogo() {
        return repositorio.leerLogo().map(LogoTienda::version).orElse(null);
    }

    private static Map<String, List<String>> pestanasFijas() {
        Map<String, List<String>> pestanas = new LinkedHashMap<>();
        pestanas.put(
                "Movimientos",
                List.of(
                        "Fecha",
                        "Hora",
                        "Cantidad",
                        "Nombre",
                        "Nombre del set",
                        "Codigo de set",
                        "Numero de carta",
                        "Printing",
                        "Stock anterior",
                        "Stock nuevo",
                        "Accion",
                        "Condicion",
                        "Cliente",
                        "DNI",
                        "ReservaId"));
        pestanas.put(
                "Caja",
                List.of(
                        "Fecha",
                        "Hora",
                        "Nombre",
                        "Nombre del set",
                        "Codigo de set",
                        "Numero de carta",
                        "Printing",
                        "Precio Local",
                        "Cantidad",
                        "Total",
                        "Total Dia"));
        pestanas.put(
                "Reservas",
                List.of(
                        "ID",
                        "Estado",
                        "Nombre",
                        "Nombre del set",
                        "Codigo de set",
                        "Numero de carta",
                        "Printing",
                        "Cantidad",
                        "Cliente",
                        "Telefono",
                        "DNI",
                        "Fecha de reserva",
                        "Fecha de retiro",
                        "Fecha de pago",
                        "Notas",
                        "Condicion"));
        pestanas.put(
                "Clientes",
                List.of("Nombre", "Apellido", "DNI", "Telefono", "Email", "Notas", "Puntos", "Actualizado"));
        return Collections.unmodifiableMap(pestanas);
    }

    /**
     * Recalcula los precios del inventario con la regla nueva (Card Kingdom). La implementa el módulo de precios
     * (fase 3); mientras no exista, guardar precios no sincroniza.
     * Ejemplo: CK no responde → {@code sincronizarPrecios()} devuelve {@code false} (o lanza) y la respuesta trae el aviso.
     */
    @FunctionalInterface
    public interface SincronizacionCardKingdom {
        /** Sincroniza y devuelve si salió bien. Ejemplo: CK disponible → {@code true}. */
        boolean sincronizarPrecios();
    }

    /**
     * Configuración completa. {@code versionLogo} y {@code gmail} son {@code null} si no hay.
     * Ejemplo: {@code new Configuracion("Codex Live", null, sheet, "tienda@gmail.com", precios, 1000)}.
     */
    public record Configuracion(
            String nombreTienda,
            Long versionLogo,
            SheetGuardado sheet,
            String gmail,
            ReglaDePrecios precios,
            double pesosPorPunto) {}

    /**
     * Tienda guardada. Ejemplo: {@code new Tienda("Codex Live Store", null)}.
     */
    public record Tienda(String nombre, Long versionLogo) {}

    /**
     * Resultado de conectar el Sheet. {@code aviso} solo si se creó la pestaña sin traer cartas; {@code cartasTraidas}
     * solo si se crearon con cartas de otra pestaña.
     * Ejemplo: {@code new SheetConectado("1Abc…", "Inventario", "tienda@gmail.com", "Se creó la pestaña «Inventario».", true, null)}.
     */
    public record SheetConectado(
            String sheetId,
            String pestanaInventario,
            String gmail,
            String aviso,
            boolean pestanaCreada,
            CartasTraidas cartasTraidas) {}

    /**
     * Cartas copiadas al crear la pestaña del inventario. Ejemplo: {@code new CartasTraidas("stock viejo", 128, true)}.
     */
    public record CartasTraidas(String origen, int cantidad, boolean hayColumnasExtra) {}

    /**
     * Pestañas de un Sheet; {@code sheetGuardado} es {@code false} si no había Sheet para listar.
     * Ejemplo: {@code new ListaDePestanas(false, List.of())}.
     */
    public record ListaDePestanas(boolean sheetGuardado, List<PestanaDelSheet> pestanas) {}

    /**
     * Una pestaña del Sheet con sus marcas. Ejemplo: {@code new PestanaDelSheet("Caja", false, false, true)}.
     */
    public record PestanaDelSheet(String nombre, boolean enUso, boolean sugerida, boolean fija) {}

    /**
     * Resultado de renombrar una pestaña, con la lista actualizada.
     * Ejemplo: {@code new PestanaRenombrada("Hoja 1", "Stock 2026", false, "Inventario", pestanas)}.
     */
    public record PestanaRenombrada(
            String nombreAnterior,
            String nombreNuevo,
            boolean eraPestanaInventario,
            String nombrePestanaInventario,
            List<PestanaDelSheet> pestanas) {}

    /**
     * Regla de precio guardada; {@code aviso} es {@code null} si sincronizó bien (o no hay sincronización).
     * Ejemplo: {@code new PreciosGuardados(regla, null)}.
     */
    public record PreciosGuardados(ReglaDePrecios precios, String aviso) {}
}
