package com.tcg.bot.controller;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.InventoryMovement;
import com.tcg.bot.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.storage-dir=target/test-data")
@AutoConfigureMockMvc
class CashPageTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InventoryService inventoryService;

    @Test
    void cashTabShowsNetTotalsAndVoidButtons() throws Exception {
        String today = LocalDate.now(ZoneId.of("America/Buenos_Aires")).toString();

        CashRegisterEntry kept = entry(today, "VENTA", "Sol Ring", "1500", 1, "V1", "");
        CashRegisterEntry voided = entry(today, "VENTA", "Counterspell", "800", 2, "V2", "");
        voided.setVoided(true);
        CashRegisterEntry voidRow = entry(today, "ANULACION", "Counterspell", "800", 2, "V3", "V2");

        when(inventoryService.getCashRegisterEntries()).thenReturn(List.of(voidRow, voided, kept));
        when(inventoryService.getRecentMovements()).thenReturn(List.of(
                new InventoryMovement(today + " 10:00:00", today, "10:00:00", "SALIDA", "-1", "Sol Ring",
                        "Commander Masters", "CMM", "410", "nonfoil", "2", "1", "Unidad vendida")
        ));

        mvc.perform(get("/movimientos").param("tab", "cash"))
                .andExpect(status().isOk())
                // 1500 + 1600 - 1600 = 1500
                .andExpect(content().string(containsString("$ 1.500")))
                .andExpect(content().string(containsString("Anulada")))
                .andExpect(content().string(containsString("name=\"saleId\" value=\"V1\"")))
                .andExpect(content().string(not(containsString("name=\"saleId\" value=\"V2\""))))
                .andExpect(content().string(containsString("-$ 1.600")));
    }

    private static CashRegisterEntry entry(String date, String type, String name, String price, int qty,
                                           String saleId, String voidsSaleId) {
        CashRegisterEntry entry = new CashRegisterEntry();
        entry.setDate(date);
        entry.setTime("12:00:00");
        entry.setType(type);
        entry.setName(name);
        entry.setSetName("Commander Masters");
        entry.setSetCode("CMM");
        entry.setCollectorNumber("410");
        entry.setPrinting("nonfoil");
        entry.setLocalPrice(price);
        entry.setQuantity(String.valueOf(qty));
        entry.setTotal(String.valueOf(Integer.parseInt(price) * qty));
        entry.setSaleId(saleId);
        entry.setVoidsSaleId(voidsSaleId);
        return entry;
    }
}
