package com.tcg.bot.configuracion;

import com.tcg.bot.compartido.AccesoSheet;
import com.tcg.bot.compartido.ErrorDeNegocioException;
import com.tcg.bot.compartido.SesionGoogle;
import com.tcg.bot.configuracion.ConfiguracionRepository.FormatoLogo;
import com.tcg.bot.configuracion.ConfiguracionRepository.LogoTienda;
import com.tcg.bot.configuracion.ConfiguracionRepository.ReglaDePrecios;
import com.tcg.bot.configuracion.ConfiguracionRepository.SheetGuardado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reglas de negocio de Configuración (CFG-F01 a CFG-F06): validar cada bloque, conectar el Sheet con la cuenta de
 * servicio (renombrar o crear la pestaña del inventario y crear las pestañas que falten) y guardar.
 * Contrato: docs/v2/api/configuracion.md.
 */
@Service
public class ConfiguracionService {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionService.class);

    static final long PESO_MAXIMO_LOGO = 2L * 1024L * 1024L;
    static final String MENSAJE_LOGO_PESADO = "Usá un logo de hasta 2 MB.";
    static final String MENSAJE_LOGO_FORMATO = "Usá un PNG, JPG o WEBP.";
    static final String MENSAJE_SHEET_INVALIDO = "Pegá una URL de Google Sheets o solo el ID.";
    static final String MENSAJE_CARPETA = "Esa carpeta no existe. Elegí otra o dejala vacía.";
    static final String AVISO_FALLA_CARD_KINGDOM = "Guardado, pero no se pudo sincronizar con Card Kingdom. Probá más tarde.";

    private static final Pattern LINK_DE_SHEET =
            Pattern.compile("^https?://docs\\.google\\.com/spreadsheets/(?:u/\\d+/)?d/([A-Za-z0-9_-]{20,})(?:[/?#].*)?$");
    /** ID solo, o el ID con el final de la URL pegado (ej.: {@code 1vSk7…/edit?gid=0#gid=0}). */
    private static final Pattern ID_DE_SHEET = Pattern.compile("^([A-Za-z0-9_-]{20,})(?:[/?#].*)?$");

    /** Encabezados de la pestaña del inventario (los mismos que escribe la v1, columnas A:K). */
    static final List<String> ENCABEZADOS_INVENTARIO = List.of("Cantidad", "Nombre", "Codigo de set", "Nombre del set",
            "Numero de carta", "Condicion", "Printing (foil/no foil)", "Idioma", "Precio Local", "Precio CK USD", "Accion");

    /** Pestañas fijas que la app necesita, con sus encabezados (los mismos que escribe la v1). */
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
    public ConfiguracionService(ConfiguracionRepository repositorio, AccesoSheet accesoSheet, SesionGoogle sesionGoogle,
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
        return new Configuracion(repositorio.leerNombreTienda(), versionLogo(), repositorio.leerSheet(), gmail,
                repositorio.leerPrecios(), repositorio.leerPesosPorPunto());
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
     * Ejemplo: PNG de 500 KB → {@code 1760550000000}; archivo de 3 MB → 400 "Usá un logo de hasta 2 MB.".
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
        return repositorio.leerLogo().orElseThrow(() ->
                new ErrorDeNegocioException(HttpStatus.NOT_FOUND, "Sin logo", "La tienda no tiene logo."));
    }

    /**
     * Conecta el Sheet: verifica la sesión y el acceso, deja lista la pestaña del inventario, crea las pestañas fijas
     * que falten y recién ahí guarda. Si la pestaña pedida no existe: renombra la del inventario de antes (mismo Sheet,
     * así no se pierden los datos), o la única pestaña del Sheet; si no hay ninguna de las dos, la crea (CFG-F03, CFG-A07).
     * Ejemplo: Sheet con "Inventario", "Caja"… guardado con "Inventario" + pestaña "Inventario Test" →
     * {@code SheetConectado(id, "Inventario Test", gmail, "La pestaña «Inventario» se renombró a «Inventario Test».")}.
     */
    public SheetConectado conectarSheet(String referenciaSheet, String pestanaPedida) {
        String sheetId = idDeSheet(referenciaSheet);
        String pestana = pestanaPedida.trim();
        String gmail = sesionGoogle.gmailConectado().orElse(null);

        List<String> pestanas = accesoSheet.listarPestanas(sheetId);
        String aviso = null;
        if (!pestanas.contains(pestana)) {
            String anterior = pestanaARenombrar(sheetId, pestanas);
            if (anterior != null) {
                accesoSheet.renombrarPestana(sheetId, anterior, pestana);
                aviso = "La pestaña «" + anterior + "» se renombró a «" + pestana + "».";
            } else {
                accesoSheet.crearPestana(sheetId, pestana);
                aviso = "Se creó la pestaña «" + pestana + "».";
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
        return new SheetConectado(sheetId, pestana, gmail, aviso);
    }

    /**
     * Qué pestaña renombrar cuando la pedida no existe: la del inventario guardada antes, si es el mismo Sheet y sigue
     * ahí (nunca una fija, como "Caja"); si no, la única pestaña del Sheet; si no, ninguna ({@code null}: se crea).
     * Ejemplo: guardado ("1vSk…", "Inventario") y el Sheet tiene "Inventario" y "Caja" → "Inventario".
     */
    private String pestanaARenombrar(String sheetId, List<String> pestanas) {
        SheetGuardado guardado = repositorio.leerSheet();
        if (guardado != null && sheetId.equals(guardado.sheetId()) && guardado.pestanaInventario() != null
                && pestanas.contains(guardado.pestanaInventario())
                && !PESTANAS_FIJAS.containsKey(guardado.pestanaInventario())) {
            return guardado.pestanaInventario();
        }
        return pestanas.size() == 1 ? pestanas.get(0) : null;
    }

    /**
     * Valida la carpeta de cache, guarda la regla de precio y después intenta sincronizar con Card Kingdom.
     * Ejemplo: cotización 1350 y CK caído → {@code PreciosGuardados(regla, "Guardado, pero no se pudo sincronizar con Card Kingdom. Probá más tarde.")};
     * carpeta inexistente → 400 "Esa carpeta no existe. Elegí otra o dejala vacía." y no guarda nada.
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
        String valor = referencia.trim();
        Matcher link = LINK_DE_SHEET.matcher(valor);
        if (link.matches()) {
            return link.group(1);
        }
        Matcher id = ID_DE_SHEET.matcher(valor);
        if (id.matches()) {
            return id.group(1);
        }
        throw ErrorDeNegocioException.campoInvalido("sheet", MENSAJE_SHEET_INVALIDO);
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
        pestanas.put("Movimientos", List.of("Fecha", "Hora", "Cantidad", "Nombre", "Nombre del set", "Codigo de set",
                "Numero de carta", "Printing", "Stock anterior", "Stock nuevo", "Accion", "Condicion", "Cliente", "DNI",
                "ReservaId"));
        pestanas.put("Caja", List.of("Fecha", "Hora", "Nombre", "Nombre del set", "Codigo de set", "Numero de carta",
                "Printing", "Precio Local", "Cantidad", "Total", "Total Dia"));
        pestanas.put("Reservas", List.of("ID", "Estado", "Nombre", "Nombre del set", "Codigo de set", "Numero de carta",
                "Printing", "Cantidad", "Cliente", "Telefono", "DNI", "Fecha de reserva", "Fecha de retiro",
                "Fecha de pago", "Notas", "Condicion"));
        pestanas.put("Clientes", List.of("Nombre", "Apellido", "DNI", "Telefono", "Email", "Notas", "Puntos",
                "Actualizado"));
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
    public record Configuracion(String nombreTienda, Long versionLogo, SheetGuardado sheet, String gmail,
                                ReglaDePrecios precios, double pesosPorPunto) {
    }

    /**
     * Tienda guardada. Ejemplo: {@code new Tienda("Codex Live Store", null)}.
     */
    public record Tienda(String nombre, Long versionLogo) {
    }

    /**
     * Resultado de conectar el Sheet; {@code aviso} es {@code null} si la pestaña ya existía.
     * Ejemplo: {@code new SheetConectado("1Abc…", "Inventario", "tienda@gmail.com", "Se creó la pestaña «Inventario».")}.
     */
    public record SheetConectado(String sheetId, String pestanaInventario, String gmail, String aviso) {
    }

    /**
     * Regla de precio guardada; {@code aviso} es {@code null} si sincronizó bien (o no hay sincronización).
     * Ejemplo: {@code new PreciosGuardados(regla, null)}.
     */
    public record PreciosGuardados(ReglaDePrecios precios, String aviso) {
    }
}
