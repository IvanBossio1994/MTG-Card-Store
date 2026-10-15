package com.tcg.bot.configuracion;

import com.tcg.bot.compartido.AccesoSheet;
import com.tcg.bot.compartido.AccesoSheetEnMemoria;
import com.tcg.bot.compartido.SesionGoogle;
import com.tcg.bot.configuracion.ConfiguracionRepository.PreciosBulk;
import com.tcg.bot.configuracion.ConfiguracionRepository.ReglaDePrecios;
import com.tcg.bot.configuracion.ConfiguracionRepository.SheetGuardado;
import com.tcg.bot.configuracion.ConfiguracionService.SincronizacionCardKingdom;
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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API de Configuración (HU-003, CA1 a CA12): códigos HTTP y forma del JSON según docs/v2/api/configuracion.md. */
@WebMvcTest(ConfiguracionController.class)
@Import({ConfiguracionService.class, ConfiguracionControllerTest.Dobles.class})
class ConfiguracionControllerTest {

    private static final String SHEET_INVENTARIO = "1SheetConInventario00000000000000000000000";
    private static final String SHEET_UNICA = "1SheetConPestanaUnica000000000000000000000";
    private static final String SHEET_VARIAS = "1SheetConVariasPestanas0000000000000000000";
    private static final String SHEET_NO_COMPARTIDO = "1SheetNoCompartido000000000000000000000000";
    private static final String PRECIOS_VALIDOS = """
            {"cotizacion":1350,"redondeo":500,
             "bulk":{"common":50,"uncommon":100,"rare":150,"mythic":150,"tierraBasica":200},"carpetaCache":null}""";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ConfiguracionRepository repositorio;
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
        ConfiguracionRepository configuracionRepository() {
            return new ConfiguracionEnMemoriaRepository();
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
        repositorio.guardarNombreTienda("Codex Live");
        repositorio.quitarLogo();
        repositorio.guardarSheet(new SheetGuardado(SHEET_INVENTARIO, "Inventario"));
        repositorio.guardarPrecios(new ReglaDePrecios(1200, 500, new PreciosBulk(50, 100, 150, 150, 200), null));
        repositorio.guardarPesosPorPunto(1000);
        sheets.compartir(SHEET_INVENTARIO, "Inventario")
                .compartir(SHEET_UNICA, "Hoja 1")
                .compartir(SHEET_VARIAS, "Hoja 1", "Ventas");
    }

    // ---- CA1 ----

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
                .andExpect(jsonPath("$.precios.cotizacion").value(1200.0))
                .andExpect(jsonPath("$.precios.redondeo").value(500))
                .andExpect(jsonPath("$.precios.bulk.common").value(50))
                .andExpect(jsonPath("$.precios.bulk.tierraBasica").value(200))
                .andExpect(jsonPath("$.precios.carpetaCache").value(nullValue()))
                .andExpect(jsonPath("$.precios.aviso").value(nullValue()))
                .andExpect(jsonPath("$.puntos.pesosPorPunto").value(1000.0));
    }

    // ---- CA2 y CA3 ----

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
                "nombre", "Ingresá el nombre de la tienda.");
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

    // ---- CA4 ----

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
                "archivo", "El logo pesa más de 2 MB.");
        assertThat(repositorio.leerLogo()).isEmpty();
    }

    @Test
    void postLogoQueNoEsImagenDa400() throws Exception {
        esErrorDeCampo(subirLogo("no soy una imagen".getBytes(), "image/png"),
                "archivo", "El archivo no es PNG, JPG ni WEBP.");
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

    // ---- CA5 a CA7 ----

    @Test
    void putSheetConLaPestanaExistenteNoTraeAvisoYCreaLasQueFaltan() throws Exception {
        putSheet("https://docs.google.com/spreadsheets/d/" + SHEET_INVENTARIO + "/edit#gid=0", "Inventario")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sheetId").value(SHEET_INVENTARIO))
                .andExpect(jsonPath("$.pestanaInventario").value("Inventario"))
                .andExpect(jsonPath("$.gmail").value("tienda@gmail.com"))
                .andExpect(jsonPath("$.aviso").value(nullValue()));
        assertThat(sheets.listarPestanas(SHEET_INVENTARIO)).contains("Movimientos", "Caja", "Reservas", "Clientes");
    }

    @Test
    void putSheetConPestanaUnicaLaRenombraYAvisa() throws Exception {
        putSheet(SHEET_UNICA, "Inventario")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pestanaInventario").value("Inventario"))
                .andExpect(jsonPath("$.aviso").value("La pestaña «Hoja 1» se renombró a «Inventario»."));
        assertThat(sheets.listarPestanas(SHEET_UNICA)).first().isEqualTo("Inventario");
        assertThat(repositorio.leerSheet()).isEqualTo(new SheetGuardado(SHEET_UNICA, "Inventario"));
    }

    @Test
    void putSheetConVariasPestanasCreaLaPedidaYAvisa() throws Exception {
        putSheet(SHEET_VARIAS, "Inventario")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aviso").value("Se creó la pestaña «Inventario»."));
        assertThat(sheets.listarPestanas(SHEET_VARIAS)).startsWith("Hoja 1", "Ventas", "Inventario");
    }

    // ---- CA8 ----

    @Test
    void putSheetConLinkVacioDa400() throws Exception {
        esErrorDeCampo(putSheet("", "Inventario"), "sheet", "Pegá el link o el ID del Sheet.");
    }

    @Test
    void putSheetConLinkQueNoEsDeGoogleDa400YNoCambiaLoGuardado() throws Exception {
        esErrorDeCampo(putSheet("https://example.com/planilla", "Inventario"), "sheet", "Ese link no es de un Google Sheet.");
        assertThat(repositorio.leerSheet().sheetId()).isEqualTo(SHEET_INVENTARIO);
    }

    @Test
    void putSheetConPestanaVaciaDa400() throws Exception {
        esErrorDeCampo(putSheet(SHEET_UNICA, " "), "pestanaInventario", "Ingresá el nombre de la pestaña.");
        assertThat(sheets.listarPestanas(SHEET_UNICA)).containsExactly("Hoja 1");
    }

    // ---- CA9 ----

    @Test
    void putSheetNoCompartidoDa403ConLaCuentaDeServicio() throws Exception {
        putSheet(SHEET_NO_COMPARTIDO, "Inventario")
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Sin acceso al Sheet"))
                .andExpect(jsonPath("$.detail").value("La app no tiene acceso a este Sheet. Compartilo con "
                        + "tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com como editor."))
                .andExpect(jsonPath("$.cuentaServicio").value(AccesoSheet.CUENTA_SERVICIO))
                .andExpect(jsonPath("$.instance").value("/api/v1/configuracion/sheet"));
        assertThat(repositorio.leerSheet().sheetId()).isEqualTo(SHEET_INVENTARIO);
    }

    // ---- CA10 ----

    @Test
    void conLaSesionVencidaGetYPutSheetDan401ConVolverAIniciarSesion() throws Exception {
        dobles.sesionVencida.set(true);

        for (ResultActions respuesta : new ResultActions[]{
                mockMvc.perform(get("/api/v1/configuracion")), putSheet(SHEET_UNICA, "Inventario")}) {
            respuesta.andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Sesión de Google vencida"))
                    .andExpect(jsonPath("$.detail").value("Tu sesión de Google venció."))
                    .andExpect(jsonPath("$.accion").value("volver-a-iniciar-sesion"))
                    .andExpect(jsonPath("$.urlInicioSesion").value(SesionGoogle.URL_INICIO_SESION));
        }
        assertThat(sheets.listarPestanas(SHEET_UNICA)).containsExactly("Hoja 1");
    }

    @Test
    void conLaCarpetaDeLaSesionSinPermisoDa500ConElMotivoYNoDiceQueEsInternet() throws Exception {
        dobles.carpetaSesionSinPermiso.set(true);

        mockMvc.perform(get("/api/v1/configuracion"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Sesión de Google ilegible"))
                .andExpect(jsonPath("$.detail").value("La app no puede leer tu sesión de Google en esta PC. "
                        + "Abrila sin permisos de administrador y volvé a iniciar sesión."));
    }

    // ---- CA11 ----

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
                "cotizacion", "Ingresá la cotización del dólar.");
        esErrorDeCampo(putJson("/api/v1/configuracion/precios", PRECIOS_VALIDOS.replace("\"cotizacion\":1350", "\"cotizacion\":0")),
                "cotizacion", "La cotización tiene que ser mayor a 0.");
        assertThat(repositorio.leerPrecios().cotizacion()).isEqualTo(1200);
    }

    @Test
    void putPreciosConRedondeoInvalidoDa400() throws Exception {
        esErrorDeCampo(putJson("/api/v1/configuracion/precios", PRECIOS_VALIDOS.replace("\"redondeo\":500,", "")),
                "redondeo", "Ingresá el múltiplo para redondear.");
        esErrorDeCampo(putJson("/api/v1/configuracion/precios", PRECIOS_VALIDOS.replace("\"redondeo\":500", "\"redondeo\":-5")),
                "redondeo", "El múltiplo tiene que ser mayor a 0.");
    }

    @Test
    void putPreciosConBulkInvalidoDa400ConElCampoDeLaRareza() throws Exception {
        esErrorDeCampo(putJson("/api/v1/configuracion/precios", PRECIOS_VALIDOS.replace("\"rare\":150", "\"rare\":null")),
                "bulk.rare", "Ingresá el precio.");
        esErrorDeCampo(putJson("/api/v1/configuracion/precios", PRECIOS_VALIDOS.replace("\"mythic\":150", "\"mythic\":0")),
                "bulk.mythic", "Tiene que ser mayor a 0.");
        esErrorDeCampo(putJson("/api/v1/configuracion/precios", PRECIOS_VALIDOS.replace("\"common\":50", "\"common\":50.5")),
                "bulk.common", "Sin centavos.");
        assertThat(repositorio.leerPrecios().cotizacion()).isEqualTo(1200);
    }

    @Test
    void putPreciosConCarpetaQueNoExisteDa400() throws Exception {
        esErrorDeCampo(putJson("/api/v1/configuracion/precios",
                        PRECIOS_VALIDOS.replace("\"carpetaCache\":null", "\"carpetaCache\":\"Z:/no/existe/esta/carpeta\"")),
                "carpetaCache", "No encontramos esa carpeta.");
        assertThat(repositorio.leerPrecios().cotizacion()).isEqualTo(1200);
    }

    @Test
    void putPreciosConFallaDeCardKingdomGuardaYAvisa() throws Exception {
        dobles.cardKingdomFalla.set(true);

        putJson("/api/v1/configuracion/precios", PRECIOS_VALIDOS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cotizacion").value(1350.0))
                .andExpect(jsonPath("$.aviso").value("Guardado, pero no se pudo sincronizar con Card Kingdom. Probá más tarde."));
        assertThat(repositorio.leerPrecios().cotizacion()).isEqualTo(1350);
    }

    // ---- CA12 ----

    @Test
    void putPuntosGuardaElValor() throws Exception {
        putJson("/api/v1/configuracion/puntos", "{\"pesosPorPunto\":500}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pesosPorPunto").value(500.0));
        assertThat(repositorio.leerPesosPorPunto()).isEqualTo(500);
    }

    @Test
    void putPuntosVacioOMenorACeroDa400YNoGuarda() throws Exception {
        esErrorDeCampo(putJson("/api/v1/configuracion/puntos", "{}"), "pesosPorPunto", "Ingresá cuántos pesos vale un punto.");
        esErrorDeCampo(putJson("/api/v1/configuracion/puntos", "{\"pesosPorPunto\":0}"), "pesosPorPunto", "Tiene que ser mayor a 0.");
        assertThat(repositorio.leerPesosPorPunto()).isEqualTo(1000);
    }

    // ---- Ayudas ----

    private ResultActions putJson(String ruta, String cuerpo) throws Exception {
        return mockMvc.perform(put(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions putSheet(String sheet, String pestana) throws Exception {
        return putJson("/api/v1/configuracion/sheet",
                "{\"sheet\":\"" + sheet + "\",\"pestanaInventario\":\"" + pestana + "\"}");
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
}
