package com.tcg.bot.controller;

import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.PricePoint;
import com.tcg.bot.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.storage-dir=target/test-data-prices")
@AutoConfigureMockMvc
class PricePageTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InventoryService inventoryService;

    @Test
    void showsTrendAndChart() throws Exception {
        InventoryCard card = new InventoryCard();
        card.setId("a");
        card.setName("Sol Ring");
        card.setSetName("Commander Masters");
        card.setSetCode("CMM");
        card.setCollectorNumber("410");
        card.setLocalPrice("5000");
        card.setCkPriceUsd("2.99");
        when(inventoryService.getInventoryCards()).thenReturn(List.of(card));
        when(inventoryService.getPriceHistory()).thenReturn(List.of(
                new PricePoint("2026-09-01", "a", "Sol Ring", "Commander Masters", "CMM", "410", "nonfoil", 2.0, null, null, 4000.0),
                new PricePoint("2026-11-01", "a", "Sol Ring", "Commander Masters", "CMM", "410", "nonfoil", 2.99, 2.5, null, 5000.0)));

        mvc.perform(get("/precios"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("+25%")))
                .andExpect(content().string(containsString("Ver historial")));

        mvc.perform(get("/precios").param("card", "a"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<polyline")))
                .andExpect(content().string(containsString("US$ 2,50")));
    }
}
