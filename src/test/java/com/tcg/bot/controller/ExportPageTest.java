package com.tcg.bot.controller;

import com.tcg.bot.model.Game;
import com.tcg.bot.model.GameCard;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.storage-dir=target/test-data-export")
@AutoConfigureMockMvc
class ExportPageTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InventoryService inventoryService;

    @Test
    void pageOffersEveryGame() throws Exception {
        mvc.perform(get("/exportar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/exportar/stock.csv?game=magic")))
                .andExpect(content().string(containsString("/exportar/stock.csv?game=pokemon")));
    }

    @Test
    void downloadsTheOtherGamesAsCsv() throws Exception {
        GameCard card = GameCard.fromRow(List.of("2", "Pikachu", "Base", "BS", "58", "Common", "Normal",
                "500", "", "pk1", "base1-58", "", "Carpeta P"), 2);
        when(inventoryService.getGameCards(Game.POKEMON)).thenReturn(List.of(card));

        mvc.perform(get("/exportar/stock.csv").param("game", "pokemon"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("stock-pokemon-")))
                .andExpect(content().string(containsString("2,Pikachu,BS,Base,58,Normal,Common,500,Carpeta P")));
    }
}
