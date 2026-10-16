package com.tcg.bot;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Controller;

/**
 * Hace cumplir la estructura de la v2 ({@code docs/agentes/estructura-carpetas.md} y {@code estilo-codigo.md}, sección
 * 4). Revisa solo el código de producción de la v2: {@code com.tcg.bot.legacy} y los tests quedan afuera.
 *
 * <p>Si una clase existente rompe una regla y arreglarla pide un refactor, se excluye en la regla con el comentario
 * {@code // HU-012: <motivo>}. Hoy no hay excepciones.
 */
@AnalyzeClasses(
        packages = "com.tcg.bot",
        importOptions = {ImportOption.DoNotIncludeTests.class, ArquitecturaV2Test.SinLegacy.class})
class ArquitecturaV2Test {

    private static final String PAQUETE_RAIZ = "com.tcg.bot";
    private static final String PAQUETE_LEGACY = "com.tcg.bot.legacy..";

    /** La v2 no importa ni usa nada de la v1: la lee como referencia y la reescribe en su módulo. */
    @ArchTest
    static final ArchRule laV2NoUsaLegacy = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAPackage(PAQUETE_LEGACY)
            .as("La v2 no usa com.tcg.bot.legacy")
            .because("legacy es solo de referencia y se borra en el corte (estructura-carpetas.md)");

    /** Las dependencias entran por constructor, nunca con {@code @Autowired}, {@code @Value} o {@code @Inject} en un campo. */
    @ArchTest
    static final ArchRule sinInyeccionPorCampo = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION
            .as("La v2 no inyecta dependencias en campos (@Autowired, @Value, @Inject, @Resource)")
            .because("las dependencias van por constructor (estilo-codigo.md)");

    /** Nombres que dicen qué hace la clase: nada de cajones de sastre {@code Utils}, {@code Helper} o {@code Manager}. */
    @ArchTest
    static final ArchRule sinNombresGenericos = noClasses()
            .should()
            .haveSimpleNameEndingWith("Utils")
            .orShould()
            .haveSimpleNameEndingWith("Helper")
            .orShould()
            .haveSimpleNameEndingWith("Manager")
            .as("La v2 no tiene clases *Utils, *Helper ni *Manager")
            .because("los nombres son descriptivos y en español (estilo-codigo.md, sección 1)");

    /** Cada módulo expone su API en un único controller (por ejemplo, {@code configuracion} → {@code ConfiguracionController}). */
    @ArchTest
    static final ArchRule unControllerPorModulo = classes()
            .that()
            .areMetaAnnotatedWith(Controller.class)
            .should(serElUnicoControllerDeSuModulo())
            .allowEmptyShould(true)
            .as("Cada módulo de la v2 tiene un solo controller")
            .because("todo se divide por módulo, nunca un controller por endpoint (estructura-carpetas.md)");

    /**
     * Agrupa los controllers por módulo y marca los módulos que tienen más de uno.
     * Ejemplo: {@code configuracion.ConfiguracionController} y {@code configuracion.OtroController} → falla nombrando los dos.
     */
    private static ArchCondition<JavaClass> serElUnicoControllerDeSuModulo() {
        return new ArchCondition<>("ser el único controller de su módulo") {
            private final Map<String, List<String>> controllersPorModulo = new ConcurrentHashMap<>();

            @Override
            public void check(JavaClass controller, ConditionEvents eventos) {
                controllersPorModulo
                        .computeIfAbsent(moduloDe(controller), modulo -> new CopyOnWriteArrayList<>())
                        .add(controller.getName());
            }

            @Override
            public void finish(ConditionEvents eventos) {
                new TreeMap<>(controllersPorModulo).forEach((modulo, controllers) -> {
                    String mensaje = "El módulo '%s' tiene %d controllers: %s"
                            .formatted(
                                    modulo,
                                    controllers.size(),
                                    String.join(
                                            ", ", controllers.stream().sorted().toList()));
                    eventos.add(new SimpleConditionEvent(modulo, controllers.size() == 1, mensaje));
                });
                controllersPorModulo.clear();
            }
        };
    }

    /**
     * Devuelve el módulo de una clase: el primer paquete después de {@code com.tcg.bot}.
     * Ejemplo: {@code com.tcg.bot.configuracion.ConfiguracionController} → {@code configuracion}; {@code com.tcg.bot.X} → {@code (raíz)}.
     */
    private static String moduloDe(JavaClass clase) {
        String paquete = clase.getPackageName();
        if (paquete.equals(PAQUETE_RAIZ)) {
            return "(raíz)";
        }
        String resto = paquete.substring(PAQUETE_RAIZ.length() + 1);
        int punto = resto.indexOf('.');
        return punto < 0 ? resto : resto.substring(0, punto);
    }

    /** Deja afuera del análisis las clases de {@code com.tcg.bot.legacy}: la v1 no se revisa. */
    static final class SinLegacy implements ImportOption {

        @Override
        public boolean includes(Location ubicacion) {
            return !ubicacion.contains("/com/tcg/bot/legacy/");
        }
    }
}
