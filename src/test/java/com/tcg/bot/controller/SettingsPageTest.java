package com.tcg.bot.controller;

import com.tcg.bot.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.storage-dir=target/test-data-settings")
@AutoConfigureMockMvc
class SettingsPageTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InventoryService inventoryService;

    @Test
    void showsFreePlanAndPricingFields() throws Exception {
        mvc.perform(get("/configuracion"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Plan Gratis")))
                .andExpect(content().string(containsString("name=\"licenseKey\"")))
                .andExpect(content().string(containsString("name=\"markupPercent\"")))
                .andExpect(content().string(containsString("name=\"minimumPrice\"")))
                .andExpect(content().string(containsString("name=\"cardmarketEnabled\"")))
                .andExpect(content().string(containsString("Promedio de las fuentes")))
                .andExpect(content().string(containsString("Cuenta de Google")))
                .andExpect(content().string(containsString("google-oauth.properties")))
                .andExpect(content().string(containsString("Actualizaciones")))
                .andExpect(content().string(containsString("Las actualizaciones automáticas funcionan en la versión para Windows")));
    }
}
