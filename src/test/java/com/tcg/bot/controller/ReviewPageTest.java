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

@SpringBootTest(properties = "app.storage-dir=target/test-data-review")
@AutoConfigureMockMvc
class ReviewPageTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InventoryService inventoryService;

    @Test
    void listsCardsWithoutPriceAndCardsPricedByHand() throws Exception {
        when(inventoryService.getInventoryCards()).thenReturn(List.of(
                card("edgar", "Edgar Markov", "50000"),
                card("np", "Sin Precio", "")));

        mvc.perform(get("/revisar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Card Kingdom no tiene precio para estas cartas")))
                .andExpect(content().string(containsString("$ 50.000")))
                .andExpect(content().string(containsString("Poner precio")))
                .andExpect(content().string(containsString("data-card-id=\"np\"")));
    }

    private static InventoryCard card(String id, String name, String price) {
        InventoryCard card = new InventoryCard();
        card.setId(id);
        card.setRowIndex(2);
        card.setName(name);
        card.setSetName("Commander 2017");
        card.setSetCode("C17");
        card.setCollectorNumber("036");
        card.setQuantity("2");
        card.setLocalPrice(price);
        return card;
    }
}
