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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.storage-dir=target/test-data-deck")
@AutoConfigureMockMvc
class DeckPageTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InventoryService inventoryService;

    @Test
    void showsTheFormEmpty() throws Exception {
        mvc.perform(get("/mazo"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pegar un mazo")));
    }

    @Test
    void showsWhatIsInStockWhereItIsAndWhatIsMissing() throws Exception {
        InventoryCard card = new InventoryCard();
        card.setId("bolt");
        card.setRowIndex(3);
        card.setName("Lightning Bolt");
        card.setSetName("Double Masters");
        card.setQuantity("3");
        card.setLocalPrice("2000");
        card.setCkPriceUsd("1");
        card.setLocation("Carpeta 2");
        when(inventoryService.getAllStockCards()).thenReturn(List.of(card));

        mvc.perform(post("/mazo").param("deckList", "4 Lightning Bolt\n1 Fatal Push"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("3 de 5")))
                .andExpect(content().string(containsString("Carpeta 2")))
                .andExpect(content().string(containsString("$ 6.000")))
                .andExpect(content().string(containsString("1 Lightning Bolt\n1 Fatal Push")))
                .andExpect(content().string(containsString("&quot;cardId&quot;:&quot;bolt&quot;")));
    }
}
