package com.tcg.bot.controller;

import com.tcg.bot.model.Reservation;
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

@SpringBootTest(properties = "app.storage-dir=target/test-data")
@AutoConfigureMockMvc
class ReservationPageTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InventoryService inventoryService;

    @Test
    void listsActiveReservationWithActions() throws Exception {
        Reservation line = new Reservation();
        line.setRowIndex(2);
        line.setDate("2099-01-01");
        line.setReservationId("RABC123");
        line.setCustomer("Juan");
        line.setContact("@juan");
        line.setCardId("sol");
        line.setName("Sol Ring");
        line.setSetName("Commander Masters");
        line.setQuantity("2");
        line.setUnitPrice("1500");
        line.setExpiresOn("2099-01-08");
        line.setStatus(Reservation.STATUS_ACTIVE);
        when(inventoryService.getReservations()).thenReturn(List.of(line));

        mvc.perform(get("/reservas"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Juan")))
                .andExpect(content().string(containsString("$ 3.000")))
                .andExpect(content().string(containsString("/reservas/RABC123/retirar")))
                .andExpect(content().string(containsString("08/01/2099")));
    }
}
