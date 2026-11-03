package com.tcg.bot.controller;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.CreditEntry;
import com.tcg.bot.model.InventoryCard;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.storage-dir=target/test-data-buy")
@AutoConfigureMockMvc
class BuyPageTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InventoryService inventoryService;

    @Test
    void registersACashPurchaseOfACardAlreadyInTheSheet() throws Exception {
        InventoryCard card = new InventoryCard();
        card.setId("sol");
        card.setRowIndex(4);
        card.setName("Sol Ring");
        card.setQuantity("1");
        card.setLocalPrice("3000");
        when(inventoryService.getInventoryCards()).thenReturn(List.of(card));

        mvc.perform(post("/compras")
                        .param("lines", "[{\"sku\":\"CMM-410\",\"cardId\":\"sol\",\"quantity\":2,\"unitPaid\":1500}]")
                        .param("customer", "Ana")
                        .param("payment", "cash"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("buyMessage", containsString("$ 3.000 en efectivo")));

        ArgumentCaptor<CashRegisterEntry> cash = ArgumentCaptor.forClass(CashRegisterEntry.class);
        verify(inventoryService).appendCashEntry(cash.capture());
        assertThat(cash.getValue().isBuy()).isTrue();
        assertThat(cash.getValue().drawerAmount()).isEqualTo(-3000);
        verify(inventoryService).updateStockState(eq(4), org.mockito.ArgumentMatchers.argThat(c -> "3".equals(c.getQuantity())));
    }

    @Test
    void customersPageShowsBalances() throws Exception {
        when(inventoryService.getCreditEntries()).thenReturn(List.of(
                new CreditEntry("2026-11-03", "10:00:00", "Ana", "", 2000, "Compra de 2 cartas", "C1"),
                new CreditEntry("2026-11-03", "11:00:00", "ana", "", -500, "Usado en una venta", "V1")));

        mvc.perform(get("/clientes"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("$ 1.500")))
                .andExpect(content().string(containsString("-$ 500")));
    }
}
