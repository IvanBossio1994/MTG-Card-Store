package com.tcg.bot.google;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.UpdateSheetPropertiesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.tcg.bot.compartido.AccesoSheet;
import com.tcg.bot.compartido.ErrorDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Acceso real al Google Sheet con la cuenta de servicio ({@link AccesoSheet#CUENTA_SERVICIO}).
 * La clave se toma de {@code config/google-service-account.json} (propiedad {@code google.cuenta-servicio.archivo})
 * recién en el primer uso; nunca se muestra ni se loguea. Las escrituras van con lock para que no se pisen.
 */
@Component
@Profile("!e2e")
public class AccesoSheetCuentaServicio implements AccesoSheet {

    private static final Logger log = LoggerFactory.getLogger(AccesoSheetCuentaServicio.class);
    private static final String NOMBRE_APP = "TCG Inventory";

    private final Path archivoClave;
    private final ReentrantLock escritura = new ReentrantLock();
    private volatile Sheets cliente;

    /**
     * Crea el acceso apuntando al archivo de la clave (no lo abre todavía).
     * Ejemplo: {@code new AccesoSheetCuentaServicio("./config/google-service-account.json")}.
     */
    public AccesoSheetCuentaServicio(
            @Value("${google.cuenta-servicio.archivo:./config/google-service-account.json}") String archivoClave) {
        this.archivoClave = Paths.get(archivoClave);
    }

    @Override
    public List<String> listarPestanas(String sheetId) {
        return conGoogle(() -> {
            var sheet = cliente().spreadsheets().get(sheetId).setFields("sheets.properties(sheetId,title)").execute();
            List<String> nombres = new ArrayList<>();
            if (sheet.getSheets() != null) {
                for (Sheet pestana : sheet.getSheets()) {
                    nombres.add(pestana.getProperties().getTitle());
                }
            }
            return nombres;
        });
    }

    @Override
    public void renombrarPestana(String sheetId, String nombreActual, String nombreNuevo) {
        conLock(() -> {
            Integer idPestana = idDePestana(sheetId, nombreActual);
            var pedido = new Request().setUpdateSheetProperties(new UpdateSheetPropertiesRequest()
                    .setProperties(new SheetProperties().setSheetId(idPestana).setTitle(nombreNuevo))
                    .setFields("title"));
            cliente().spreadsheets().batchUpdate(sheetId, new BatchUpdateSpreadsheetRequest().setRequests(List.of(pedido)))
                    .execute();
            return null;
        });
    }

    @Override
    public void crearPestana(String sheetId, String pestana) {
        conLock(() -> {
            var pedido = new Request().setAddSheet(new AddSheetRequest().setProperties(new SheetProperties().setTitle(pestana)));
            cliente().spreadsheets().batchUpdate(sheetId, new BatchUpdateSpreadsheetRequest().setRequests(List.of(pedido)))
                    .execute();
            return null;
        });
    }

    @Override
    public List<String> leerEncabezados(String sheetId, String pestana) {
        return conGoogle(() -> {
            var valores = cliente().spreadsheets().values().get(sheetId, rango(pestana, "1:1")).execute().getValues();
            if (valores == null || valores.isEmpty()) {
                return List.of();
            }
            return valores.get(0).stream().map(String::valueOf).toList();
        });
    }

    @Override
    public void escribirEncabezados(String sheetId, String pestana, List<String> encabezados) {
        conLock(() -> {
            var cuerpo = new ValueRange().setValues(List.of(new ArrayList<Object>(encabezados)));
            cliente().spreadsheets().values().update(sheetId, rango(pestana, "A1"), cuerpo)
                    .setValueInputOption("RAW").execute();
            return null;
        });
    }

    private Integer idDePestana(String sheetId, String nombre) throws IOException, GeneralSecurityException {
        var sheet = cliente().spreadsheets().get(sheetId).setFields("sheets.properties(sheetId,title)").execute();
        return sheet.getSheets().stream()
                .filter(pestana -> nombre.equals(pestana.getProperties().getTitle()))
                .map(pestana -> pestana.getProperties().getSheetId())
                .findFirst()
                .orElseThrow(() -> new ErrorDeNegocioException(HttpStatus.CONFLICT, "Pestaña no encontrada",
                        "La pestaña «" + nombre + "» ya no está en el Sheet. Probá de nuevo."));
    }

    /** Rango con el nombre de la pestaña entre comillas simples (las comillas internas se duplican). */
    private static String rango(String pestana, String celdas) {
        return "'" + pestana.replace("'", "''") + "'!" + celdas;
    }

    private Sheets cliente() throws IOException, GeneralSecurityException {
        Sheets actual = cliente;
        if (actual == null) {
            synchronized (this) {
                if (cliente == null) {
                    cliente = crearCliente();
                }
                actual = cliente;
            }
        }
        return actual;
    }

    private Sheets crearCliente() throws IOException, GeneralSecurityException {
        if (!Files.isRegularFile(archivoClave)) {
            log.error("Falta la clave de la cuenta de servicio en {}", archivoClave.toAbsolutePath());
            throw new ErrorDeNegocioException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                    "No pudimos guardar por un error inesperado. Probá de nuevo en un momento.");
        }
        try (InputStream clave = Files.newInputStream(archivoClave)) {
            GoogleCredentials credenciales = GoogleCredentials.fromStream(clave).createScoped(SheetsScopes.SPREADSHEETS);
            return new Sheets.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credenciales))
                    .setApplicationName(NOMBRE_APP)
                    .build();
        }
    }

    private <T> T conLock(LlamadaGoogle<T> llamada) {
        escritura.lock();
        try {
            return conGoogle(llamada);
        } finally {
            escritura.unlock();
        }
    }

    /** Traduce los errores de Google a errores de negocio en español (403 sin acceso, 503 sin conexión). */
    private <T> T conGoogle(LlamadaGoogle<T> llamada) {
        try {
            return llamada.ejecutar();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 403 || e.getStatusCode() == 404) {
                throw AccesoSheet.sinAcceso();
            }
            log.warn("Google Sheets respondió {}: {}", e.getStatusCode(),
                    e.getDetails() == null ? "" : e.getDetails().getMessage());
            throw new ErrorDeNegocioException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                    "No pudimos guardar por un error inesperado. Probá de nuevo en un momento.");
        } catch (IOException e) {
            log.warn("No se pudo conectar con Google Sheets.", e);
            throw AccesoSheet.sinConexion();
        } catch (GeneralSecurityException e) {
            log.error("No se pudo crear la conexión segura con Google.", e);
            throw new ErrorDeNegocioException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                    "No pudimos guardar por un error inesperado. Probá de nuevo en un momento.");
        }
    }

    @FunctionalInterface
    private interface LlamadaGoogle<T> {
        T ejecutar() throws IOException, GeneralSecurityException;
    }
}
