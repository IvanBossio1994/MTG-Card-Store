package com.tcg.bot.configuracion;

import com.tcg.bot.compartido.AccesoSheet;
import com.tcg.bot.compartido.AccesoSheetEnMemoria;
import com.tcg.bot.compartido.SesionGoogle;
import com.tcg.bot.configuracion.ConfiguracionRepository.PreciosBulk;
import com.tcg.bot.configuracion.ConfiguracionRepository.ReglaDePrecios;
import com.tcg.bot.configuracion.ConfiguracionRepository.SheetGuardado;
import com.tcg.bot.configuracion.ConfiguracionService.SincronizacionCardKingdom;
import com.tcg.bot.configuracion.ConfiguracionServiceTest.RepositorioQuePuedeFallar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API de Configuración (HU-003 CA1 a CA12; HU-006 CA1 a CA10): códigos HTTP y forma del JSON según docs/v2/api/configuracion.md. */
@WebMvcTest(ConfiguracionController.class)
@Import({ConfiguracionService.class, ConfiguracionControllerTest.Dobles.class})
class ConfiguracionControllerTest {

    private static final String SHEET_INVENTARIO = "1SheetConInventario00000000000000000000000";
    private static final String SHEET_UNICA = "1SheetConPestanaUnica000000000000000000000";
    private static final String SHEET_VARIAS = "1SheetConVariasPestanas0000000000000000000";
    private static final String SHEET_STOCK_VIEJO = "1SheetConStockViejo00000000000000000000000";
    private static final String SHEET_NO_COMPARTIDO = "1SheetNoCompartido000000000000000000000000";
    private static final String RUTA_PESTANAS = "/api/v1/configuracion/sheet/pestanas";
    private static final String RUTA_RENOMBRAR = "/api/v1/configuracion/sheet/pestanas/renombrar";
    private static final String PRECIOS_VALIDOS = """
            {"cotizacion":1350,"redondeo":500,
             "bulk":{"common":50,"uncommon":100,"rare":150,"mythic":150,"tierraBasica":200},"carpetaCache":null}""";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RepositorioQuePuedeFallar repositorio;
    @Autowired
    private AccesoSheetEnMemoria sheets;
    @Autowired
    private Dobles dobles;

    @TestConfiguration
    static class Dobles {
        final AtomicBoolean sesionVencida = new AtomicBoolean(false);
        final AtomicBoolean carpetaSesionSinPermiso = new AtomicBoolean(false);
        final AtomicBoolean cardKingdomFalla = new AtomicBoolean(false);

        @Bean
        RepositorioQuePuedeFallar configuracionRepository() {
            return new RepositorioQuePuedeFallar();
        }

        @Bean
        AccesoSheetEnMemoria accesoSheet() {
            return new AccesoSheetEnMemoria();
        }

        @Bean
        SesionGoogle sesionGoogle() {
            return () -> {
                if (sesionVencida.get()) {
                    throw SesionGoogle.sesionVencida();
                }
                if (carpetaSesionSinPermiso.get()) {
                    throw SesionGoogle.sinPermisoEnCarpeta();
                }
                return Optional.of("tienda@gmail.com");
            };
        }

        @Bean
        SincronizacionCardKingdom sincronizacionCardKingdom() {
            return () -> !cardKingdomFalla.get();
        }
    }

    @BeforeEach
    void preparar() {
        dobles.sesionVencida.set(false);
        dobles.carpetaSesionSinPermiso.set(false);
        dobles.cardKingdomFalla.set(false);
        repositorio.fallarAlGuardarSheet.set(false);
        repositorio.guardarNombreTienda("Codex Live");
        repositorio.quitarLogo();
        repositorio.guardarSheet(new SheetGuardado(SHEET_INVENTARIO, "Inventario"));
        repositorio.guardarPrecios(new ReglaDePrecios(1200, 500, new PreciosBulk(50, 100, 150, 150, 200), null));
        repositorio.guardarPesosPorPunto(1000);
        List<Object> encabezados = List.copyOf(ConfiguracionService.ENCABEZADOS_INVENTARIO);
        sheets.compartir(SHEET_INVENTARIO, "Inventario")
                .compartir(SHEET_UNICA, "Hoja 1")
                .compartir(SHEET_VARIAS, "Hoja 1", "Ventas")
                .compartir(SHEET_STOCK_VIEJO, "Inventario", "stock viejo", "Caja")
                .cargarFilas(SHEET_STOCK_VIEJO, "Inventario", List.of(encabezados))
                .cargarFilas(SHEET_STOCK_VIEJO, "stock viejo", List.of(
                        List.of("Carta", "Stock", "Notas"),
                        List.of("Sol Ring", 4, "firmada"),
                        List.of("", "", ""),
                        List.of("Opt", 2, ""),
                        List.of("Counterspell", 1, "")));
    }

    // ---- HU-003 CA1 ----

    @Test
    void getDevuelveLosCuatroBloquesConElGmail() throws Exception {
        mockMvc.perform(get("/api/v1/configuracion"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tienda.nombre").value("Codex Live"))
                .andExpect(jsonPath("$.tienda.logoUrl").value(nullValue()))
                .andExpect(jsonPath("$.sheet.sheetId").value(SHEET_INVENTARIO))
                .andExpect(jsonPath("$.sheet.link").value("https://docs.google.com/spreadsheets/d/" + SHEET_INVENTARIO + "/edit"))
                .andExpect(jsonPath("$.sheet.pestanaInventario").value("Inventario"))
                .andExpect(jsonPath("$.sheet.gmail").value("tienda@gmail.com"))
                .andExpect(jsonPath("$.sheet.aviso").value(nullValue()))
                .andExpect(jsonPath("$.sheet.pestanaCreada").value(false))
                .andExpect(jsonPath("$.sheet.cartasTraidas").value(nullValue()))
                .andExpect(jsonPath("$.precios.cotizacion").value(1200.0))
                .andExpect(jsonPath("$.precios.redondeo").value(500))
                .andExpect(jsonPath("$.precios.bulk.common").value(50))
                .andExpect(jsonPath("$.precios.bulk.tierraBasica").value(200))
                .andExpect(jsonPath("$.precios.carpetaCache").value(nullValue()))
                .andExpect(jsonPath("$.precios.aviso").value(nullValue()))
                .andExpect(jsonPath("$.puntos.pesosPorPunto").value(1000.0));
    }

    // ---- HU-003 CA2 a CA4 ----

    @Test
    void putTiendaGuardaElNombre() throws Exception {
        putJson("/api/v1/configuracion/tienda", "{\"nombre\":\"Codex Live Store\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Codex Live Store"))
                .andExpect(jsonPath("$.logoUrl").value(nullValue()));
        assertThat(repositorio.leerNombreTienda()).isEqualTo("Codex Live Store");
    }

    @Test
    void putTiendaConNombreVacioDa400YNoGuarda() throws Exception {
        esErrorDeCampo(putJson("/api/v1/configuracion/tienda", "{\"nombre\":\"  \"}"),
                "nombre", "El nombre de la tienda es obligatorio.");
        assertThat(repositorio.leerNombreTienda()).isEqualTo("Codex Live");
    }

    @Test
    void putTiendaConQuitarLogoBorraElLogo() throws Exception {
        subirLogo(ConfiguracionServiceTest.png(100), "image/png").andExpect(status().isOk());

        putJson("/api/v1/configuracion/tienda", "{\"nombre\":\"Codex Live\",\"quitarLogo\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").value(nullValue()));
        assertThat(repositorio.leerLogo()).isEmpty();
    }

    @Test
    void postLogoPngDe500KbDevuelveLaDireccionYSePuedeVer() throws Exception {
        subirLogo(ConfiguracionServiceTest.png(500 * 1024), "image/png")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").value(startsWith("/api/v1/configuracion/tienda/logo?version=")));

        mockMvc.perform(get("/api/v1/configuracion/tienda/logo"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
        mockMvc.perform(get("/api/v1/configuracion"))
                .andExpect(jsonPath("$.tienda.logoUrl").value(startsWith("/api/v1/configuracion/tienda/logo?version=")));
    }

    @Test
    void postLogoDeMasDe2MbDa400() throws Exception {
        esErrorDeCampo(subirLogo(ConfiguracionServiceTest.png(2 * 1024 * 1024 + 1), "image/png"),
                "archivo", "El logo no debe superar los 2 MB.");
        assertThat(repositorio.leerLogo()).isEmpty();
    }

    @Test
    void postLogoQueNoEsImagenDa400() throws Exception {
        esErrorDeCampo(subirLogo("no soy una imagen".getBytes(), "image/png"),
                "archivo", "El logo debe ser PNG, JPG o WEBP.");
    }

    @Test
    void postLogoSinArchivoDa400() throws Exception {
        mockMvc.perform(multipart("/api/v1/configuracion/tienda/logo"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Pedido inválido"));
    }

    @Test
    void getLogoSinLogoDa404() throws Exception {
        mockMvc.perform(get("/api/v1/configuracion/tienda/logo"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La tienda no tiene logo."));
    }

    // ---- PUT /sheet (HU-003 CA5 a CA10; HU-006 CA7 a CA10) ----

    @Test
    void putSheetConLaPestanaExistenteNoTraeAvisoYCreaLasQueFaltan() throws Exception {
        putSheet("https://docs.google.com/spreadsheets/d/" + SHEET_INVENTARIO + "/edit#gid=0", "Inventario", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sheetId").value(SHEET_INVENTARIO))
                .andExpect(jsonPath("$.pestanaInventario").value("Inventario"))
                .andExpect(jsonPath("$.gmail").value("tienda@gmail.com"))
                .andExpect(jsonPath("$.aviso").value(nullValue()))
                .andExpect(jsonPath("$.pestanaCreada").value(false))
                .andExpect(jsonPath("$.cartasTraidas").value(nullValue()));
        assertThat(sheets.listarPestanas(SHEET_INVENTARIO)).contains("Movimientos", "Caja", "Reservas", "Clientes");
    }

    @Test
    void putSheetConUnSheetNuevoUsaLaPestanaVaciaYAvisaQueSeCreo() throws Exception {
        putSheet(SHEET_UNICA, "Inventario", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pestanaInventario").value("Inventario"))
                .andExpect(jsonPath("$.aviso").value("Se creó la pestaña «Inventario»."))
                .andExpect(jsonPath("$.pestanaCreada").value(true));
        assertThat(sheets.listarPestanas(SHEET_UNICA)).first().isEqualTo("Inventario");
        assertThat(repositorio.leerSheet()).isEqualTo(new SheetGuardado(SHEET_UNICA, "Inventario"));
    }

    @Test
    void putSheetConUnaSolaPestanaConDatosCreaLaPedidaSinRenombrar() throws Exception {
        sheets.cargarFilas(SHEET_UNICA, "Hoja 1", List.of(List.of("Carta", "Stock"), List.of("Opt", 2)));

        putSheet(SHEET_UNICA, "Inventario", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aviso").value("Se creó la pestaña «Inventario»."))
                .andExpect(jsonPath("$.pestanaCreada").value(true));
        assertThat(sheets.listarPestanas(SHEET_UNICA)).startsWith("Hoja 1", "Inventario");
    }

    @Test
    void putSheetConVariasPestanasCreaLaPedidaYAvisa() throws Exception {
        putSheet(SHEET_VARIAS, "Inventario", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aviso").value("Se creó la pestaña «Inventario»."));
        assertThat(sheets.listarPestanas(SHEET_VARIAS)).startsWith("Hoja 1", "Ventas", "Inventario");
    }

    @Test
    void putSheetTrayendoCartasCreaLaPestanaYRespondeCartasTraidas() throws Exception {
        putSheet(SHEET_STOCK_VIEJO, "Stock 2026", "stock viejo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pestanaInventario").value("Stock 2026"))
                .andExpect(jsonPath("$.aviso").value(nullValue()))
                .andExpect(jsonPath("$.pestanaCreada").value(true))
                .andExpect(jsonPath("$.cartasTraidas.origen").value("stock viejo"))
                .andExpect(jsonPath("$.cartasTraidas.cantidad").value(3))
                .andExpect(jsonPath("$.cartasTraidas.hayColumnasExtra").value(true));
        assertThat(sheets.leerValores(SHEET_STOCK_VIEJO, "Stock 2026")).hasSize(4);
        assertThat(sheets.leerValores(SHEET_STOCK_VIEJO, "stock viejo")).hasSize(5);
    }

    @Test
    void putSheetConPestanaExistenteIgnoraTraerCartasDe() throws Exception {
        putSheet(SHEET_STOCK_VIEJO, "Inventario", "no existe")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pestanaCreada").value(false))
                .andExpect(jsonPath("$.cartasTraidas").value(nullValue()));
    }

    @Test
    void putSheetConOrigenInexistenteDa400ConCampoYCodigo() throws Exception {
        putSheet(SHEET_STOCK_VIEJO, "Stock 2026", "borrada")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.detail").value("Hay datos para corregir."))
                .andExpect(jsonPath("$.errores.traerCartasDe").value("La pestaña de origen ya no existe en el Sheet. Se actualizó la lista."))
                .andExpect(jsonPath("$.campo").value("traer-cartas-de"))
                .andExpect(jsonPath("$.codigo").value("ORIGEN_INEXISTENTE"));
        assertThat(sheets.listarPestanas(SHEET_STOCK_VIEJO)).doesNotContain("Stock 2026");
        assertThat(repositorio.leerSheet().sheetId()).isEqualTo(SHEET_INVENTARIO);
    }

    @Test
    void putSheetConLinkVacioDa400() throws Exception {
        esErrorDeCampo(putSheet("", "Inventario", null), "sheet", "El ID o la URL del Sheet es obligatorio.");
    }

    @Test
    void putSheetConLinkQueNoEsDeGoogleDa400YNoCambiaLoGuardado() throws Exception {
        esErrorDeCampo(putSheet("https://example.com/planilla", "Inventario", null), "sheet",
                "Debe ser una URL de Google Sheets o solo el ID.");
        assertThat(repositorio.leerSheet().sheetId()).isEqualTo(SHEET_INVENTARIO);
    }

    @Test
    void putSheetConPestanaVaciaDa400() throws Exception {
        esErrorDeCampo(putSheet(SHEET_UNICA, " ", null), "pestanaInventario", "El nombre de la pestaña es obligatorio.");
        assertThat(sheets.listarPestanas(SHEET_UNICA)).containsExactly("Hoja 1");
    }

    @Test
    void putSheetNoCompartidoDa403ConLaCuentaDeServicio() throws Exception {
        esSinAcceso(putSheet(SHEET_NO_COMPARTIDO, "Inventario", null), "/api/v1/configuracion/sheet");
        assertThat(repositorio.leerSheet().sheetId()).isEqualTo(SHEET_INVENTARIO);
    }

    @Test
    void conLaSesionVencidaGetPutSheetListarYRenombrarDan401ConVolverAIniciarSesion() throws Exception {
        dobles.sesionVencida.set(true);

        for (ResultActions respuesta : new ResultActions[]{
                mockMvc.perform(get("/api/v1/configuracion")),
                putSheet(SHEET_UNICA, "Inventario", null),
                mockMvc.perform(get(RUTA_PESTANAS)),
                mockMvc.perform(get(RUTA_PESTANAS).param("idOLink", SHEET_VARIAS)),
                renombrar("Inventario", "Stock 2026")}) {
            respuesta.andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Sesión de Google vencida"))
                    .andExpect(jsonPath("$.detail").value("La sesión de Google venció."))
                    .andExpect(jsonPath("$.accion").value("volver-a-iniciar-sesion"))
                    .andExpect(jsonPath("$.urlInicioSesion").value(SesionGoogle.URL_INICIO_SESION));
        }
        assertThat(sheets.listarPestanas(SHEET_UNICA)).containsExactly("Hoja 1");
        assertThat(sheets.listarPestanas(SHEET_INVENTARIO)).containsExactly("Inventario");
    }

    @Test
    void conLaCarpetaDeLaSesionSinPermisoDa500ConElMotivoYNoDiceQueEsInternet() throws Exception {
        dobles.carpetaSesionSinPermiso.set(true);

        mockMvc.perform(get("/api/v1/configuracion"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Sesión de Google ilegible"))
                .andExpect(jsonPath("$.detail").value("La app no puede leer la sesión de Google en esta PC. "
                        + "Abrirla sin permisos de administrador e iniciar sesión de nuevo."));
    }

    // ---- GET /sheet/pestanas (HU-006 CA1 a CA3, CA10) ----

    @Test
    void getPestanasDelSheetGuardadoEnOrdenConSusMarcas() throws Exception {
        repositorio.guardarSheet(new SheetGuardado(SHEET_STOCK_VIEJO, "Inventario"));

        mockMvc.perform(get(RUTA_PESTANAS))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sheetGuardado").value(true))
                .andExpect(jsonPath("$.pestanas", hasSize(3)))
                .andExpect(jsonPath("$.pestanas[0].nombre").value("Inventario"))
                .andExpect(jsonPath("$.pestanas[0].enUso").value(true))
                .andExpect(jsonPath("$.pestanas[0].sugerida").value(true))
                .andExpect(jsonPath("$.pestanas[0].fija").value(false))
                .andExpect(jsonPath("$.pestanas[1].nombre").value("stock viejo"))
                .andExpect(jsonPath("$.pestanas[1].enUso").value(false))
                .andExpect(jsonPath("$.pestanas[1].sugerida").value(true))
                .andExpect(jsonPath("$.pestanas[2].nombre").value("Caja"))
                .andExpect(jsonPath("$.pestanas[2].sugerida").value(false))
                .andExpect(jsonPath("$.pestanas[2].fija").value(true));
    }

    @Test
    void getPestanasConIdOLinkDeOtroSheet() throws Exception {
        mockMvc.perform(get(RUTA_PESTANAS)
                        .param("idOLink", "https://docs.google.com/spreadsheets/d/" + SHEET_VARIAS + "/edit#gid=0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sheetGuardado").value(true))
                .andExpect(jsonPath("$.pestanas[0].nombre").value("Hoja 1"))
                .andExpect(jsonPath("$.pestanas[0].enUso").value(false))
                .andExpect(jsonPath("$.pestanas[1].nombre").value("Ventas"));
    }

    @Test
    void getPestanasSinSheetGuardadoDevuelveListaVacia() throws Exception {
        repositorio.guardarSheet(new SheetGuardado(null, "Inventario"));

        mockMvc.perform(get(RUTA_PESTANAS))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"sheetGuardado\":false,\"pestanas\":[]}", true));
    }

    @Test
    void getPestanasConIdOLinkInvalidoDa400LinkInvalido() throws Exception {
        mockMvc.perform(get(RUTA_PESTANAS).param("idOLink", "https://example.com/planilla"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errores.idOLink").value("Debe ser una URL de Google Sheets o solo el ID."))
                .andExpect(jsonPath("$.campo").value("link-sheet"))
                .andExpect(jsonPath("$.codigo").value("LINK_INVALIDO"));
    }

    @Test
    void getPestanasDeUnSheetNoCompartidoDa403() throws Exception {
        esSinAcceso(mockMvc.perform(get(RUTA_PESTANAS).param("idOLink", SHEET_NO_COMPARTIDO)), RUTA_PESTANAS);
    }

    // ---- PUT /sheet/pestanas/renombrar (HU-006 CA4 a CA6, CA10, D1, D4) ----

    @Test
    void renombrarUnaPestanaDevuelveLosNombresYLaListaActualizada() throws Exception {
        repositorio.guardarSheet(new SheetGuardado(SHEET_VARIAS, "Ventas"));

        renombrar("Hoja 1", "Stock 2026")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreAnterior").value("Hoja 1"))
                .andExpect(jsonPath("$.nombreNuevo").value("Stock 2026"))
                .andExpect(jsonPath("$.eraPestanaInventario").value(false))
                .andExpect(jsonPath("$.nombrePestanaInventario").value("Ventas"))
                .andExpect(jsonPath("$.pestanas[0].nombre").value("Stock 2026"))
                .andExpect(jsonPath("$.pestanas[1].nombre").value("Ventas"))
                .andExpect(jsonPath("$.pestanas[1].enUso").value(true));
        assertThat(sheets.listarPestanas(SHEET_VARIAS)).containsExactly("Stock 2026", "Ventas");
    }

    @Test
    void renombrarLaPestanaDelInventarioGuardaLaConfiguracion() throws Exception {
        renombrar("Inventario", "Stock 2026")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eraPestanaInventario").value(true))
                .andExpect(jsonPath("$.nombrePestanaInventario").value("Stock 2026"))
                .andExpect(jsonPath("$.pestanas[0].enUso").value(true));
        assertThat(repositorio.leerSheet()).isEqualTo(new SheetGuardado(SHEET_INVENTARIO, "Stock 2026"));
    }

    @Test
    void renombrarConDatosInvalidosDa400ConCampoYCodigo() throws Exception {
        sheets.crearPestana(SHEET_INVENTARIO, "Caja");
        sheets.crearPestana(SHEET_INVENTARIO, "Hoja 1");

        esErrorDeRenombrar(renombrar(null, "Stock 2026"), "nombreActual", "pestana-a-renombrar", "PESTANA_VACIA",
                "La pestaña a renombrar es obligatoria.");
        esErrorDeRenombrar(renombrar("Caja", "Ventas 2026"), "nombreActual", "pestana-a-renombrar", "PESTANA_FIJA",
                "Esa pestaña la usa la app y no se puede renombrar.");
        esErrorDeRenombrar(renombrar("Hoja 1", " "), "nombreNuevo", "nombre-nuevo", "NOMBRE_VACIO",
                "El nombre nuevo es obligatorio.");
        esErrorDeRenombrar(renombrar("Hoja 1", "x".repeat(101)), "nombreNuevo", "nombre-nuevo", "NOMBRE_LARGO",
                "El nombre no debe superar los 100 caracteres.");
        esErrorDeRenombrar(renombrar("Hoja 1", "Hoja 1"), "nombreNuevo", "nombre-nuevo", "NOMBRE_IGUAL",
                "El nombre nuevo es igual al actual.");
        esErrorDeRenombrar(renombrar("Hoja 1", "INVENTARIO"), "nombreNuevo", "nombre-nuevo", "NOMBRE_REPETIDO",
                "Ya existe una pestaña con ese nombre.");
        assertThat(sheets.listarPestanas(SHEET_INVENTARIO)).containsExactly("Inventario", "Caja", "Hoja 1");
    }

    @Test
    void renombrarUnaPestanaQueYaNoExisteDa404ConCampoNull() throws Exception {
        renombrar("Borrada", "Stock 2026")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Pestaña inexistente"))
                .andExpect(jsonPath("$.detail").value("La pestaña ya no existe en el Sheet. Se actualizó la lista."))
                .andExpect(jsonPath("$.instance").value(RUTA_RENOMBRAR))
                .andExpect(jsonPath("$.codigo").value("PESTANA_INEXISTENTE"))
                .andExpect(content().string(containsString("\"campo\":null")));
    }

    @Test
    void renombrarSinSheetGuardadoDa400SinSheetGuardado() throws Exception {
        repositorio.guardarSheet(new SheetGuardado(null, "Inventario"));

        renombrar("Hoja 1", "Stock 2026")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Sin Sheet guardado"))
                .andExpect(jsonPath("$.detail").value("Disponible cuando haya un Sheet guardado."))
                .andExpect(jsonPath("$.codigo").value("SIN_SHEET_GUARDADO"));
    }

    @Test
    void renombrarEnUnSheetNoCompartidoDa403() throws Exception {
        repositorio.guardarSheet(new SheetGuardado(SHEET_NO_COMPARTIDO, "Inventario"));

        esSinAcceso(renombrar("Inventario", "Stock 2026"), RUTA_RENOMBRAR);
    }

    @Test
    void siFallaGuardarLaConfiguracionDeshaceElRenombradoYDa500() throws Exception {
        repositorio.fallarAlGuardarSheet.set(true);

        renombrar("Inventario", "Stock 2026")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Error interno"))
                .andExpect(jsonPath("$.detail").value("No se pudo guardar por un error inesperado. Se puede reintentar en un momento."));
        assertThat(sheets.listarPestanas(SHEET_INVENTARIO)).containsExactly("Inventario");
        assertThat(repositorio.leerSheet().pestanaInventario()).isEqualTo("Inventario");
    }

    // ---- HU-003 CA11 y CA12 ----

    @Test
    void putPreciosGuardaYDevuelveLosValoresNuevos() throws Exception {
        putJson("/api/v1/configuracion/precios", PRECIOS_VALIDOS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cotizacion").value(1350.0))
                .andExpect(jsonPath("$.redondeo").value(500))
                .andExpect(jsonPath("$.bulk.common").value(50))
                .andExpect(jsonPath("$.bulk.uncommon").value(100))
                .andExpect(jsonPath("$.bulk.rare").value(150))
                .andExpect(jsonPath("$.bulk.mythic").value(150))
                .andExpect(jsonPath("$.bulk.tierraBasica").value(200))
                .andExpect(jsonPath("$.carpetaCache").value(nullValue()))
                .andExpect(jsonPath("$.aviso").value(nullValue()));
        assertThat(repositorio.leerPrecios().cotizacion()).isEqualTo(1350);
    }

    @Test
    void putPreciosConCotizacionVaciaOMenorACeroDa400YNoGuarda() throws Exception {
        esErrorDeCampo(putJson("/api/v1/configuracion/precios", PRECIOS_VALIDOS.replace("\"cotizacion\":1350", "\"cotizacion\":null")),
                "cotizacion", "La cotización del dólar es obligatoria.");
        esErrorDeCampo(putJson("/api/v1/configuracion/precios", PRECIOS_VALIDOS.replace("\"cotizacion\":1350", "\"cotizacion\":0")),
                "cotizacion", "La cotización debe ser mayor a 0.");
        assertThat(repositorio.leerPrecios().cotizacion()).isEqualTo(1200);
    }

    @Test
    void putPreciosConRedondeoInvalidoDa400() throws Exception {
        esErrorDeCampo(putJson("/api/v1/configuracion/precios", PRECIOS_VALIDOS.replace("\"redondeo\":500,", "")),
                "redondeo", "El múltiplo para redondear es obligatorio.");
        esErrorDeCampo(putJson("/api/v1/configuracion/precios", PRECIOS_VALIDOS.replace("\"redondeo\":500", "\"redondeo\":-5")),
                "redondeo", "El múltiplo debe ser mayor a 0.");
    }

    @Test
    void putPreciosConBulkInvalidoDa400ConElCampoDeLaRareza() throws Exception {
        esErrorDeCampo(putJson("/api/v1/configuracion/precios", PRECIOS_VALIDOS.replace("\"rare\":150", "\"rare\":null")),
                "bulk.rare", "El precio es obligatorio.");
        esErrorDeCampo(putJson("/api/v1/configuracion/precios", PRECIOS_VALIDOS.replace("\"mythic\":150", "\"mythic\":0")),
                "bulk.mythic", "Debe ser mayor a 0.");
        esErrorDeCampo(putJson("/api/v1/configuracion/precios", PRECIOS_VALIDOS.replace("\"common\":50", "\"common\":50.5")),
                "bulk.common", "El precio debe ser un número entero, sin centavos.");
        assertThat(repositorio.leerPrecios().cotizacion()).isEqualTo(1200);
    }

    @Test
    void putPreciosConCarpetaQueNoExisteDa400() throws Exception {
        esErrorDeCampo(putJson("/api/v1/configuracion/precios",
                        PRECIOS_VALIDOS.replace("\"carpetaCache\":null", "\"carpetaCache\":\"Z:/no/existe/esta/carpeta\"")),
                "carpetaCache", "La carpeta no existe. Se puede indicar otra o dejar el campo vacío.");
        assertThat(repositorio.leerPrecios().cotizacion()).isEqualTo(1200);
    }

    @Test
    void putPreciosConFallaDeCardKingdomGuardaYAvisa() throws Exception {
        dobles.cardKingdomFalla.set(true);

        putJson("/api/v1/configuracion/precios", PRECIOS_VALIDOS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cotizacion").value(1350.0))
                .andExpect(jsonPath("$.aviso").value("Guardado, pero no se pudo sincronizar con Card Kingdom. Se puede reintentar más tarde."));
        assertThat(repositorio.leerPrecios().cotizacion()).isEqualTo(1350);
    }

    @Test
    void putPuntosGuardaElValor() throws Exception {
        putJson("/api/v1/configuracion/puntos", "{\"pesosPorPunto\":500}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pesosPorPunto").value(500.0));
        assertThat(repositorio.leerPesosPorPunto()).isEqualTo(500);
    }

    @Test
    void putPuntosVacioOMenorACeroDa400YNoGuarda() throws Exception {
        esErrorDeCampo(putJson("/api/v1/configuracion/puntos", "{}"), "pesosPorPunto", "Los pesos por punto son obligatorios.");
        esErrorDeCampo(putJson("/api/v1/configuracion/puntos", "{\"pesosPorPunto\":0}"), "pesosPorPunto", "Debe ser mayor a 0.");
        assertThat(repositorio.leerPesosPorPunto()).isEqualTo(1000);
    }

    // ---- Ayudas ----

    private ResultActions putJson(String ruta, String cuerpo) throws Exception {
        return mockMvc.perform(put(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions putSheet(String sheet, String pestana, String traerCartasDe) throws Exception {
        String traer = traerCartasDe == null ? "null" : "\"" + traerCartasDe + "\"";
        return putJson("/api/v1/configuracion/sheet",
                "{\"sheet\":\"" + sheet + "\",\"pestanaInventario\":\"" + pestana + "\",\"traerCartasDe\":" + traer + "}");
    }

    private ResultActions renombrar(String nombreActual, String nombreNuevo) throws Exception {
        String actual = nombreActual == null ? "null" : "\"" + nombreActual + "\"";
        return putJson(RUTA_RENOMBRAR, "{\"nombreActual\":" + actual + ",\"nombreNuevo\":\"" + nombreNuevo + "\"}");
    }

    private ResultActions subirLogo(byte[] contenido, String tipo) throws Exception {
        return mockMvc.perform(multipart("/api/v1/configuracion/tienda/logo")
                .file(new MockMultipartFile("archivo", "logo.png", tipo, contenido)));
    }

    private static void esErrorDeCampo(ResultActions respuesta, String campo, String mensaje) throws Exception {
        respuesta.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.detail").value("Hay datos para corregir."))
                .andExpect(jsonPath("$.errores['" + campo + "']").value(mensaje));
    }

    private static void esErrorDeRenombrar(ResultActions respuesta, String claveJson, String campo, String codigo,
                                           String mensaje) throws Exception {
        esErrorDeCampo(respuesta, claveJson, mensaje);
        respuesta.andExpect(jsonPath("$.campo").value(campo))
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.instance").value(RUTA_RENOMBRAR));
    }

    private static void esSinAcceso(ResultActions respuesta, String ruta) throws Exception {
        respuesta.andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Sin acceso al Sheet"))
                .andExpect(jsonPath("$.detail").value("La app no tiene acceso a este Sheet. Compartirlo con "
                        + "tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com como editor."))
                .andExpect(jsonPath("$.cuentaServicio").value(AccesoSheet.CUENTA_SERVICIO))
                .andExpect(jsonPath("$.instance").value(ruta));
    }
}
