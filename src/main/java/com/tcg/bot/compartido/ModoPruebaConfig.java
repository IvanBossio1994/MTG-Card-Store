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

    /**
     * Registra el filtro que quita los beans de v1 y Google.
     * Ejemplo: con {@code --spring.profiles.active=e2e} no existe el bean {@code dashboardController}.
     */
    @Bean
    public static BeanDefinitionRegistryPostProcessor excluirBeansSinGoogle() {
        return new ExcluirPaquetes(PAQUETES_EXCLUIDOS);
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
