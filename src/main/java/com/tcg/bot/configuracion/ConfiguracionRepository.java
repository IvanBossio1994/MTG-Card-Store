package com.tcg.bot.configuracion;

import java.util.Optional;

/**
 * Dónde se guarda la configuración de la tienda. Implementaciones: {@link ConfiguracionArchivosRepository}
 * (los mismos archivos que la v1) y {@link ConfiguracionEnMemoriaRepository} (tests y modo prueba).
 * Cada método guarda un solo bloque y no toca los demás.
 */
public interface ConfiguracionRepository {

    /** Nombre que se muestra si la tienda todavía no cargó el suyo (el de la v1). */
    String NOMBRE_TIENDA_POR_DEFECTO = "Inventory Manager";

    /** Pestaña del inventario si nunca se eligió otra. */
    String PESTANA_INVENTARIO_POR_DEFECTO = "Inventario";

    /**
     * Devuelve el nombre de la tienda. Ejemplo: tienda sin configurar → {@code "Inventory Manager"}.
     */
    String leerNombreTienda();

    /**
     * Guarda el nombre de la tienda. Ejemplo: {@code guardarNombreTienda("Codex Live Store")}.
     */
    void guardarNombreTienda(String nombre);

    /**
     * Devuelve el Sheet y la pestaña guardados. Ejemplo: sin Sheet → {@code SheetGuardado(null, "Inventario")}.
     */
    SheetGuardado leerSheet();

    /**
     * Guarda el Sheet y la pestaña del inventario. Ejemplo: {@code guardarSheet(new SheetGuardado("1Abc…", "Inventario"))}.
     */
    void guardarSheet(SheetGuardado sheet);

    /**
     * Devuelve la regla de precio. Ejemplo: sin configurar → cotización 1650, redondeo 500, bulk 50/100/150/150/200.
     */
    ReglaDePrecios leerPrecios();

    /**
     * Guarda la regla de precio (cotización, redondeo, bulk y carpeta de cache). Los puntos no cambian.
     * Ejemplo: {@code guardarPrecios(new ReglaDePrecios(1350, 500, bulk, null))}.
     */
    void guardarPrecios(ReglaDePrecios precios);

    /**
     * Devuelve cuántos pesos vale un punto. Ejemplo: sin configurar → {@code 1.0}.
     */
    double leerPesosPorPunto();

    /**
     * Guarda cuántos pesos vale un punto. Los precios no cambian. Ejemplo: {@code guardarPesosPorPunto(500)}.
     */
    void guardarPesosPorPunto(double pesosPorPunto);

    /**
     * Devuelve el logo guardado, si hay. Ejemplo: tienda sin logo → {@code Optional.empty()}.
     */
    Optional<LogoTienda> leerLogo();

    /**
     * Guarda el logo (reemplaza al anterior) y devuelve su versión nueva.
     * Ejemplo: {@code guardarLogo(bytesPng, FormatoLogo.PNG)} → {@code 1760550000000}.
     */
    long guardarLogo(byte[] contenido, FormatoLogo formato);

    /**
     * Borra el logo. Si no había, no hace nada. Ejemplo: {@code quitarLogo()} → {@code leerLogo()} vacío.
     */
    void quitarLogo();

    /**
     * Sheet de la tienda. {@code sheetId} es {@code null} si todavía no se conectó ninguno.
     * Ejemplo: {@code new SheetGuardado("1vSk7Y…", "Inventario")}.
     */
    record SheetGuardado(String sheetId, String pestanaInventario) {
    }

    /**
     * Regla de precio. {@code carpetaCache} es {@code null} si se usa la de siempre ({@code <app.storage-dir>/cache}).
     * Ejemplo: {@code new ReglaDePrecios(1350, 500, new PreciosBulk(50, 100, 150, 150, 200), null)}.
     */
    record ReglaDePrecios(double cotizacion, int redondeo, PreciosBulk bulk, String carpetaCache) {
    }

    /**
     * Precio bulk por rareza, en pesos. Ejemplo: {@code new PreciosBulk(50, 100, 150, 150, 200)}.
     */
    record PreciosBulk(double common, double uncommon, double rare, double mythic, double tierraBasica) {
    }

    /**
     * Logo guardado: contenido, formato y versión (cambia con cada logo nuevo).
     * Ejemplo: {@code new LogoTienda(bytes, FormatoLogo.PNG, 1760550000000L)}.
     */
    record LogoTienda(byte[] contenido, FormatoLogo formato, long version) {
    }

    /** Formatos de logo aceptados, con su extensión de archivo y su tipo de contenido. */
    enum FormatoLogo {
        PNG("png", "image/png"),
        JPG("jpg", "image/jpeg"),
        WEBP("webp", "image/webp");

        private final String extension;
        private final String tipoContenido;

        FormatoLogo(String extension, String tipoContenido) {
            this.extension = extension;
            this.tipoContenido = tipoContenido;
        }

        /** Extensión del archivo. Ejemplo: {@code PNG.extension()} → {@code "png"}. */
        public String extension() {
            return extension;
        }

        /** Tipo de contenido HTTP. Ejemplo: {@code JPG.tipoContenido()} → {@code "image/jpeg"}. */
        public String tipoContenido() {
            return tipoContenido;
        }

        /**
         * Busca el formato por extensión. Ejemplo: {@code deArchivo("store-logo.webp")} → {@code Optional.of(WEBP)}.
         */
        public static Optional<FormatoLogo> deArchivo(String nombreArchivo) {
            for (FormatoLogo formato : values()) {
                if (nombreArchivo.endsWith("." + formato.extension)) {
                    return Optional.of(formato);
                }
            }
            return Optional.empty();
        }
    }
}
