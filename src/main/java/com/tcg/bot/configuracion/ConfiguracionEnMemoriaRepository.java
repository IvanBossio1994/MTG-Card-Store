package com.tcg.bot.configuracion;

import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * Configuración en memoria, para los tests y el modo prueba (perfil {@code e2e}). Se pierde al reiniciar.
 * Arranca con datos de ejemplo: tienda "Codex Live", el Sheet de prueba con pestaña "Inventario",
 * cotización 1200, redondeo 500, bulk 50/100/150/150/200, sin carpeta de cache, 1000 pesos por punto y sin logo.
 */
@Repository
@Profile("e2e")
public class ConfiguracionEnMemoriaRepository implements ConfiguracionRepository {

    /** Sheet de prueba con el que arranca (está "compartido" en el modo prueba). */
    public static final String SHEET_DE_EJEMPLO = "1PruebaSheetConInventario000000000000000000";

    private String nombreTienda = "Codex Live";
    private SheetGuardado sheet = new SheetGuardado(SHEET_DE_EJEMPLO, PESTANA_INVENTARIO_POR_DEFECTO);
    private ReglaDePrecios precios = new ReglaDePrecios(1200, 500, new PreciosBulk(50, 100, 150, 150, 200), null);
    private double pesosPorPunto = 1000;
    private LogoTienda logo;
    private long ultimaVersionLogo;

    @Override
    public synchronized String leerNombreTienda() {
        return nombreTienda;
    }

    @Override
    public synchronized void guardarNombreTienda(String nombre) {
        nombreTienda = nombre;
    }

    @Override
    public synchronized SheetGuardado leerSheet() {
        return sheet;
    }

    @Override
    public synchronized void guardarSheet(SheetGuardado sheet) {
        this.sheet = sheet;
    }

    @Override
    public synchronized ReglaDePrecios leerPrecios() {
        return precios;
    }

    @Override
    public synchronized void guardarPrecios(ReglaDePrecios precios) {
        this.precios = precios;
    }

    @Override
    public synchronized double leerPesosPorPunto() {
        return pesosPorPunto;
    }

    @Override
    public synchronized void guardarPesosPorPunto(double pesosPorPunto) {
        this.pesosPorPunto = pesosPorPunto;
    }

    @Override
    public synchronized Optional<LogoTienda> leerLogo() {
        return Optional.ofNullable(logo);
    }

    @Override
    public synchronized long guardarLogo(byte[] contenido, FormatoLogo formato) {
        // La versión siempre crece, aunque se guarden dos logos en el mismo milisegundo.
        ultimaVersionLogo = Math.max(System.currentTimeMillis(), ultimaVersionLogo + 1);
        logo = new LogoTienda(contenido.clone(), formato, ultimaVersionLogo);
        return ultimaVersionLogo;
    }

    @Override
    public synchronized void quitarLogo() {
        logo = null;
    }
}
