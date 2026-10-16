package com.tcg.bot.google;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.ExtendedValue;
import com.google.api.services.sheets.v4.model.GridCoordinate;
import com.google.api.services.sheets.v4.model.GridProperties;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.UpdateCellsRequest;
import com.google.api.services.sheets.v4.model.UpdateSheetPropertiesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.tcg.bot.compartido.AccesoSheet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

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
    /** Filas y columnas con las que Google crea una pestaña nueva. */
    private static final int FILAS_POR_DEFECTO = 1000;

    private static final int COLUMNAS_POR_DEFECTO = 26;

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
        return conGoogle(() -> propiedadesDePestanas(sheetId).stream()
                .map(SheetProperties::getTitle)
                .toList());
    }

    @Override
    public void renombrarPestana(String sheetId, String nombreActual, String nombreNuevo) {
        conLock(() -> {
            Integer idPestana = idDePestana(sheetId, nombreActual);
            var pedido = new Request()
                    .setUpdateSheetProperties(new UpdateSheetPropertiesRequest()
                            .setProperties(
                                    new SheetProperties().setSheetId(idPestana).setTitle(nombreNuevo))
                            .setFields("title"));
            cliente()
                    .spreadsheets()
                    .batchUpdate(sheetId, new BatchUpdateSpreadsheetRequest().setRequests(List.of(pedido)))
                    .execute();
            return null;
        });
    }

    @Override
    public void crearPestana(String sheetId, String pestana) {
        conLock(() -> {
            var pedido = new Request()
                    .setAddSheet(new AddSheetRequest().setProperties(new SheetProperties().setTitle(pestana)));
            cliente()
                    .spreadsheets()
                    .batchUpdate(sheetId, new BatchUpdateSpreadsheetRequest().setRequests(List.of(pedido)))
                    .execute();
            return null;
        });
    }

    @Override
    public List<String> leerEncabezados(String sheetId, String pestana) {
        return conGoogle(() -> {
            var valores = cliente()
                    .spreadsheets()
                    .values()
                    .get(sheetId, rango(pestana, "1:1"))
                    .execute()
                    .getValues();
            return primeraFila(valores);
        });
    }

    @Override
    public void escribirEncabezados(String sheetId, String pestana, List<String> encabezados) {
        conLock(() -> {
            var cuerpo = new ValueRange().setValues(List.of(new ArrayList<Object>(encabezados)));
            cliente()
                    .spreadsheets()
                    .values()
                    .update(sheetId, rango(pestana, "A1"), cuerpo)
                    .setValueInputOption("RAW")
                    .execute();
            return null;
        });
    }

    @Override
    public LinkedHashMap<String, List<String>> leerEncabezadosDeTodas(String sheetId) {
        return conGoogle(() -> {
            List<String> pestanas = propiedadesDePestanas(sheetId).stream()
                    .map(SheetProperties::getTitle)
                    .toList();
            LinkedHashMap<String, List<String>> encabezados = new LinkedHashMap<>();
            if (pestanas.isEmpty()) {
                return encabezados;
            }
            List<String> rangos =
                    pestanas.stream().map(pestana -> rango(pestana, "1:1")).toList();
            List<ValueRange> filas = cliente()
                    .spreadsheets()
                    .values()
                    .batchGet(sheetId)
                    .setRanges(rangos)
                    .execute()
                    .getValueRanges();
            for (int i = 0; i < pestanas.size(); i++) {
                ValueRange fila = filas != null && i < filas.size() ? filas.get(i) : null;
                encabezados.put(pestanas.get(i), primeraFila(fila == null ? null : fila.getValues()));
            }
            return encabezados;
        });
    }

    @Override
    public List<List<Object>> leerValores(String sheetId, String pestana) {
        return conGoogle(() -> {
            // Sin formato: los números llegan como números; las fechas, como se ven.
            var valores = cliente()
                    .spreadsheets()
                    .values()
                    .get(sheetId, rango(pestana, null))
                    .setValueRenderOption("UNFORMATTED_VALUE")
                    .setDateTimeRenderOption("FORMATTED_STRING")
                    .execute()
                    .getValues();
            List<List<Object>> filas = new ArrayList<>();
            if (valores != null) {
                for (List<Object> fila : valores) {
                    filas.add(new ArrayList<>(fila));
                }
            }
            return filas;
        });
    }

    @Override
    public void crearPestanaConValores(String sheetId, String pestana, List<List<Object>> filas) {
        conLock(() -> {
            Set<Integer> idsUsados = new HashSet<>();
            propiedadesDePestanas(sheetId).forEach(propiedades -> idsUsados.add(propiedades.getSheetId()));
            int idNuevo;
            do {
                idNuevo = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
            } while (idsUsados.contains(idNuevo));

            int columnas = filas.stream().mapToInt(List::size).max().orElse(0);
            var crear = new Request()
                    .setAddSheet(new AddSheetRequest()
                            .setProperties(new SheetProperties()
                                    .setSheetId(idNuevo)
                                    .setTitle(pestana)
                                    .setGridProperties(new GridProperties()
                                            .setRowCount(Math.max(filas.size(), FILAS_POR_DEFECTO))
                                            .setColumnCount(Math.max(columnas, COLUMNAS_POR_DEFECTO)))));
            List<Request> pedidos = new ArrayList<>(List.of(crear));
            if (!filas.isEmpty()) {
                pedidos.add(new Request()
                        .setUpdateCells(new UpdateCellsRequest()
                                .setStart(new GridCoordinate()
                                        .setSheetId(idNuevo)
                                        .setRowIndex(0)
                                        .setColumnIndex(0))
                                .setRows(filas.stream()
                                        .map(AccesoSheetCuentaServicio::filaDeCeldas)
                                        .toList())
                                .setFields("userEnteredValue")));
            }
            // Crear y escribir van en el mismo batchUpdate: Google los aplica todo o nada.
            cliente()
                    .spreadsheets()
                    .batchUpdate(sheetId, new BatchUpdateSpreadsheetRequest().setRequests(pedidos))
                    .execute();
            return null;
        });
    }

    private static RowData filaDeCeldas(List<Object> fila) {
        List<CellData> celdas = new ArrayList<>();
        for (Object valor : fila) {
            ExtendedValue contenido = new ExtendedValue();
            if (valor instanceof Number numero) {
                contenido.setNumberValue(numero.doubleValue());
            } else if (valor instanceof Boolean logico) {
                contenido.setBoolValue(logico);
            } else {
                String texto = valor == null ? "" : String.valueOf(valor);
                if (texto.isEmpty()) {
                    celdas.add(new CellData());
                    continue;
                }
                contenido.setStringValue(texto);
            }
            celdas.add(new CellData().setUserEnteredValue(contenido));
        }
        return new RowData().setValues(celdas);
    }

    private static List<String> primeraFila(List<List<Object>> valores) {
        if (valores == null || valores.isEmpty()) {
            return List.of();
        }
        return valores.get(0).stream().map(String::valueOf).toList();
    }

    private List<SheetProperties> propiedadesDePestanas(String sheetId) throws IOException, GeneralSecurityException {
        var sheet = cliente()
                .spreadsheets()
                .get(sheetId)
                .setFields("sheets.properties(sheetId,title)")
                .execute();
        List<SheetProperties> propiedades = new ArrayList<>();
        if (sheet.getSheets() != null) {
            for (Sheet pestana : sheet.getSheets()) {
                propiedades.add(pestana.getProperties());
            }
        }
        return propiedades;
    }

    private Integer idDePestana(String sheetId, String nombre) throws IOException, GeneralSecurityException {
        return propiedadesDePestanas(sheetId).stream()
                .filter(propiedades -> nombre.equals(propiedades.getTitle()))
                .map(SheetProperties::getSheetId)
                .findFirst()
                .orElseThrow(AccesoSheet::pestanaInexistente);
    }

    /** Rango con el nombre de la pestaña entre comillas simples (las comillas internas se duplican); sin celdas = toda la pestaña. */
    private static String rango(String pestana, String celdas) {
        String nombre = "'" + pestana.replace("'", "''") + "'";
        return celdas == null ? nombre : nombre + "!" + celdas;
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
            throw AccesoSheet.errorInesperado();
        }
        try (InputStream clave = Files.newInputStream(archivoClave)) {
            GoogleCredentials credenciales =
                    GoogleCredentials.fromStream(clave).createScoped(SheetsScopes.SPREADSHEETS);
            return new Sheets.Builder(
                            GoogleNetHttpTransport.newTrustedTransport(),
                            GsonFactory.getDefaultInstance(),
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
            log.warn(
                    "Google Sheets respondió {}: {}",
                    e.getStatusCode(),
                    e.getDetails() == null ? "" : e.getDetails().getMessage());
            throw AccesoSheet.errorInesperado();
        } catch (IOException e) {
            log.warn("No se pudo conectar con Google Sheets.", e);
            throw AccesoSheet.sinConexion();
        } catch (GeneralSecurityException e) {
            log.error("No se pudo crear la conexión segura con Google.", e);
            throw AccesoSheet.errorInesperado();
        }
    }

    @FunctionalInterface
    private interface LlamadaGoogle<T> {
        T ejecutar() throws IOException, GeneralSecurityException;
    }
}
