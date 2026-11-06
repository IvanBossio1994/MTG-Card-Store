package com.tcg.bot.controller;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.Consignment;
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

@SpringBootTest(properties = "app.storage-dir=target/test-data-consign")
@AutoConfigureMockMvc
class ConsignmentPageTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InventoryService inventoryService;

    @Test
    void showsOwnersWhatSoldAndWhatIsOwed() throws Exception {
        Consignment item = Consignment.fromRow(List.of("2026-11-01", "Juan", "11 5555-1234", "abc", "Sheoldred",
                "Dominaria United", "2", "70", "0", "ACTIVA", "K1"), 2);
        InventoryCard card = new InventoryCard();
        card.setId("abc");
        card.setName("Sheoldred");
        card.setQuantity("1");
        card.setLocalPrice("10000");
        CashRegisterEntry sale = new CashRegisterEntry();
        sale.setType(CashRegisterEntry.TYPE_SALE);
        sale.setCardId("abc");
        sale.setQuantity("1");
        sale.setTotal("10000");
        when(inventoryService.getConsignments()).thenReturn(List.of(item));
        when(inventoryService.getAllStockCards()).thenReturn(List.of(card));
        when(inventoryService.getCashRegisterEntries()).thenReturn(List.of(sale));

        mvc.perform(get("/consignacion"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Juan")))
                .andExpect(content().string(containsString("1 a la venta · 1 vendidas")))
                .andExpect(content().string(containsString("Le debés $ 7.000")))
                .andExpect(content().string(containsString("Pagar en efectivo")));
    }

    @Test
    void buyPageOffersConsignment() throws Exception {
        mvc.perform(get("/compras"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Consignación (le pagás cuando se venda)")))
                .andExpect(content().string(containsString("name=\"consignPercent\"")));
    }
}
