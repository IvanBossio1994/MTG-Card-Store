package com.tcg.bot.configuracion;

import com.tcg.bot.configuracion.ConfiguracionRepository.FormatoLogo;
import com.tcg.bot.configuracion.ConfiguracionRepository.PreciosBulk;
import com.tcg.bot.configuracion.ConfiguracionRepository.ReglaDePrecios;
import com.tcg.bot.configuracion.ConfiguracionRepository.SheetGuardado;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Mismo almacenamiento que la v1 (HU-003): mismos archivos y claves, y cada bloque sin pisar los demás. */
class ConfiguracionArchivosRepositoryTest {

    @TempDir
    Path carpeta;

    private ConfiguracionArchivosRepository repositorio() {
        return new ConfiguracionArchivosRepository(carpeta.toString(), "", 1650, 500, 1);
    }

    @Test
    void sinArchivosDevuelveLosValoresPorDefectoDeLaV1() {
        var repositorio = repositorio();

        assertThat(repositorio.leerNombreTienda()).isEqualTo("Inventory Manager");
        assertThat(repositorio.leerSheet()).isEqualTo(new SheetGuardado(null, "Inventario"));
        assertThat(repositorio.leerPrecios()).isEqualTo(new ReglaDePrecios(1650, 500, new PreciosBulk(50, 100, 150, 150, 200), null));
        assertThat(repositorio.leerPesosPorPunto()).isEqualTo(1);
        assertThat(repositorio.leerLogo()).isEmpty();
    }

    @Test
    void leeLoQueGuardoLaV1YConservaSusOtrasClaves() throws IOException {
        Properties tiendaV1 = new Properties();
        tiendaV1.setProperty("store.name", "Codex Live");
        tiendaV1.setProperty("store.spreadsheet-id", "1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ");
        tiendaV1.setProperty("store.inventory-sheet-name", "Stock");
        tiendaV1.setProperty("tutorial.completed", "true");
        escribir("store.properties", tiendaV1);
        Properties preciosV1 = new Properties();
        preciosV1.setProperty("pricing.ck-dollar-rate", "1200.0");
        preciosV1.setProperty("points.pesos-per-point", "1000.0");
        escribir("pricing.properties", preciosV1);
        var repositorio = repositorio();

        repositorio.guardarNombreTienda("Codex Live Store");
        repositorio.guardarPesosPorPunto(500);

        Properties tienda = leer("store.properties");
        assertThat(tienda.getProperty("store.name")).isEqualTo("Codex Live Store");
        assertThat(tienda.getProperty("store.inventory-sheet-name")).isEqualTo("Stock");
        assertThat(tienda.getProperty("tutorial.completed")).isEqualTo("true");
        Properties precios = leer("pricing.properties");
        assertThat(precios.getProperty("points.pesos-per-point")).isEqualTo("500.0");
        assertThat(precios.getProperty("pricing.ck-dollar-rate")).isEqualTo("1200.0");
        assertThat(repositorio.leerSheet().sheetId()).isEqualTo("1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ");
    }

    @Test
    void guardaSheetPreciosYCarpetaConLasClavesDeLaV1() throws IOException {
        var repositorio = repositorio();
        Path cache = Files.createDirectory(carpeta.resolve("mi-cache"));

        repositorio.guardarSheet(new SheetGuardado("1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ", "Inventario"));
        repositorio.guardarPrecios(new ReglaDePrecios(1350, 1000, new PreciosBulk(60, 110, 160, 170, 210), cache.toString()));

        Properties tienda = leer("store.properties");
        assertThat(tienda.getProperty("store.spreadsheet-id")).isEqualTo("1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ");
        assertThat(tienda.getProperty("store.inventory-sheet-name")).isEqualTo("Inventario");
        assertThat(tienda.getProperty("store.cache-directory")).isEqualTo(cache.toString());
        Properties precios = leer("pricing.properties");
        assertThat(precios.getProperty("pricing.ck-dollar-rate")).isEqualTo("1350.0");
        assertThat(precios.getProperty("pricing.round-multiple")).isEqualTo("1000");
        assertThat(precios.getProperty("points.bulk-common-price")).isEqualTo("60.0");
        assertThat(precios.getProperty("points.bulk-basic-land-price")).isEqualTo("210.0");
        assertThat(repositorio.leerPrecios().carpetaCache()).isEqualTo(cache.toString());
    }

    @Test
    void sinCarpetaDeCacheGuardaLaDeSiempreYLaInformaComoNull() {
        var repositorio = repositorio();

        repositorio.guardarPrecios(new ReglaDePrecios(1350, 500, new PreciosBulk(50, 100, 150, 150, 200), null));

        assertThat(leer("store.properties").getProperty("store.cache-directory")).isEqualTo(carpeta.resolve("cache").toString());
        assertThat(repositorio.leerPrecios().carpetaCache()).isNull();
    }

    @Test
    void guardaYQuitaElLogoComoLaV1() {
        var repositorio = repositorio();

        repositorio.guardarLogo(new byte[]{1, 2, 3}, FormatoLogo.PNG);
        repositorio.guardarLogo(new byte[]{4, 5, 6}, FormatoLogo.WEBP);

        assertThat(leer("store.properties").getProperty("store.logo-filename")).isEqualTo("store-logo.webp");
        assertThat(carpeta.resolve("store-logo.png")).doesNotExist();
        assertThat(repositorio.leerLogo()).get().satisfies(logo -> {
            assertThat(logo.contenido()).containsExactly(4, 5, 6);
            assertThat(logo.formato()).isEqualTo(FormatoLogo.WEBP);
        });

        repositorio.quitarLogo();

        assertThat(repositorio.leerLogo()).isEmpty();
        assertThat(carpeta.resolve("store-logo.webp")).doesNotExist();
    }

    private void escribir(String archivo, Properties propiedades) throws IOException {
        try (OutputStream salida = Files.newOutputStream(carpeta.resolve(archivo))) {
            propiedades.store(salida, null);
        }
    }

    private Properties leer(String archivo) {
        Properties propiedades = new Properties();
        try (InputStream entrada = Files.newInputStream(carpeta.resolve(archivo))) {
            propiedades.load(entrada);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return propiedades;
    }
}
