package com.tcg.bot.controller;

import com.tcg.bot.service.GoogleSheetsService;
import com.tcg.bot.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.storage-dir=target/test-data-public")
@AutoConfigureMockMvc
class PublicCatalogPageTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InventoryService inventoryService;

    @MockitoBean
    GoogleSheetsService googleSheetsService;

    @Test
    void withoutGoogleAccountExplainsWhy() throws Exception {
        when(googleSheetsService.usesGoogleAccount()).thenReturn(false);

        mvc.perform(get("/publico"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Catálogo y buylist para compartir")))
                .andExpect(content().string(containsString("hace falta entrar con tu cuenta de Google")));

        mvc.perform(post("/publico").param("cashPercent", "50"))
                .andExpect(redirectedUrl("/publico"))
                .andExpect(flash().attributeExists("publicError"));
    }
}
