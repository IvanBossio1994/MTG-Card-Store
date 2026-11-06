package com.tcg.bot.controller;

import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.WantedCard;
import com.tcg.bot.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.storage-dir=target/test-data-wanted")
@AutoConfigureMockMvc
class WantedPageTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InventoryService inventoryService;

    @Test
    void arrivedCardsComeWithAWhatsappLink() throws Exception {
        WantedCard wanted = WantedCard.fromRow(List.of("2026-11-01", "Juan", "11 5555-1234", "Sol Ring", "", "1",
                "BUSCANDO", "", "B1"), 2);
        WantedCard waiting = WantedCard.fromRow(List.of("2026-11-02", "Ana", "", "Fatal Push", "", "2",
                "BUSCANDO", "", "B2"), 3);
        InventoryCard card = new InventoryCard();
        card.setId("sol");
        card.setName("Sol Ring");
        card.setQuantity("1");
        card.setLocation("Vitrina");
        when(inventoryService.getWantedCards()).thenReturn(List.of(wanted, waiting));
        when(inventoryService.getAllStockCards()).thenReturn(List.of(card));

        mvc.perform(get("/buscadas"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Avisale al cliente")))
                .andExpect(content().string(containsString("https://wa.me/5491155551234?text=")))
                .andExpect(content().string(containsString("Vitrina")))
                .andExpect(content().string(containsString("2 × Fatal Push")));
    }

    @Test
    void savesTheMissingCardsOfADeck() throws Exception {
        mvc.perform(post("/buscadas").param("customer", "Juan").param("cards", "1 Fatal Push\n2 Thoughtseize"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("wantedMessage", containsString("2 cartas anotadas para Juan")));

        verify(inventoryService).appendWantedCards(argThat(cards -> cards.size() == 2));
    }
}
