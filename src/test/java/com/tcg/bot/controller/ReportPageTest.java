package com.tcg.bot.controller;

import com.tcg.bot.model.InventoryCard;
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

@SpringBootTest(properties = "app.storage-dir=target/test-data-report")
@AutoConfigureMockMvc
class ReportPageTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InventoryService inventoryService;

    @Test
    void showsStockValueAndStillCards() throws Exception {
        InventoryCard card = new InventoryCard();
        card.setId("old");
        card.setName("Counterspell");
        card.setQuantity("2");
        card.setLocalPrice("5000");
        card.setCost("3000");
        when(inventoryService.getAllStockCards()).thenReturn(List.of(card));

        mvc.perform(get("/reportes").param("dias", "90"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cartas quietas")))
                .andExpect(content().string(containsString("$ 10.000")))
                .andExpect(content().string(containsString("Ganancia posible $ 4.000")))
                .andExpect(content().string(containsString("Sin registro")));
    }
}
