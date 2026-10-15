package com.tcg.bot.configuracion;

import com.tcg.bot.compartido.AccesoSheet;
import com.tcg.bot.compartido.AccesoSheetEnMemoria;
import com.tcg.bot.compartido.ErrorDeNegocioException;
import com.tcg.bot.compartido.SesionGoogle;
import com.tcg.bot.configuracion.ConfiguracionRepository.FormatoLogo;
import com.tcg.bot.configuracion.ConfiguracionRepository.PreciosBulk;
import com.tcg.bot.configuracion.ConfiguracionRepository.ReglaDePrecios;
import com.tcg.bot.configuracion.ConfiguracionRepository.SheetGuardado;
import com.tcg.bot.configuracion.ConfiguracionService.CartasTraidas;
import com.tcg.bot.configuracion.ConfiguracionService.PestanaDelSheet;
import com.tcg.bot.configuracion.ConfiguracionService.SincronizacionCardKingdom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas de Configuración (HU-003 CA1 a CA12; HU-006 CA1 a CA10) con el repositorio y los Sheets en memoria. */
class ConfiguracionServiceTest {

    private static final String SHEET_INVENTARIO = "1SheetConInventario00000000000000000000000";
    private static final String SHEET_UNICA = "1SheetConPestanaUnica000000000000000000000";
    private static final String SHEET_VARIAS = "1SheetConVariasPestanas0000000000000000000";
    private static final String SHEET_STOCK_VIEJO = "1SheetConStockViejo00000000000000000000000";
    private static final String SHEET_NO_COMPARTIDO = "1SheetNoCompartido000000000000000000000000";
    private static final PreciosBulk BULK = new PreciosBulk(50, 100, 150, 150, 200);
    private static final List<Object> ENCABEZADOS = List.copyOf(ConfiguracionService.ENCABEZADOS_INVENTARIO);

    private RepositorioQuePuedeFallar repositorio;
    private AccesoSheetEnMemoria sheets;
    private final AtomicBoolean sesionVencida = new AtomicBoolean(false);
    private ConfiguracionService servicio;

    /** Repositorio en memoria al que se le puede pedir que falle al guardar el Sheet (para D4). */
    static class RepositorioQuePuedeFallar extends ConfiguracionEnMemoriaRepository {
        final AtomicBoolean fallarAlGuardarSheet = new AtomicBoolean(false);

        @Override
        public synchronized void guardarSheet(SheetGuardado sheet) {
            if (fallarAlGuardarSheet.get()) {
                throw new IllegalStateException("No se pudo escribir store.properties");
            }
            super.guardarSheet(sheet);
        }
    }

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioQuePuedeFallar();
        sheets = new AccesoSheetEnMemoria()
                .compartir(SHEET_INVENTARIO, "Inventario")
                .compartir(SHEET_UNICA, "Hoja 1")
                .compartir(SHEET_VARIAS, "Hoja 1", "Ventas")
                .compartir(SHEET_STOCK_VIEJO, "Inventario", "stock viejo", "Caja")
                .cargarFilas(SHEET_STOCK_VIEJO, "Inventario", List.of(ENCABEZADOS))
                .cargarFilas(SHEET_STOCK_VIEJO, "stock viejo", List.of(
                        List.of("Carta", "Stock", "Set", "Precio", "Notas"),
                        List.of("Sol Ring", 4, "Commander Masters", 1500, "firmada"),
                        List.of("", "", "", "", ""),
                        List.of("Opt", 2, "Dominaria", 300, "")))
                .cargarFilas(SHEET_STOCK_VIEJO, "Caja", List.of(List.of("Fecha", "Total")));
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

    // ---- HU-003 CA1 ----

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
                    assertThat(error.getMessage()).isEqualTo("La sesión de Google venció.");
                    assertThat(error.getPropiedades()).containsEntry("accion", "volver-a-iniciar-sesion");
                });
    }

    // ---- HU-003 CA2 a CA4 ----

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
                .satisfies(error -> assertThat(errores(error)).containsEntry("archivo", "El logo no debe superar los 2 MB."));
        assertThat(servicio.leerLogo().version()).isEqualTo(anterior);
    }

    @Test
    void unArchivoQueNoEsImagenSeRechaza() {
        assertThatThrownBy(() -> servicio.guardarLogo("hola".getBytes()))
                .satisfies(error -> assertThat(errores(error)).containsEntry("archivo", "El logo debe ser PNG, JPG o WEBP."));
        assertThat(repositorio.leerLogo()).isEmpty();
    }

    @Test
    void sinLogoLeerLogoDa404() {
        assertThatThrownBy(() -> servicio.leerLogo())
                .isInstanceOfSatisfying(ErrorDeNegocioException.class,
                        error -> assertThat(error.getEstado()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---- PUT /sheet: pestaña existente (HU-003 CA5) ----

    @Test
    void conectaUnSheetConLaPestanaExistenteSinAvisoYCreaLasQueFaltan() {
        var conectado = servicio.conectarSheet(
                "https://docs.google.com/spreadsheets/d/" + SHEET_INVENTARIO + "/edit#gid=0", "Inventario", null);

        assertThat(conectado.sheetId()).isEqualTo(SHEET_INVENTARIO);
        assertThat(conectado.pestanaInventario()).isEqualTo("Inventario");
        assertThat(conectado.aviso()).isNull();
        assertThat(conectado.pestanaCreada()).isFalse();
        assertThat(conectado.cartasTraidas()).isNull();
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

        servicio.conectarSheet(SHEET_INVENTARIO, "Inventario", null);

        assertThat(sheets.leerEncabezados(SHEET_INVENTARIO, "Inventario")).containsExactly("Cant", "Carta");
    }

    @Test
    void aceptaElIdSoloYElLinkCompleto() {
        assertThat(ConfiguracionService.idDeSheet(SHEET_INVENTARIO)).isEqualTo(SHEET_INVENTARIO);
        assertThat(ConfiguracionService.idDeSheet(" https://docs.google.com/spreadsheets/d/" + SHEET_INVENTARIO + "/edit "))
                .isEqualTo(SHEET_INVENTARIO);
        assertThat(ConfiguracionService.idDeSheet("https://docs.google.com/spreadsheets/u/0/d/" + SHEET_INVENTARIO))
                .isEqualTo(SHEET_INVENTARIO);
        assertThat(ConfiguracionService.idDeSheet(SHEET_INVENTARIO + "/edit?gid=0#gid=0")).isEqualTo(SHEET_INVENTARIO);
    }

    // ---- PUT /sheet sin renombrar (HU-006 CA8) ----

    @Test
    void conUnSheetNuevoUsaSuPestanaVaciaEnLugarDeCrearOtra() {
        var conectado = servicio.conectarSheet(SHEET_UNICA, "Inventario", null);

        assertThat(conectado.aviso()).isEqualTo("Se creó la pestaña «Inventario».");
        assertThat(conectado.pestanaCreada()).isTrue();
        assertThat(sheets.listarPestanas(SHEET_UNICA)).startsWith("Inventario").doesNotContain("Hoja 1");
        assertThat(sheets.leerEncabezados(SHEET_UNICA, "Inventario")).isEqualTo(ConfiguracionService.ENCABEZADOS_INVENTARIO);
        assertThat(repositorio.leerSheet().pestanaInventario()).isEqualTo("Inventario");
    }

    @Test
    void conUnaSolaPestanaConDatosCreaLaPedidaYNoLaRenombra() {
        sheets.cargarFilas(SHEET_UNICA, "Hoja 1", List.of(List.of("Carta", "Stock"), List.of("Sol Ring", 4)));

        var conectado = servicio.conectarSheet(SHEET_UNICA, "Inventario", null);

        assertThat(conectado.aviso()).isEqualTo("Se creó la pestaña «Inventario».");
        assertThat(conectado.pestanaCreada()).isTrue();
        assertThat(sheets.listarPestanas(SHEET_UNICA)).startsWith("Hoja 1", "Inventario");
        assertThat(sheets.leerValores(SHEET_UNICA, "Hoja 1")).hasSize(2);
    }

    @Test
    void conVariasPestanasYSinLaPedidaLaCreaSinTocarLasDemas() {
        sheets.escribirEncabezados(SHEET_VARIAS, "Ventas", List.of("Fecha", "Total"));

        var conectado = servicio.conectarSheet(SHEET_VARIAS, " Inventario ", "  ");

        assertThat(conectado.aviso()).isEqualTo("Se creó la pestaña «Inventario».");
        assertThat(conectado.cartasTraidas()).isNull();
        assertThat(sheets.listarPestanas(SHEET_VARIAS)).startsWith("Hoja 1", "Ventas", "Inventario");
        assertThat(sheets.leerEncabezados(SHEET_VARIAS, "Inventario")).isEqualTo(ConfiguracionService.ENCABEZADOS_INVENTARIO);
        assertThat(sheets.leerEncabezados(SHEET_VARIAS, "Ventas")).containsExactly("Fecha", "Total");
        assertThat(sheets.leerEncabezados(SHEET_VARIAS, "Hoja 1")).isEmpty();
    }

    @Test
    void cambiarElNombreDeLaPestanaYaNoRenombraLaDeAntes() {
        servicio.conectarSheet(SHEET_VARIAS, "Inventario", null);

        var conectado = servicio.conectarSheet(SHEET_VARIAS, "Inventario Test", null);

        assertThat(conectado.aviso()).isEqualTo("Se creó la pestaña «Inventario Test».");
        assertThat(sheets.listarPestanas(SHEET_VARIAS)).contains("Inventario", "Inventario Test");
        assertThat(repositorio.leerSheet().pestanaInventario()).isEqualTo("Inventario Test");
    }

    // ---- PUT /sheet trayendo cartas (HU-006 CA7 y CA9) ----

    @Test
    void creaLaPestanaConLasCartasDelOrigenEnElFormatoDeLaAppYNoTocaElOrigen() {
        var origenAntes = sheets.leerValores(SHEET_STOCK_VIEJO, "stock viejo");

        var conectado = servicio.conectarSheet(SHEET_STOCK_VIEJO, "Stock 2026", "stock viejo");

        assertThat(conectado.pestanaCreada()).isTrue();
        assertThat(conectado.aviso()).isNull();
        assertThat(conectado.cartasTraidas()).isEqualTo(new CartasTraidas("stock viejo", 2, true));
        var filas = sheets.leerValores(SHEET_STOCK_VIEJO, "Stock 2026");
        assertThat(filas).hasSize(3);
        assertThat(filas.get(0)).startsWith(ENCABEZADOS.toArray()).endsWith("Notas").hasSize(12);
        assertThat(filas.get(1)).containsExactly(4, "Sol Ring", "", "Commander Masters", "", "", "", "", 1500, "", "", "firmada");
        assertThat(filas.get(2)).containsExactly(2, "Opt", "", "Dominaria", "", "", "", "", 300, "", "", "");
        assertThat(sheets.leerValores(SHEET_STOCK_VIEJO, "stock viejo")).isEqualTo(origenAntes);
        assertThat(sheets.listarPestanas(SHEET_STOCK_VIEJO)).contains("Movimientos", "Reservas", "Clientes");
        assertThat(repositorio.leerSheet()).isEqualTo(new SheetGuardado(SHEET_STOCK_VIEJO, "Stock 2026"));
    }

    @Test
    void siLaPestanaDelInventarioYaExisteIgnoraTraerCartasDe() {
        var conectado = servicio.conectarSheet(SHEET_STOCK_VIEJO, "Inventario", "no existe");

        assertThat(conectado.pestanaCreada()).isFalse();
        assertThat(conectado.cartasTraidas()).isNull();
        assertThat(sheets.leerValores(SHEET_STOCK_VIEJO, "Inventario")).hasSize(1);
    }

    @Test
    void siElOrigenNoExisteDa400OrigenInexistenteYNoCreaNada() {
        var antes = repositorio.leerSheet();

        assertThatThrownBy(() -> servicio.conectarSheet(SHEET_STOCK_VIEJO, "Stock 2026", "borrada"))
                .isInstanceOfSatisfying(ErrorDeNegocioException.class, error -> {
                    assertThat(error.getEstado()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getPropiedades())
                            .containsEntry("campo", "traer-cartas-de")
                            .containsEntry("codigo", "ORIGEN_INEXISTENTE")
                            .containsEntry("errores", Map.of("traerCartasDe",
                                    "La pestaña de origen ya no existe en el Sheet. Se actualizó la lista."));
                });
        assertThat(sheets.listarPestanas(SHEET_STOCK_VIEJO)).containsExactly("Inventario", "stock viejo", "Caja");
        assertThat(repositorio.leerSheet()).isEqualTo(antes);
    }

    // ---- PUT /sheet: errores (HU-003 CA8 a CA10; HU-006 CA10) ----

    @Test
    void unLinkQueNoEsDeGoogleSheetsDa400YNoCambiaLoGuardado() {
        var antes = repositorio.leerSheet();

        for (String link : List.of("https://example.com/hoja", "hola", "https://docs.google.com/document/d/" + SHEET_INVENTARIO)) {
            assertThatThrownBy(() -> servicio.conectarSheet(link, "Inventario", null))
                    .satisfies(error -> assertThat(errores(error)).containsEntry("sheet", "Debe ser una URL de Google Sheets o solo el ID."));
        }
        assertThat(repositorio.leerSheet()).isEqualTo(antes);
    }

    @Test
    void unSheetNoCompartidoDa403ConLaCuentaDeServicioYNoCambiaLoGuardado() {
        var antes = repositorio.leerSheet();

        assertThatThrownBy(() -> servicio.conectarSheet(SHEET_NO_COMPARTIDO, "Inventario", null))
                .isInstanceOfSatisfying(ErrorDeNegocioException.class, error -> {
                    assertThat(error.getEstado()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.getMessage()).isEqualTo("La app no tiene acceso a este Sheet. Compartirlo con "
                            + "tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com como editor.");
                    assertThat(error.getPropiedades()).containsEntry("cuentaServicio", AccesoSheet.CUENTA_SERVICIO);
                });
        assertThat(repositorio.leerSheet()).isEqualTo(antes);
    }

    @Test
    void conectarConLaSesionVencidaDa401YNoTocaElSheet() {
        sesionVencida.set(true);
        var antes = repositorio.leerSheet();

        assertThatThrownBy(() -> servicio.conectarSheet(SHEET_STOCK_VIEJO, "Stock 2026", "stock viejo"))
                .isInstanceOfSatisfying(ErrorDeNegocioException.class,
                        error -> assertThat(error.getEstado()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThat(sheets.listarPestanas(SHEET_STOCK_VIEJO)).doesNotContain("Stock 2026");
        assertThat(repositorio.leerSheet()).isEqualTo(antes);
    }

    // ---- Listar pestañas (HU-006 CA1 a CA3, CA10) ----

    @Test
    void listaLasPestanasDelSheetGuardadoEnOrdenConSusMarcas() {
        repositorio.guardarSheet(new SheetGuardado(SHEET_STOCK_VIEJO, "Inventario"));

        var lista = servicio.listarPestanas(null);

        assertThat(lista.sheetGuardado()).isTrue();
        assertThat(lista.pestanas()).containsExactly(
                new PestanaDelSheet("Inventario", true, true, false),
                new PestanaDelSheet("stock viejo", false, true, false),
                new PestanaDelSheet("Caja", false, false, true));
    }

    @Test
    void conIdOLinkListaLasDeEseSheetSinMarcarNingunaEnUso() {
        repositorio.guardarSheet(new SheetGuardado(SHEET_INVENTARIO, "Inventario"));

        var lista = servicio.listarPestanas("https://docs.google.com/spreadsheets/d/" + SHEET_STOCK_VIEJO + "/edit");

        assertThat(lista.sheetGuardado()).isTrue();
        assertThat(lista.pestanas()).extracting(PestanaDelSheet::nombre).containsExactly("Inventario", "stock viejo", "Caja");
        assertThat(lista.pestanas()).noneMatch(PestanaDelSheet::enUso);
    }

    @Test
    void sinSheetGuardadoNiIdOLinkDevuelveListaVaciaSinLlamarAGoogle() {
        repositorio.guardarSheet(new SheetGuardado(null, "Inventario"));
        sesionVencida.set(true);

        var lista = servicio.listarPestanas(" ");

        assertThat(lista.sheetGuardado()).isFalse();
        assertThat(lista.pestanas()).isEmpty();
    }

    @Test
    void unIdOLinkInvalidoDa400LinkInvalido() {
        assertThatThrownBy(() -> servicio.listarPestanas("https://example.com"))
                .isInstanceOfSatisfying(ErrorDeNegocioException.class, error -> {
                    assertThat(error.getEstado()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getPropiedades())
                            .containsEntry("campo", "link-sheet")
                            .containsEntry("codigo", "LINK_INVALIDO")
                            .containsEntry("errores", Map.of("idOLink", "Debe ser una URL de Google Sheets o solo el ID."));
                });
    }

    @Test
    void listarConIdOLinkYSesionVencidaDa401() {
        sesionVencida.set(true);

        assertThatThrownBy(() -> servicio.listarPestanas(SHEET_STOCK_VIEJO))
                .isInstanceOfSatisfying(ErrorDeNegocioException.class,
                        error -> assertThat(error.getEstado()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void listarUnSheetNoCompartidoDa403() {
        assertThatThrownBy(() -> servicio.listarPestanas(SHEET_NO_COMPARTIDO))
                .isInstanceOfSatisfying(ErrorDeNegocioException.class,
                        error -> assertThat(error.getEstado()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ---- Renombrar (HU-006 CA4 a CA6, D1, D4) ----

    @Test
    void renombraUnaPestanaQueNoEsLaDelInventario() {
        repositorio.guardarSheet(new SheetGuardado(SHEET_VARIAS, "Ventas"));

        var renombrada = servicio.renombrarPestana(" Hoja 1 ", " Stock 2026 ");

        assertThat(renombrada.nombreAnterior()).isEqualTo("Hoja 1");
        assertThat(renombrada.nombreNuevo()).isEqualTo("Stock 2026");
        assertThat(renombrada.eraPestanaInventario()).isFalse();
        assertThat(renombrada.nombrePestanaInventario()).isEqualTo("Ventas");
        assertThat(renombrada.pestanas()).extracting(PestanaDelSheet::nombre).containsExactly("Stock 2026", "Ventas");
        assertThat(sheets.listarPestanas(SHEET_VARIAS)).containsExactly("Stock 2026", "Ventas");
        assertThat(repositorio.leerSheet()).isEqualTo(new SheetGuardado(SHEET_VARIAS, "Ventas"));
    }

    @Test
    void renombrarLaPestanaDelInventarioGuardaElNombreNuevo() {
        repositorio.guardarSheet(new SheetGuardado(SHEET_STOCK_VIEJO, "Inventario"));

        var renombrada = servicio.renombrarPestana("Inventario", "Stock 2026");

        assertThat(renombrada.eraPestanaInventario()).isTrue();
        assertThat(renombrada.nombrePestanaInventario()).isEqualTo("Stock 2026");
        assertThat(renombrada.pestanas().get(0)).isEqualTo(new PestanaDelSheet("Stock 2026", true, true, false));
        assertThat(repositorio.leerSheet()).isEqualTo(new SheetGuardado(SHEET_STOCK_VIEJO, "Stock 2026"));
    }

    @Test
    void cambiarSoloMayusculasSePermite() {
        repositorio.guardarSheet(new SheetGuardado(SHEET_VARIAS, "Ventas"));

        assertThat(servicio.renombrarPestana("Hoja 1", "HOJA 1").nombreNuevo()).isEqualTo("HOJA 1");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULO", value = {
            "NULO        | Stock 2026 | nombreActual | pestana-a-renombrar | PESTANA_VACIA   | La pestaña a renombrar es obligatoria.",
            "Caja        | Stock 2026 | nombreActual | pestana-a-renombrar | PESTANA_FIJA    | Esa pestaña la usa la app y no se puede renombrar.",
            "Hoja 1      | '  '       | nombreNuevo  | nombre-nuevo        | NOMBRE_VACIO    | El nombre nuevo es obligatorio.",
            "Hoja 1      | Hoja 1     | nombreNuevo  | nombre-nuevo        | NOMBRE_IGUAL    | El nombre nuevo es igual al actual.",
            "Hoja 1      | ventas     | nombreNuevo  | nombre-nuevo        | NOMBRE_REPETIDO | Ya existe una pestaña con ese nombre."})
    void erroresDeValidacionAlRenombrar(String actual, String nuevo, String claveJson, String campo, String codigo, String mensaje) {
        repositorio.guardarSheet(new SheetGuardado(SHEET_VARIAS, "Ventas"));
        sheets.crearPestana(SHEET_VARIAS, "Caja");

        assertThatThrownBy(() -> servicio.renombrarPestana(actual, nuevo))
                .isInstanceOfSatisfying(ErrorDeNegocioException.class, error -> {
                    assertThat(error.getEstado()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getPropiedades()).containsEntry("campo", campo).containsEntry("codigo", codigo);
                    assertThat(errores(error)).containsEntry(claveJson, mensaje);
                });
        assertThat(sheets.listarPestanas(SHEET_VARIAS)).containsExactly("Hoja 1", "Ventas", "Caja");
    }

    @Test
    void unNombreDeMasDe100CaracteresDa400NombreLargo() {
        assertThatThrownBy(() -> servicio.renombrarPestana("Hoja 1", "x".repeat(101)))
                .isInstanceOfSatisfying(ErrorDeNegocioException.class, error ->
                        assertThat(error.getPropiedades()).containsEntry("codigo", "NOMBRE_LARGO")
                                .containsEntry("errores", Map.of("nombreNuevo", "El nombre no debe superar los 100 caracteres.")));
    }

    @Test
    void siFallanLosDosCamposRespondeConElPrimeroYTraeAmbosErrores() {
        assertThatThrownBy(() -> servicio.renombrarPestana("", ""))
                .isInstanceOfSatisfying(ErrorDeNegocioException.class, error -> {
                    assertThat(error.getPropiedades()).containsEntry("campo", "pestana-a-renombrar")
                            .containsEntry("codigo", "PESTANA_VACIA");
                    assertThat(errores(error)).containsOnlyKeys("nombreActual", "nombreNuevo");
                });
    }

    @Test
    void siLaPestanaYaNoExisteDa404() {
        repositorio.guardarSheet(new SheetGuardado(SHEET_VARIAS, "Ventas"));

        assertThatThrownBy(() -> servicio.renombrarPestana("Borrada", "Stock 2026"))
                .isInstanceOfSatisfying(ErrorDeNegocioException.class, error -> {
                    assertThat(error.getEstado()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(error.getMessage()).isEqualTo("La pestaña ya no existe en el Sheet. Se actualizó la lista.");
                    assertThat(error.getPropiedades()).containsEntry("codigo", "PESTANA_INEXISTENTE").containsEntry("campo", null);
                });
    }

    @Test
    void sinSheetGuardadoDa400SinSheetGuardado() {
        repositorio.guardarSheet(new SheetGuardado(null, "Inventario"));

        assertThatThrownBy(() -> servicio.renombrarPestana("Hoja 1", "Stock 2026"))
                .isInstanceOfSatisfying(ErrorDeNegocioException.class, error -> {
                    assertThat(error.getEstado()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getTitulo()).isEqualTo("Sin Sheet guardado");
                    assertThat(error.getMessage()).isEqualTo("Disponible cuando haya un Sheet guardado.");
                    assertThat(error.getPropiedades()).containsEntry("codigo", "SIN_SHEET_GUARDADO");
                });
    }

    @Test
    void renombrarConLaSesionVencidaDa401YNoTocaElSheet() {
        repositorio.guardarSheet(new SheetGuardado(SHEET_VARIAS, "Ventas"));
        sesionVencida.set(true);

        assertThatThrownBy(() -> servicio.renombrarPestana("Hoja 1", "Stock 2026"))
                .isInstanceOfSatisfying(ErrorDeNegocioException.class,
                        error -> assertThat(error.getEstado()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThat(sheets.listarPestanas(SHEET_VARIAS)).containsExactly("Hoja 1", "Ventas");
    }

    @Test
    void siFallaGuardarLaConfiguracionVuelveElNombreAnteriorYDa500() {
        repositorio.guardarSheet(new SheetGuardado(SHEET_STOCK_VIEJO, "Inventario"));
        repositorio.fallarAlGuardarSheet.set(true);

        assertThatThrownBy(() -> servicio.renombrarPestana("Inventario", "Stock 2026"))
                .isInstanceOfSatisfying(ErrorDeNegocioException.class, error -> {
                    assertThat(error.getEstado()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(error.getMessage())
                            .isEqualTo("No se pudo guardar por un error inesperado. Se puede reintentar en un momento.");
                });
        assertThat(sheets.listarPestanas(SHEET_STOCK_VIEJO)).containsExactly("Inventario", "stock viejo", "Caja");
        assertThat(repositorio.leerSheet()).isEqualTo(new SheetGuardado(SHEET_STOCK_VIEJO, "Inventario"));
    }

    // ---- HU-003 CA11 y CA12 ----

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
                .satisfies(error -> assertThat(errores(error)).containsEntry("carpetaCache",
                        "La carpeta no existe. Se puede indicar otra o dejar el campo vacío."));
        assertThat(repositorio.leerPrecios().cotizacion()).isEqualTo(1200);
    }

    @Test
    void siFallaCardKingdomGuardaIgualYAvisa() {
        servicio = servicioCon(Optional.of(() -> false));

        var guardados = servicio.guardarPrecios(new ReglaDePrecios(1350, 500, BULK, null));

        assertThat(guardados.aviso())
                .isEqualTo("Guardado, pero no se pudo sincronizar con Card Kingdom. Se puede reintentar más tarde.");
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
