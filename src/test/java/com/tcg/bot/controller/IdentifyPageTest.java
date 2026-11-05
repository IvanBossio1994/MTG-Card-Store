package com.tcg.bot.controller;

import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.service.CardIdentifyService;
import com.tcg.bot.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.storage-dir=target/test-data-identify")
@AutoConfigureMockMvc
class IdentifyPageTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InventoryService inventoryService;

    @MockitoBean
    CardIdentifyService identifyService;

    static InventoryCard bare(int row, String name) {
        InventoryCard card = new InventoryCard();
        card.setRowIndex(row);
        card.setName(name);
        card.setQuantity("2");
        return card;
    }

    @Test
    void listsCardsWithoutEditionAndOffersPrintings() throws Exception {
        when(inventoryService.getInventoryCards()).thenReturn(List.of(bare(5, "Elli's Rage")));
        when(identifyService.candidates("Elli's Rage")).thenReturn(List.of(new CardIdentifyService.Candidate(
                "Dictate of Erebos", "Elli's Rage", "Secret Lair Drop", "SLD", "1563", "2024-03-01",
                List.of("foil"), "", true)));

        mvc.perform(get("/completar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1 carta sin edición o número")));

        mvc.perform(get("/completar").param("row", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("También: Elli&#39;s Rage")))
                .andExpect(content().string(containsString("Secret Lair Drop · SLD #1563")));
    }

    @Test
    void savesChosenPrintingOnlyIfTheRowStillHasTheSameCard() throws Exception {
        when(inventoryService.getInventoryCards()).thenReturn(List.of(bare(5, "Elli's Rage")));

        mvc.perform(post("/completar").param("row", "5").param("originalName", "Otra carta")
                        .param("name", "Dictate of Erebos").param("setCode", "SLD").param("setName", "Secret Lair Drop")
                        .param("collectorNumber", "1563").param("finish", "foil"))
                .andExpect(status().is3xxRedirection());
        verify(inventoryService, never()).updateCardIdentity(anyInt(), org.mockito.ArgumentMatchers.any());

        mvc.perform(post("/completar").param("row", "5").param("originalName", "Elli's Rage")
                        .param("name", "Dictate of Erebos").param("setCode", "SLD").param("setName", "Secret Lair Drop")
                        .param("collectorNumber", "1563").param("finish", "foil"))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<InventoryCard> saved = ArgumentCaptor.forClass(InventoryCard.class);
        verify(inventoryService).updateCardIdentity(eq(5), saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Dictate of Erebos");
        assertThat(saved.getValue().getSetCode()).isEqualTo("SLD");
        assertThat(saved.getValue().getCollectorNumber()).isEqualTo("1563");
        assertThat(saved.getValue().getPrinting()).isEqualTo("foil");
    }
}
