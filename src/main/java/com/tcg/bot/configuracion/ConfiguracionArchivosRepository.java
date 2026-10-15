package com.tcg.bot.configuracion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Configuración guardada en los mismos archivos y claves que la v1, dentro de {@code app.storage-dir}:
 * {@code store.properties} (tienda, Sheet, carpeta de cache, logo), {@code pricing.properties} (precios y puntos)
 * y {@code store-logo.<ext>}. Lee el archivo en cada consulta (no guarda copia en memoria) y, al guardar, conserva
 * todas las claves que no son del bloque. Cada escritura va a un archivo temporal que después reemplaza al original.
 */
@Repository
@Profile("!e2e")
public class ConfiguracionArchivosRepository implements ConfiguracionRepository {

    private static final String CLAVE_NOMBRE = "store.name";
    private static final String CLAVE_ORIGEN = "store.inventory-source";
    private static final String CLAVE_SHEET = "store.spreadsheet-id";
    private static final String CLAVE_PESTANA = "store.inventory-sheet-name";
    private static final String CLAVE_CARPETA_CACHE = "store.cache-directory";
    private static final String CLAVE_LOGO = "store.logo-filename";
    private static final String CLAVE_COTIZACION = "pricing.ck-dollar-rate";
    private static final String CLAVE_REDONDEO = "pricing.round-multiple";
    private static final String CLAVE_PESOS_POR_PUNTO = "points.pesos-per-point";
    private static final String CLAVE_BULK_COMMON = "points.bulk-common-price";
    private static final String CLAVE_BULK_UNCOMMON = "points.bulk-uncommon-price";
    private static final String CLAVE_BULK_RARE = "points.bulk-rare-price";
    private static final String CLAVE_BULK_MYTHIC = "points.bulk-mythic-price";
    private static final String CLAVE_BULK_TIERRA_BASICA = "points.bulk-basic-land-price";
    private static final PreciosBulk BULK_POR_DEFECTO = new PreciosBulk(50, 100, 150, 150, 200);

    private final Path carpetaDatos;
    private final Path archivoTienda;
    private final Path archivoPrecios;
    private final String sheetPorDefecto;
    private final double cotizacionPorDefecto;
    private final int redondeoPorDefecto;
    private final double pesosPorPuntoPorDefecto;
    private final ReentrantLock escritura = new ReentrantLock();

    /**
     * Crea el repositorio sobre la carpeta de datos, con los valores por defecto de {@code application.properties}.
     * Ejemplo: {@code new ConfiguracionArchivosRepository("./data", "", 1650, 500, 1)} → usa ./data/store.properties.
     */
    public ConfiguracionArchivosRepository(
            @Value("${app.storage-dir:./data}") String carpetaDatos,
            @Value("${store.default-spreadsheet-id:}") String sheetPorDefecto,
            @Value("${pricing.ck-dollar-rate:1650.0}") double cotizacionPorDefecto,
            @Value("${pricing.round-multiple:500}") int redondeoPorDefecto,
            @Value("${points.pesos-per-point:1.0}") double pesosPorPuntoPorDefecto) {
        this.carpetaDatos = Paths.get(carpetaDatos);
        this.archivoTienda = this.carpetaDatos.resolve("store.properties");
        this.archivoPrecios = this.carpetaDatos.resolve("pricing.properties");
        this.sheetPorDefecto = sheetPorDefecto;
        this.cotizacionPorDefecto = cotizacionPorDefecto;
        this.redondeoPorDefecto = redondeoPorDefecto;
        this.pesosPorPuntoPorDefecto = pesosPorPuntoPorDefecto;
    }

    @Override
    public String leerNombreTienda() {
        return leer(archivoTienda).getProperty(CLAVE_NOMBRE, NOMBRE_TIENDA_POR_DEFECTO);
    }

    @Override
    public void guardarNombreTienda(String nombre) {
        modificar(archivoTienda, propiedades -> propiedades.setProperty(CLAVE_NOMBRE, nombre));
    }

    @Override
    public SheetGuardado leerSheet() {
        Properties propiedades = leer(archivoTienda);
        String sheetId = propiedades.getProperty(CLAVE_SHEET, sheetPorDefecto);
        return new SheetGuardado(sheetId == null || sheetId.isBlank() ? null : sheetId,
                propiedades.getProperty(CLAVE_PESTANA, PESTANA_INVENTARIO_POR_DEFECTO));
    }

    @Override
    public void guardarSheet(SheetGuardado sheet) {
        modificar(archivoTienda, propiedades -> {
            propiedades.setProperty(CLAVE_ORIGEN, "GOOGLE_SHEET");
            propiedades.setProperty(CLAVE_SHEET, sheet.sheetId());
            propiedades.setProperty(CLAVE_PESTANA, sheet.pestanaInventario());
        });
    }

    @Override
    public ReglaDePrecios leerPrecios() {
        Properties precios = leer(archivoPrecios);
        var bulk = new PreciosBulk(
                positivo(precios.getProperty(CLAVE_BULK_COMMON), BULK_POR_DEFECTO.common()),
                positivo(precios.getProperty(CLAVE_BULK_UNCOMMON), BULK_POR_DEFECTO.uncommon()),
                positivo(precios.getProperty(CLAVE_BULK_RARE), BULK_POR_DEFECTO.rare()),
                positivo(precios.getProperty(CLAVE_BULK_MYTHIC), BULK_POR_DEFECTO.mythic()),
                positivo(precios.getProperty(CLAVE_BULK_TIERRA_BASICA), BULK_POR_DEFECTO.tierraBasica()));
        return new ReglaDePrecios(
                positivo(precios.getProperty(CLAVE_COTIZACION), cotizacionPorDefecto),
                (int) positivo(precios.getProperty(CLAVE_REDONDEO), redondeoPorDefecto),
                bulk,
                carpetaCachePropia(leer(archivoTienda).getProperty(CLAVE_CARPETA_CACHE)));
    }

    @Override
    public void guardarPrecios(ReglaDePrecios precios) {
        modificar(archivoPrecios, propiedades -> {
            propiedades.setProperty(CLAVE_COTIZACION, Double.toString(precios.cotizacion()));
            propiedades.setProperty(CLAVE_REDONDEO, Integer.toString(precios.redondeo()));
            propiedades.setProperty(CLAVE_BULK_COMMON, Double.toString(precios.bulk().common()));
            propiedades.setProperty(CLAVE_BULK_UNCOMMON, Double.toString(precios.bulk().uncommon()));
            propiedades.setProperty(CLAVE_BULK_RARE, Double.toString(precios.bulk().rare()));
            propiedades.setProperty(CLAVE_BULK_MYTHIC, Double.toString(precios.bulk().mythic()));
            propiedades.setProperty(CLAVE_BULK_TIERRA_BASICA, Double.toString(precios.bulk().tierraBasica()));
            // La v1 espera siempre esta clave: si no estaba, se deja el valor por defecto.
            propiedades.putIfAbsent(CLAVE_PESOS_POR_PUNTO, Double.toString(pesosPorPuntoPorDefecto));
        });
        String carpeta = precios.carpetaCache() == null ? carpetaCachePorDefecto().toString() : precios.carpetaCache();
        modificar(archivoTienda, propiedades -> propiedades.setProperty(CLAVE_CARPETA_CACHE, carpeta));
    }

    @Override
    public double leerPesosPorPunto() {
        return positivo(leer(archivoPrecios).getProperty(CLAVE_PESOS_POR_PUNTO), pesosPorPuntoPorDefecto);
    }

    @Override
    public void guardarPesosPorPunto(double pesosPorPunto) {
        modificar(archivoPrecios, propiedades -> propiedades.setProperty(CLAVE_PESOS_POR_PUNTO, Double.toString(pesosPorPunto)));
    }

    @Override
    public Optional<LogoTienda> leerLogo() {
        String nombreArchivo = leer(archivoTienda).getProperty(CLAVE_LOGO, "");
        Optional<FormatoLogo> formato = FormatoLogo.deArchivo(nombreArchivo);
        Path archivo = carpetaDatos.resolve(nombreArchivo);
        if (nombreArchivo.isBlank() || formato.isEmpty() || !Files.isRegularFile(archivo)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new LogoTienda(Files.readAllBytes(archivo), formato.get(),
                    Files.getLastModifiedTime(archivo).toMillis()));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el logo de la tienda.", e);
        }
    }

    @Override
    public long guardarLogo(byte[] contenido, FormatoLogo formato) {
        escritura.lock();
        try {
            String nombreNuevo = "store-logo." + formato.extension();
            Path destino = carpetaDatos.resolve(nombreNuevo);
            Files.createDirectories(carpetaDatos);
            Path temporal = Files.createTempFile(carpetaDatos, "store-logo", ".tmp");
            Files.write(temporal, contenido);
            reemplazar(temporal, destino);
            String nombreAnterior = leer(archivoTienda).getProperty(CLAVE_LOGO, "");
            if (!nombreAnterior.isBlank() && !nombreAnterior.equals(nombreNuevo)) {
                Files.deleteIfExists(carpetaDatos.resolve(nombreAnterior));
            }
            modificar(archivoTienda, propiedades -> propiedades.setProperty(CLAVE_LOGO, nombreNuevo));
            return Files.getLastModifiedTime(destino).toMillis();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el logo de la tienda.", e);
        } finally {
            escritura.unlock();
        }
    }

    @Override
    public void quitarLogo() {
        escritura.lock();
        try {
            String nombreAnterior = leer(archivoTienda).getProperty(CLAVE_LOGO, "");
            if (!nombreAnterior.isBlank()) {
                Files.deleteIfExists(carpetaDatos.resolve(nombreAnterior));
            }
            modificar(archivoTienda, propiedades -> propiedades.setProperty(CLAVE_LOGO, ""));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo quitar el logo de la tienda.", e);
        } finally {
            escritura.unlock();
        }
    }

    /** La carpeta de cache de la v1 por defecto es {@code <app.storage-dir>/cache}; si es esa, se informa {@code null}. */
    private String carpetaCachePropia(String guardada) {
        if (guardada == null || guardada.isBlank()) {
            return null;
        }
        try {
            Path normalizada = Paths.get(guardada).toAbsolutePath().normalize();
            return normalizada.equals(carpetaCachePorDefecto().toAbsolutePath().normalize()) ? null : guardada;
        } catch (InvalidPathException e) {
            return guardada;
        }
    }

    private Path carpetaCachePorDefecto() {
        return carpetaDatos.resolve("cache");
    }

    private Properties leer(Path archivo) {
        Properties propiedades = new Properties();
        if (!Files.exists(archivo)) {
            return propiedades;
        }
        try (InputStream entrada = Files.newInputStream(archivo)) {
            propiedades.load(entrada);
            return propiedades;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + archivo.getFileName(), e);
        }
    }

    private void modificar(Path archivo, Consumer<Properties> cambio) {
        escritura.lock();
        try {
            Properties propiedades = leer(archivo);
            cambio.accept(propiedades);
            Files.createDirectories(carpetaDatos);
            Path temporal = Files.createTempFile(carpetaDatos, archivo.getFileName().toString(), ".tmp");
            try (OutputStream salida = Files.newOutputStream(temporal)) {
                propiedades.store(salida, "Configuracion local de la tienda");
            }
            reemplazar(temporal, archivo);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar " + archivo.getFileName(), e);
        } finally {
            escritura.unlock();
        }
    }

    private static void reemplazar(Path temporal, Path destino) throws IOException {
        try {
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static double positivo(String valor, double porDefecto) {
        if (valor == null) {
            return porDefecto;
        }
        try {
            double numero = Double.parseDouble(valor.trim());
            return numero > 0 ? numero : porDefecto;
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }
}
