package com.tcg.bot.compartido;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Perfil {@code e2e}: la app arranca sin Google ni internet.
 * Saca del contexto los beans de la v1 ({@code com.tcg.bot.legacy}: descarga de Card Kingdom al arrancar y
 * sincronización programada) y de {@code com.tcg.bot.google} (OAuth). No importa ninguna clase de legacy:
 * filtra por nombre de paquete.
 */
@Configuration(proxyBeanMethods = false)
@Profile("e2e")
public class ModoPruebaConfig {

    static final List<String> PAQUETES_EXCLUIDOS = List.of("com.tcg.bot.legacy.", "com.tcg.bot.google.");
    static final String GMAIL_DE_PRUEBA = "prueba@tcg-inventory.local";
    static final String SHEET_CON_INVENTARIO = "1PruebaSheetConInventario000000000000000000";
    static final String SHEET_CON_STOCK_VIEJO = "1PruebaSheetConStockViejo00000000000000000";

    /**
     * Registra el filtro que quita los beans de v1 y Google.
     * Ejemplo: con {@code --spring.profiles.active=e2e} no existe el bean {@code dashboardController}.
     */
    @Bean
    public static BeanDefinitionRegistryPostProcessor excluirBeansSinGoogle() {
        return new ExcluirPaquetes(PAQUETES_EXCLUIDOS);
    }

    /**
     * Sheets simulados del modo prueba (docs/v2/api/configuracion.md, "Modo prueba"). Cualquier otro ID responde 403.
     * Ejemplo: {@code listarPestanas("1PruebaSheetConPestanaUnica0000000000000000")} → {@code ["Hoja 1"]}.
     */
    @Bean
    public AccesoSheet accesoSheetDePrueba() {
        List<Object> encabezadosInventario = List.of("Cantidad", "Nombre", "Codigo de set", "Nombre del set",
                "Numero de carta", "Condicion", "Printing (foil/no foil)", "Idioma", "Precio Local", "Precio CK USD", "Accion");
        return new AccesoSheetEnMemoria()
                .compartir(SHEET_CON_INVENTARIO, "Inventario")
                .cargarFilas(SHEET_CON_INVENTARIO, "Inventario", List.of(encabezadosInventario))
                .compartir("1PruebaSheetConPestanaUnica0000000000000000", "Hoja 1")
                .compartir("1PruebaSheetConVariasPestanas00000000000000", "Hoja 1", "Ventas")
                .compartir(SHEET_CON_STOCK_VIEJO, "Inventario", "stock viejo")
                .cargarFilas(SHEET_CON_STOCK_VIEJO, "Inventario", List.of(encabezadosInventario))
                .cargarFilas(SHEET_CON_STOCK_VIEJO, "stock viejo", List.of(
                        List.of("Carta", "Stock", "Set", "Precio", "Notas"),
                        List.of("Sol Ring", 4, "Commander Masters", 1500, "firmada"),
                        List.of("", "", "", "", ""),
                        List.of("Opt", 2, "Dominaria", 300, ""),
                        List.of("Counterspell", 1, "Mystery Booster", 800, "")));
    }

    /**
     * Sesión de Google simulada: siempre vigente, con un Gmail de prueba.
     * Ejemplo: {@code gmailConectado()} → {@code Optional.of("prueba@tcg-inventory.local")}.
     */
    @Bean
    public SesionGoogle sesionGoogleDePrueba() {
        return () -> Optional.of(GMAIL_DE_PRUEBA);
    }

    /** Quita las definiciones de beans cuya clase está en alguno de los paquetes indicados. */
    static class ExcluirPaquetes implements BeanDefinitionRegistryPostProcessor, Ordered {

        private static final Logger log = LoggerFactory.getLogger(ExcluirPaquetes.class);
        private final List<String> paquetes;

        ExcluirPaquetes(List<String> paquetes) {
            this.paquetes = paquetes;
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            List<String> quitados = new ArrayList<>();
            for (String nombre : registry.getBeanDefinitionNames()) {
                BeanDefinition definicion = registry.getBeanDefinition(nombre);
                String clase = definicion.getBeanClassName();
                if (clase != null && paquetes.stream().anyMatch(clase::startsWith)) {
                    registry.removeBeanDefinition(nombre);
                    quitados.add(nombre);
                }
            }
            log.info("Perfil e2e: sin v1 ni Google. Beans excluidos: {}", quitados);
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            // Nada: todo se resuelve al quitar las definiciones.
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }
}
