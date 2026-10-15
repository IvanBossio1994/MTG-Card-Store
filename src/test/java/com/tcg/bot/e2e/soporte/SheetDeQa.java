package com.tcg.bot.e2e.soporte;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.DeleteSheetRequest;
import com.google.api.services.sheets.v4.model.ExtendedValue;
import com.google.api.services.sheets.v4.model.GridCoordinate;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.UpdateCellsRequest;
import com.google.api.services.sheets.v4.model.UpdateSheetPropertiesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Acceso directo al Sheet de QA con la cuenta de servicio, para preparar y validar datos desde los pasos y para dejarlo
 * como estaba (CA2 de HU-008). Al arrancar la corrida saca una "foto" (pestañas, orden y valores) y, al terminar cada
 * escenario, pase o falle, vuelve a esa foto: borra las pestañas nuevas, devuelve los nombres y el orden, y reescribe
 * solo las pestañas cuyos valores cambiaron. Los formatos de celda no se tocan.
 * <p>
 * En los pasos se recibe por constructor (Cucumber lo inyecta). Ejemplo:
 * {@code assertThat(sheetDeQa.pestanas()).contains("Inventario")}.
 */
public class SheetDeQa {

    /** ID del Sheet de QA (docs/agentes/estado.md, Google, punto 7). Nunca el del local ni el de pruebas de la dueña. */
    public static final String ID = "1wWH5bJV3kqXHyLK4zorxnGcGsZ_bRHrKXmn4DGgrG2I";

    /** Link completo del Sheet de QA, como lo pega el usuario en Configuración. */
    public static final String LINK = "https://docs.google.com/spreadsheets/d/" + ID + "/edit";

    /** Clave de la cuenta de servicio (la misma que usa la app; nunca se abre a mano ni se muestra). */
    static final Path ARCHIVO_CLAVE = Paths.get(
            System.getProperty("google.cuenta-servicio.archivo", "config/google-service-account.json"));

    private static final String RENDER_CON_FORMULAS = "FORMULA";
    private static final String RENDER_SIN_FORMATO = "UNFORMATTED_VALUE";

    private static Sheets cliente;
    private static Foto estadoInicial;

    /**
     * Conecta con Google y saca la foto del Sheet de QA, una vez por corrida. Si falta la clave, frena con un mensaje claro.
     * Ejemplo: Sheet de QA con "Inventario" y "Caja" → la foto guarda esas dos pestañas con sus valores.
     */
    static synchronized void guardarEstadoInicial() {
        if (estadoInicial == null) {
            estadoInicial = tomarFoto();
        }
    }

    /**
     * Deja el Sheet de QA como en la foto inicial. Si nada cambió, solo lee (no escribe).
     * Ejemplo: el escenario renombró "Hoja 1" a "Inventario" y creó "Caja" → vuelve "Hoja 1" y se borra "Caja".
     */
    static synchronized void volverAlEstadoInicial() {
        if (estadoInicial == null) {
            return;
        }
        boolean recreoPestanas = restaurar(estadoInicial);
        if (recreoPestanas) {
            // Las pestañas recreadas tienen un ID nuevo: la foto se actualiza para la próxima comparación.
            estadoInicial = tomarFoto();
        }
    }

    /**
     * Nombres de las pestañas del Sheet de QA, en orden.
     * Ejemplo: {@code pestanas()} → {@code ["Inventario", "Movimientos", "Caja", "Reservas", "Clientes"]}.
     */
    public List<String> pestanas() {
        return conGoogle(() -> propiedades().stream().map(SheetProperties::getTitle).toList());
    }

    /**
     * Valores de una pestaña tal como los lee la app (números como número, fórmulas resueltas).
     * Ejemplo: {@code valores("Inventario")} → {@code [["Cantidad", "Nombre", …], [4, "Sol Ring", …]]}; pestaña vacía → {@code []}.
     */
    public List<List<Object>> valores(String pestana) {
        return conGoogle(() -> copiar(cliente().spreadsheets().values().get(ID, rango(pestana))
                .setValueRenderOption(RENDER_SIN_FORMATO).execute().getValues()));
    }

    /**
     * Encabezados (fila 1) de una pestaña, como texto.
     * Ejemplo: {@code encabezados("Clientes")} → {@code ["Nombre", "Apellido", …]}; pestaña vacía → {@code []}.
     */
    public List<String> encabezados(String pestana) {
        List<List<Object>> filas = valores(pestana);
        return filas.isEmpty() ? List.of() : filas.get(0).stream().map(String::valueOf).toList();
    }

    /**
     * Prepara el Sheet de QA para un escenario: lo deja exactamente con estas pestañas, en este orden y con estos
     * valores (desde A1), en una sola escritura. Al terminar el escenario vuelve solo a la foto inicial.
     * Ejemplo: {@code dejarConPestanas(Map.of("Hoja 1", List.of()))} → un Sheet "nuevo", con una sola pestaña vacía.
     */
    public void dejarConPestanas(Map<String, List<List<Object>>> pestanas) {
        if (pestanas.isEmpty()) {
            throw new IllegalArgumentException("El Sheet tiene que quedar con al menos una pestaña.");
        }
        conGoogle(() -> {
            List<SheetProperties> actuales = propiedades();
            Set<Integer> idsUsados = new HashSet<>();
            actuales.forEach(propiedad -> idsUsados.add(propiedad.getSheetId()));
            List<Request> pedidos = new ArrayList<>();
            List<Integer> idsNuevos = new ArrayList<>();
            int posicion = 0;
            for (var pestana : pestanas.entrySet()) {
                int idNuevo = idLibre(idsUsados);
                idsNuevos.add(idNuevo);
                pedidos.add(crearPestana(idNuevo, "qa-preparando-" + posicion++));
                pedidos.addAll(escribirValores(idNuevo, pestana.getValue()));
            }
            actuales.forEach(propiedad -> pedidos.add(borrarPestana(propiedad.getSheetId())));
            posicion = 0;
            for (String titulo : pestanas.keySet()) {
                pedidos.add(cambiarNombreYOrden(idsNuevos.get(posicion), titulo, posicion));
                posicion++;
            }
            ejecutar(pedidos);
            return null;
        });
    }

    /** Foto del Sheet: pestañas en orden, con su ID, nombre y valores (fórmulas como fórmula). */
    record Foto(List<PestanaGuardada> pestanas) {
    }

    /** Una pestaña de la foto. */
    record PestanaGuardada(int id, String titulo, List<List<Object>> valores) {
    }

    private static Foto tomarFoto() {
        return conGoogle(() -> {
            List<SheetProperties> propiedades = propiedades();
            List<List<List<Object>>> valores = valoresDe(propiedades.stream().map(SheetProperties::getTitle).toList());
            List<PestanaGuardada> pestanas = new ArrayList<>();
            for (int i = 0; i < propiedades.size(); i++) {
                pestanas.add(new PestanaGuardada(propiedades.get(i).getSheetId(), propiedades.get(i).getTitle(), valores.get(i)));
            }
            return new Foto(pestanas);
        });
    }

    /** Arma y manda en un solo batchUpdate todo lo necesario para volver a la foto. Devuelve si recreó pestañas borradas. */
    private static boolean restaurar(Foto foto) {
        return conGoogle(() -> {
            List<SheetProperties> actuales = propiedades();
            Map<Integer, String> tituloActualPorId = new HashMap<>();
            Set<Integer> idsUsados = new HashSet<>();
            List<Integer> ordenActual = new ArrayList<>();
            for (SheetProperties propiedad : actuales) {
                tituloActualPorId.put(propiedad.getSheetId(), propiedad.getTitle());
                idsUsados.add(propiedad.getSheetId());
                ordenActual.add(propiedad.getSheetId());
            }
            Set<Integer> idsDeLaFoto = new HashSet<>();
            foto.pestanas().forEach(pestana -> idsDeLaFoto.add(pestana.id()));

            List<Request> estructura = new ArrayList<>();
            Map<Integer, Integer> idFinalPorIdDeFoto = new HashMap<>();
            Set<Integer> recreadas = new HashSet<>();
            // 1. Recrear las pestañas de la foto que ya no están (con un ID nuevo y un nombre provisorio).
            for (PestanaGuardada pestana : foto.pestanas()) {
                if (tituloActualPorId.containsKey(pestana.id())) {
                    idFinalPorIdDeFoto.put(pestana.id(), pestana.id());
                } else {
                    int idNuevo = idLibre(idsUsados);
                    idFinalPorIdDeFoto.put(pestana.id(), idNuevo);
                    recreadas.add(idNuevo);
                    estructura.add(crearPestana(idNuevo, "qa-restaurando-" + idNuevo));
                }
            }
            // 2. Borrar las pestañas que creó el escenario.
            for (Integer id : ordenActual) {
                if (!idsDeLaFoto.contains(id)) {
                    estructura.add(borrarPestana(id));
                }
            }
            // 3. Nombres y orden: primero un nombre provisorio a las renombradas (para que no choquen), después el original.
            List<Integer> ordenEsperado = foto.pestanas().stream().map(pestana -> idFinalPorIdDeFoto.get(pestana.id())).toList();
            boolean nombresUOrdenCambiaron = !recreadas.isEmpty()
                    || !ordenActual.stream().filter(idsDeLaFoto::contains).toList().equals(ordenEsperado);
            for (PestanaGuardada pestana : foto.pestanas()) {
                String actual = tituloActualPorId.get(pestana.id());
                if (actual != null && !actual.equals(pestana.titulo())) {
                    estructura.add(cambiarNombre(pestana.id(), "qa-restaurando-" + pestana.id()));
                    nombresUOrdenCambiaron = true;
                }
            }
            if (nombresUOrdenCambiaron) {
                for (int i = 0; i < foto.pestanas().size(); i++) {
                    estructura.add(cambiarNombreYOrden(ordenEsperado.get(i), foto.pestanas().get(i).titulo(), i));
                }
            }

            // 4. Valores: se reescriben solo las pestañas que cambiaron (o las recreadas).
            List<PestanaGuardada> existentes = foto.pestanas().stream()
                    .filter(pestana -> tituloActualPorId.containsKey(pestana.id())).toList();
            List<List<List<Object>>> valoresActuales = valoresDe(existentes.stream()
                    .map(pestana -> tituloActualPorId.get(pestana.id())).toList());
            List<Request> valores = new ArrayList<>();
            for (PestanaGuardada pestana : foto.pestanas()) {
                int indice = existentes.indexOf(pestana);
                if (indice < 0 || !valoresActuales.get(indice).equals(pestana.valores())) {
                    int id = idFinalPorIdDeFoto.get(pestana.id());
                    valores.add(vaciarPestana(id));
                    valores.addAll(escribirValores(id, pestana.valores()));
                }
            }

            List<Request> pedidos = new ArrayList<>(estructura);
            pedidos.addAll(valores);
            ejecutar(pedidos);
            return !recreadas.isEmpty();
        });
    }

    private static void ejecutar(List<Request> pedidos) throws IOException, GeneralSecurityException {
        if (!pedidos.isEmpty()) {
            // Un solo batchUpdate: Google lo aplica todo o nada.
            cliente().spreadsheets().batchUpdate(ID, new BatchUpdateSpreadsheetRequest().setRequests(pedidos)).execute();
        }
    }

    private static List<List<List<Object>>> valoresDe(List<String> titulos) throws IOException, GeneralSecurityException {
        List<List<List<Object>>> resultado = new ArrayList<>();
        if (titulos.isEmpty()) {
            return resultado;
        }
        List<ValueRange> rangos = cliente().spreadsheets().values().batchGet(ID)
                .setRanges(titulos.stream().map(SheetDeQa::rango).toList())
                .setValueRenderOption(RENDER_CON_FORMULAS)
                .execute().getValueRanges();
        for (int i = 0; i < titulos.size(); i++) {
            ValueRange rango = rangos != null && i < rangos.size() ? rangos.get(i) : null;
            resultado.add(copiar(rango == null ? null : rango.getValues()));
        }
        return resultado;
    }

    private static List<SheetProperties> propiedades() throws IOException, GeneralSecurityException {
        var sheet = cliente().spreadsheets().get(ID).setFields("sheets.properties(sheetId,title,index)").execute();
        List<SheetProperties> propiedades = new ArrayList<>();
        if (sheet.getSheets() != null) {
            for (Sheet pestana : sheet.getSheets()) {
                propiedades.add(pestana.getProperties());
            }
        }
        return propiedades;
    }

    private static Request crearPestana(int id, String titulo) {
        return new Request().setAddSheet(new AddSheetRequest().setProperties(new SheetProperties().setSheetId(id).setTitle(titulo)));
    }

    private static Request borrarPestana(int id) {
        return new Request().setDeleteSheet(new DeleteSheetRequest().setSheetId(id));
    }

    private static Request cambiarNombre(int id, String titulo) {
        return new Request().setUpdateSheetProperties(new UpdateSheetPropertiesRequest()
                .setProperties(new SheetProperties().setSheetId(id).setTitle(titulo)).setFields("title"));
    }

    private static Request cambiarNombreYOrden(int id, String titulo, int indice) {
        return new Request().setUpdateSheetProperties(new UpdateSheetPropertiesRequest()
                .setProperties(new SheetProperties().setSheetId(id).setTitle(titulo).setIndex(indice)).setFields("title,index"));
    }

    /** Sin filas y con el campo userEnteredValue: Google borra los valores de toda la pestaña (no los formatos). */
    private static Request vaciarPestana(int id) {
        return new Request().setUpdateCells(new UpdateCellsRequest()
                .setRange(new GridRange().setSheetId(id)).setFields("userEnteredValue"));
    }

    private static List<Request> escribirValores(int id, List<List<Object>> filas) {
        if (filas.isEmpty()) {
            return List.of();
        }
        return List.of(new Request().setUpdateCells(new UpdateCellsRequest()
                .setStart(new GridCoordinate().setSheetId(id).setRowIndex(0).setColumnIndex(0))
                .setRows(filas.stream().map(SheetDeQa::filaDeCeldas).toList())
                .setFields("userEnteredValue")));
    }

    /** Cada valor vuelve con su tipo: fórmula ("=…"), número, verdadero/falso o texto (sin convertir "001" en 1). */
    private static RowData filaDeCeldas(List<Object> fila) {
        List<CellData> celdas = new ArrayList<>();
        for (Object valor : fila) {
            ExtendedValue contenido = switch (valor) {
                case Number numero -> new ExtendedValue().setNumberValue(numero.doubleValue());
                case Boolean logico -> new ExtendedValue().setBoolValue(logico);
                case String texto when texto.startsWith("=") -> new ExtendedValue().setFormulaValue(texto);
                case String texto when texto.isEmpty() -> null;
                case null -> null;
                default -> new ExtendedValue().setStringValue(String.valueOf(valor));
            };
            celdas.add(contenido == null ? new CellData() : new CellData().setUserEnteredValue(contenido));
        }
        return new RowData().setValues(celdas);
    }

    private static int idLibre(Set<Integer> idsUsados) {
        int id;
        do {
            id = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        } while (!idsUsados.add(id));
        return id;
    }

    private static List<List<Object>> copiar(List<List<Object>> valores) {
        List<List<Object>> filas = new ArrayList<>();
        if (valores != null) {
            valores.forEach(fila -> filas.add(new ArrayList<>(fila)));
        }
        return filas;
    }

    private static String rango(String pestana) {
        return "'" + pestana.replace("'", "''") + "'";
    }

    private static synchronized Sheets cliente() throws IOException, GeneralSecurityException {
        if (cliente == null) {
            if (!Files.isRegularFile(ARCHIVO_CLAVE)) {
                throw new IllegalStateException("Falta la clave de la cuenta de servicio en " + ARCHIVO_CLAVE.toAbsolutePath()
                        + ". Los E2E la necesitan para usar el Sheet de QA (docs/agentes/qa-guia.md).");
            }
            try (InputStream clave = Files.newInputStream(ARCHIVO_CLAVE)) {
                GoogleCredentials credenciales = GoogleCredentials.fromStream(clave).createScoped(SheetsScopes.SPREADSHEETS);
                cliente = new Sheets.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(),
                        new HttpCredentialsAdapter(credenciales)).setApplicationName("TCG Inventory E2E").build();
            }
        }
        return cliente;
    }

    /**
     * Google limita las lecturas a 60 por minuto por proyecto (la app y los E2E comparten el cupo). Ante un 429 espera y
     * reintenta (hasta 5 veces): cada llamada vuelve a mirar el Sheet, así que repetirla es seguro.
     * Ejemplo: 429 en la restauración → espera 20 s y vuelve a intentar.
     */
    private static <T> T conGoogle(LlamadaGoogle<T> llamada) {
        try {
            for (int intento = 1; ; intento++) {
                try {
                    return llamada.ejecutar();
                } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
                    if (e.getStatusCode() != 429 || intento >= 5) {
                        throw e;
                    }
                    Thread.sleep(20_000L);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la espera por el límite de lecturas de Google.", e);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer o escribir el Sheet de QA: " + e.getMessage(), e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo crear la conexión segura con Google.", e);
        }
    }

    @FunctionalInterface
    private interface LlamadaGoogle<T> {
        T ejecutar() throws IOException, GeneralSecurityException;
    }
}
