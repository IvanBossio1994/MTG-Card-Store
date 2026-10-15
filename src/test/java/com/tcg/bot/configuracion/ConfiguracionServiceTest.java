package com.tcg.bot.configuracion;

import com.tcg.bot.compartido.AccesoSheet;
import com.tcg.bot.compartido.AccesoSheetEnMemoria;
import com.tcg.bot.compartido.ErrorDeNegocioException;
import com.tcg.bot.compartido.SesionGoogle;
import com.tcg.bot.configuracion.ConfiguracionRepository.FormatoLogo;
import com.tcg.bot.configuracion.ConfiguracionRepository.PreciosBulk;
import com.tcg.bot.configuracion.ConfiguracionRepository.ReglaDePrecios;
import com.tcg.bot.configuracion.ConfiguracionRepository.SheetGuardado;
import com.tcg.bot.configuracion.ConfiguracionService.SincronizacionCardKingdom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas de Configuración (HU-003, CA1 a CA12) con el repositorio y los Sheets en memoria. */
class ConfiguracionServiceTest {

    private static final String SHEET_INVENTARIO = "1SheetConInventario00000000000000000000000";
    private static final String SHEET_UNICA = "1SheetConPestanaUnica000000000000000000000";
    private static final String SHEET_VARIAS = "1SheetConVariasPestanas0000000000000000000";
    private static final String SHEET_NO_COMPARTIDO = "1SheetNoCompartido000000000000000000000000";
    private static final PreciosBulk BULK = new PreciosBulk(50, 100, 150, 150, 200);

    private ConfiguracionEnMemoriaRepository repositorio;
    private AccesoSheetEnMemoria sheets;
    private final AtomicBoolean sesionVencida = new AtomicBoolean(false);
    private ConfiguracionService servicio;

    @BeforeEach
    void preparar() {
        repositorio = new ConfiguracionEnMemoriaRepository();
        sheets = new AccesoSheetEnMemoria()
                .compartir(SHEET_INVENTARIO, "Inventario")
                .compartir(SHEET_UNICA, "Hoja 1")
                .compartir(SHEET_VARIAS, "Hoja 1", "Ventas");
        servicio = servicioCon(Optional.empty());
    }

    private ConfiguracionService servicioCon(Optional<SincronizacionCardKingdom> sincronizacion) {
        SesionGoogle sesion = () -> {
            if (sesionVencida.get()) {
                throw SesionGoogle.sesionVencida();
            }
            return Optional.of("tienda@gmail.com");
        };
        return new ConfiguracionService(repositorio, sheets, sesion, sincronizacion);
    }

    // ---- CA1 ----

    @Test
    void leeLosCuatroBloquesYElGmailConectado() {
        var configuracion = servicio.leerConfiguracion();

        assertThat(configuracion.nombreTienda()).isEqualTo("Codex Live");
        assertThat(configuracion.sheet()).isEqualTo(
                new SheetGuardado(ConfiguracionEnMemoriaRepository.SHEET_DE_EJEMPLO, "Inventario"));
        assertThat(configuracion.gmail()).isEqualTo("tienda@gmail.com");
        assertThat(configuracion.precios().cotizacion()).isEqualTo(1200);
        assertThat(configuracion.pesosPorPunto()).isEqualTo(1000);
        assertThat(configuracion.versionLogo()).isNull();
    }

    @Test
    void leerConLaSesionVencidaDa401() {
        sesionVencida.set(true);

        assertThatThrownBy(() -> servicio.leerConfiguracion())
                .isInstanceOfSatisfying(ErrorDeNegocioException.class, error -> {
                    assertThat(error.getEstado()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(error.getMessage()).isEqualTo("Tu sesión de Google venció.");
                    assertThat(error.getPropiedades()).containsEntry("accion", "volver-a-iniciar-sesion");
                });
    }

    // ---- CA2 ----

    @Test
    void guardaElNombreDeLaTiendaSinEspaciosEnLosBordes() {
        var tienda = servicio.guardarTienda("  Codex Live Store ", false);

        assertThat(tienda.nombre()).isEqualTo("Codex Live Store");
        assertThat(repositorio.leerNombreTienda()).isEqualTo("Codex Live Store");
    }

    @Test
    void quitarLogoBorraElLogoGuardado() {
        servicio.guardarLogo(png(1000));

        var tienda = servicio.guardarTienda("Codex Live", true);

        assertThat(tienda.versionLogo()).isNull();
        assertThat(repositorio.leerLogo()).isEmpty();
    }

    // ---- CA4 ----

    @Test
    void guardaUnLogoPngDe500Kb() {
        long version = servicio.guardarLogo(png(500 * 1024));

        assertThat(version).isPositive();
        assertThat(servicio.leerLogo().formato()).isEqualTo(FormatoLogo.PNG);
    }

    @Test
    void reconoceJpgYWebpPorSuContenido() {
        assertThat(ConfiguracionService.formatoDe(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}))
                .contains(FormatoLogo.JPG);
        assertThat(ConfiguracionService.formatoDe("RIFF\0\0\0\0WEBPVP8 ".getBytes())).contains(FormatoLogo.WEBP);
        assertThat(ConfiguracionService.formatoDe("GIF89a".getBytes())).isEmpty();
    }

    @Test
    void unLogoDeMasDe2MbSeRechazaYNoCambiaElAnterior() {
        long anterior = servicio.guardarLogo(png(100));

        assertThatThrownBy(() -> servicio.guardarLogo(png((int) ConfiguracionService.PESO_MAXIMO_LOGO + 1)))
                .satisfies(error -> assertThat(errores(error)).containsEntry("archivo", "Usá un logo de hasta 2 MB."));
        assertThat(servicio.leerLogo().version()).isEqualTo(anterior);
    }

    @Test
    void unArchivoQueNoEsImagenSeRechaza() {
        assertThatThrownBy(() -> servicio.guardarLogo("hola".getBytes()))
                .satisfies(error -> assertThat(errores(error)).containsEntry("archivo", "Usá un PNG, JPG o WEBP."));
        assertThat(repositorio.leerLogo()).isEmpty();
    }

    @Test
    void sinLogoLeerLogoDa404() {
        assertThatThrownBy(() -> servicio.leerLogo())
                .isInstanceOfSatisfying(ErrorDeNegocioException.class,
                        error -> assertThat(error.getEstado()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---- CA5 ----

    @Test
    void conectaUnSheetConLaPestanaExistenteSinAvisoYCreaLasQueFaltan() {
        var conectado = servicio.conectarSheet(
                "https://docs.google.com/spreadsheets/d/" + SHEET_INVENTARIO + "/edit#gid=0", "Inventario");

        assertThat(conectado.sheetId()).isEqualTo(SHEET_INVENTARIO);
        assertThat(conectado.pestanaInventario()).isEqualTo("Inventario");
        assertThat(conectado.aviso()).isNull();
        assertThat(conectado.gmail()).isEqualTo("tienda@gmail.com");
        assertThat(sheets.listarPestanas(SHEET_INVENTARIO))
                .containsExactly("Inventario", "Movimientos", "Caja", "Reservas", "Clientes");
        assertThat(sheets.leerEncabezados(SHEET_INVENTARIO, "Caja"))
                .isEqualTo(ConfiguracionService.PESTANAS_FIJAS.get("Caja"));
        assertThat(sheets.leerEncabezados(SHEET_INVENTARIO, "Inventario"))
                .isEqualTo(ConfiguracionService.ENCABEZADOS_INVENTARIO);
        assertThat(repositorio.leerSheet()).isEqualTo(new SheetGuardado(SHEET_INVENTARIO, "Inventario"));
    }

    @Test
    void noPisaLosEncabezadosQueYaTieneElSheet() {
        sheets.escribirEncabezados(SHEET_INVENTARIO, "Inventario", List.of("Cant", "Carta"));

        servicio.conectarSheet(SHEET_INVENTARIO, "Inventario");

        assertThat(sheets.leerEncabezados(SHEET_INVENTARIO, "Inventario")).containsExactly("Cant", "Carta");
    }

    @Test
    void aceptaElIdSoloYElLinkCompleto() {
        assertThat(ConfiguracionService.idDeSheet(SHEET_INVENTARIO)).isEqualTo(SHEET_INVENTARIO);
        assertThat(ConfiguracionService.idDeSheet(" https://docs.google.com/spreadsheets/d/" + SHEET_INVENTARIO + "/edit "))
                .isEqualTo(SHEET_INVENTARIO);
        assertThat(ConfiguracionService.idDeSheet("https://docs.google.com/spreadsheets/u/0/d/" + SHEET_INVENTARIO))
                .isEqualTo(SHEET_INVENTARIO);
    }

    // ---- CA6 ----

    @Test
    void renombraLaPestanaUnicaYGuardaElNombrePedido() {
        var conectado = servicio.conectarSheet(SHEET_UNICA, "Inventario");

        assertThat(conectado.aviso()).isEqualTo("La pestaña «Hoja 1» se renombró a «Inventario».");
        assertThat(sheets.listarPestanas(SHEET_UNICA)).startsWith("Inventario").doesNotContain("Hoja 1");
        assertThat(repositorio.leerSheet().pestanaInventario()).isEqualTo("Inventario");
    }

    // ---- CA7 ----

    @Test
    void conVariasPestanasYSinLaPedidaLaCreaSinTocarLasDemas() {
        sheets.escribirEncabezados(SHEET_VARIAS, "Ventas", List.of("Fecha", "Total"));

        var conectado = servicio.conectarSheet(SHEET_VARIAS, " Inventario ");

        assertThat(conectado.aviso()).isEqualTo("Se creó la pestaña «Inventario».");
        assertThat(sheets.listarPestanas(SHEET_VARIAS)).startsWith("Hoja 1", "Ventas", "Inventario");
        assertThat(sheets.leerEncabezados(SHEET_VARIAS, "Inventario")).isEqualTo(ConfiguracionService.ENCABEZADOS_INVENTARIO);
        assertThat(sheets.leerEncabezados(SHEET_VARIAS, "Ventas")).containsExactly("Fecha", "Total");
        assertThat(sheets.leerEncabezados(SHEET_VARIAS, "Hoja 1")).isEmpty();
    }

    @Test
    void conVariasPestanasCambiarElNombreRenombraLaDelInventarioDeAntesSinPerderDatos() {
        servicio.conectarSheet(SHEET_VARIAS, "Inventario");

        var conectado = servicio.conectarSheet(SHEET_VARIAS, "Inventario Test");

        assertThat(conectado.aviso()).isEqualTo("La pestaña «Inventario» se renombró a «Inventario Test».");
        assertThat(sheets.listarPestanas(SHEET_VARIAS)).contains("Inventario Test").doesNotContain("Inventario");
        assertThat(sheets.leerEncabezados(SHEET_VARIAS, "Inventario Test")).isEqualTo(ConfiguracionService.ENCABEZADOS_INVENTARIO);
        assertThat(repositorio.leerSheet().pestanaInventario()).isEqualTo("Inventario Test");
    }

    @Test
    void siLaPestanaPedidaYaExisteLaUsaSinRenombrarLaDeAntes() {
        servicio.conectarSheet(SHEET_VARIAS, "Inventario");

        var conectado = servicio.conectarSheet(SHEET_VARIAS, "Ventas");

        assertThat(conectado.aviso()).isNull();
        assertThat(sheets.listarPestanas(SHEET_VARIAS)).contains("Inventario", "Ventas");
    }

    // ---- CA8 ----

    @Test
    void unLinkQueNoEsDeGoogleSheetsDa400YNoCambiaLoGuardado() {
        var antes = repositorio.leerSheet();

        for (String link : List.of("https://example.com/hoja", "hola", "https://docs.google.com/document/d/" + SHEET_INVENTARIO)) {
            assertThatThrownBy(() -> servicio.conectarSheet(link, "Inventario"))
                    .satisfies(error -> assertThat(errores(error)).containsEntry("sheet", "Pegá una URL de Google Sheets o solo el ID."));
        }
        assertThat(repositorio.leerSheet()).isEqualTo(antes);
    }

    // ---- CA9 ----

    @Test
    void unSheetNoCompartidoDa403ConLaCuentaDeServicioYNoCambiaLoGuardado() {
        var antes = repositorio.leerSheet();

        assertThatThrownBy(() -> servicio.conectarSheet(SHEET_NO_COMPARTIDO, "Inventario"))
                .isInstanceOfSatisfying(ErrorDeNegocioException.class, error -> {
                    assertThat(error.getEstado()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.getMessage()).isEqualTo("La app no tiene acceso a este Sheet. Compartilo con "
                            + "tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com como editor.");
                    assertThat(error.getPropiedades()).containsEntry("cuentaServicio", AccesoSheet.CUENTA_SERVICIO);
                });
        assertThat(repositorio.leerSheet()).isEqualTo(antes);
    }

    // ---- CA10 ----

    @Test
    void conectarConLaSesionVencidaDa401YNoTocaElSheet() {
        sesionVencida.set(true);
        var antes = repositorio.leerSheet();

        assertThatThrownBy(() -> servicio.conectarSheet(SHEET_UNICA, "Inventario"))
                .isInstanceOfSatisfying(ErrorDeNegocioException.class,
                        error -> assertThat(error.getEstado()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThat(sheets.listarPestanas(SHEET_UNICA)).containsExactly("Hoja 1");
        assertThat(repositorio.leerSheet()).isEqualTo(antes);
    }

    // ---- CA11 ----

    @Test
    void guardaLosPreciosSinCarpetaDeCacheYSinAviso() {
        var guardados = servicio.guardarPrecios(new ReglaDePrecios(1350, 1000, BULK, "  "));

        assertThat(guardados.aviso()).isNull();
        assertThat(guardados.precios()).isEqualTo(new ReglaDePrecios(1350, 1000, BULK, null));
        assertThat(repositorio.leerPrecios().cotizacion()).isEqualTo(1350);
        assertThat(repositorio.leerPesosPorPunto()).isEqualTo(1000);
    }

    @Test
    void guardaUnaCarpetaDeCacheQueExiste(@TempDir Path carpeta) {
        var guardados = servicio.guardarPrecios(new ReglaDePrecios(1350, 500, BULK, carpeta.toString()));

        assertThat(guardados.precios().carpetaCache()).isEqualTo(carpeta.normalize().toString());
    }

    @Test
    void unaCarpetaDeCacheQueNoExisteDa400YNoGuardaNada(@TempDir Path carpeta) {
        String inexistente = carpeta.resolve("no-existe").toString();

        assertThatThrownBy(() -> servicio.guardarPrecios(new ReglaDePrecios(1350, 500, BULK, inexistente)))
                .satisfies(error -> assertThat(errores(error)).containsEntry("carpetaCache", "Esa carpeta no existe. Elegí otra o dejala vacía."));
        assertThat(repositorio.leerPrecios().cotizacion()).isEqualTo(1200);
    }

    @Test
    void siFallaCardKingdomGuardaIgualYAvisa() {
        servicio = servicioCon(Optional.of(() -> false));

        var guardados = servicio.guardarPrecios(new ReglaDePrecios(1350, 500, BULK, null));

        assertThat(guardados.aviso()).isEqualTo("Guardado, pero no se pudo sincronizar con Card Kingdom. Probá más tarde.");
        assertThat(repositorio.leerPrecios().cotizacion()).isEqualTo(1350);
    }

    @Test
    void siCardKingdomTiraErrorGuardaIgualYAvisa() {
        servicio = servicioCon(Optional.of(() -> {
            throw new IllegalStateException("CK caído");
        }));

        assertThat(servicio.guardarPrecios(new ReglaDePrecios(1350, 500, BULK, null)).aviso())
                .isEqualTo(ConfiguracionService.AVISO_FALLA_CARD_KINGDOM);
    }

    @Test
    void siCardKingdomSincronizaNoHayAviso() {
        servicio = servicioCon(Optional.of(() -> true));

        assertThat(servicio.guardarPrecios(new ReglaDePrecios(1350, 500, BULK, null)).aviso()).isNull();
    }

    // ---- CA12 ----

    @Test
    void guardaLosPesosPorPuntoSinTocarLosPrecios() {
        assertThat(servicio.guardarPuntos(500)).isEqualTo(500);
        assertThat(repositorio.leerPrecios().cotizacion()).isEqualTo(1200);
    }

    // ---- Ayudas ----

    static byte[] png(int bytes) {
        byte[] contenido = new byte[Math.max(bytes, 8)];
        byte[] firma = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(firma, 0, contenido, 0, firma.length);
        return contenido;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> errores(Throwable error) {
        assertThat(error).isInstanceOf(ErrorDeNegocioException.class);
        var negocio = (ErrorDeNegocioException) error;
        assertThat(negocio.getEstado()).isEqualTo(HttpStatus.BAD_REQUEST);
        return (Map<String, String>) negocio.getPropiedades().get("errores");
    }
}
