package com.tcg.bot.controller;

import com.tcg.bot.dto.CardKingdomProduct;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.service.CardKingdomApiService;
import com.tcg.bot.service.InventoryService;
import com.tcg.bot.service.PricingSettingsService;
import com.tcg.bot.service.ScryfallPriceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.storage-dir=target/test-data-search")
@AutoConfigureMockMvc
class SearchPageTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    PricingSettingsService pricingSettings;

    @MockitoBean
    InventoryService inventoryService;

    @MockitoBean
    CardKingdomApiService cardKingdomApiService;

    @MockitoBean
    ScryfallPriceService scryfallPriceService;

    @Test
    void showsComparisonAndReserveButton() throws Exception {
        pricingSettings.update(1000, 100, 0, 0);
        pricingSettings.updateSources(true, true, 1100, PricingSettingsService.CombineMode.MAX);

        CardKingdomProduct product = new CardKingdomProduct();
        product.setName("Sol Ring");
        product.setEdition("Commander Masters");
        product.setSku("CMM-0410");
        product.setFoil("false");
        CardKingdomProduct.ConditionValues values = new CardKingdomProduct.ConditionValues();
        values.setNmPrice("1.00");
        product.setConditionValues(values);
        when(cardKingdomApiService.searchProducts(anyString())).thenReturn(List.of(product));

        InventoryCard card = new InventoryCard();
        card.setId("sol");
        card.setRowIndex(2);
        card.setName("Sol Ring");
        card.setSetCode("CMM");
        card.setSetName("Commander Masters");
        card.setCollectorNumber("410");
        card.setPrinting("nonfoil");
        card.setQuantity("2");
        when(inventoryService.getInventoryCards()).thenReturn(List.of(card));
        when(scryfallPriceService.prices(any())).thenReturn(Map.of(
                ScryfallPriceService.key("CMM", "0410"),
                new ScryfallPriceService.Prices(2.0, null, 3.0, null)));

        mvc.perform(get("/").param("q", "Sol Ring"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("US$ 2,00")))
                .andExpect(content().string(containsString("€ 3,00")))
                .andExpect(content().string(containsString("$ 3.300")))
                .andExpect(content().string(containsString("reserve-button")))
                .andExpect(content().string(containsString("data-card-id=\"sol\"")));
    }
}
