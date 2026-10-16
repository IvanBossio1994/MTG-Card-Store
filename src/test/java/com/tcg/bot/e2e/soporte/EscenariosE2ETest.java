package com.tcg.bot.e2e.soporte;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PUBLISH_QUIET_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Runner de los escenarios E2E: corre los {@code .feature} de {@code src/test/resources/features} con Cucumber.
 * Solo se ejecuta con el perfil Maven {@code escenarios-e2e}, que se activa al pasar el filtro de tags.
 * Ejemplo: {@code mvn.cmd -q test "-Dcucumber.filter.tags=@critico"} → corre el smoke y deja el reporte en
 * {@code target/evidencias/reporte-e2e.html}.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.tcg.bot.e2e")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/evidencias/reporte-e2e.html")
@ConfigurationParameter(key = PLUGIN_PUBLISH_QUIET_PROPERTY_NAME, value = "true")
public class EscenariosE2ETest {}
