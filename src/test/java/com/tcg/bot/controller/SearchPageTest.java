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

    @Test
    void cardsWithoutCardKingdomPriceCanGetAPriceByHandAndShowAWarning() throws Exception {
        CardKingdomProduct edgar = new CardKingdomProduct();
        edgar.setName("Edgar Markov");
        edgar.setEdition("Commander 2017");
        edgar.setSku("C17-036");
        edgar.setFoil("false");
        CardKingdomProduct sol = new CardKingdomProduct();
        sol.setName("Sol Ring");
        sol.setEdition("Commander");
        sol.setSku("CMD-261");
        sol.setFoil("false");
        when(cardKingdomApiService.searchProducts(anyString())).thenReturn(List.of(edgar, sol));
        when(scryfallPriceService.prices(any())).thenReturn(Map.of());

        InventoryCard manual = new InventoryCard();
        manual.setId("edgar");
        manual.setRowIndex(2);
        manual.setName("Edgar Markov");
        manual.setSetCode("C17");
        manual.setSetName("Commander 2017");
        manual.setCollectorNumber("036");
        manual.setPrinting("nonfoil");
        manual.setQuantity("2");
        manual.setLocalPrice("50000");
        InventoryCard missing = new InventoryCard();
        missing.setId("sol-cmd");
        missing.setRowIndex(3);
        missing.setName("Sol Ring");
        missing.setSetCode("CMD");
        missing.setSetName("Commander");
        missing.setCollectorNumber("261");
        missing.setPrinting("nonfoil");
        missing.setQuantity("1");
        when(inventoryService.getInventoryCards()).thenReturn(List.of(manual, missing));

        mvc.perform(get("/").param("q", "Commander"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("manual-price")))
                .andExpect(content().string(containsString("$ 50.000")))
                .andExpect(content().string(containsString("data-manual-price=\"true\"")))
                .andExpect(content().string(containsString("Sin precio · poner")))
                .andExpect(content().string(containsString("data-card-id=\"sol-cmd\"")));
    }

    @Test
    void cardsThatCardKingdomDoesNotHaveStillShowUpFromTheSheet() throws Exception {
        when(cardKingdomApiService.searchProducts(anyString())).thenReturn(List.of());
        when(scryfallPriceService.prices(any())).thenReturn(Map.of());

        InventoryCard promo = new InventoryCard();
        promo.setId("promo");
        promo.setRowIndex(4);
        promo.setName("Édgar Markov");
        promo.setSetCode("PLST");
        promo.setSetName("The List");
        promo.setCollectorNumber("007");
        promo.setPrinting("nonfoil");
        promo.setQuantity("1");
        InventoryCard other = new InventoryCard();
        other.setId("other");
        other.setRowIndex(5);
        other.setName("Sol Ring");
        other.setQuantity("1");
        when(inventoryService.getInventoryCards()).thenReturn(List.of(promo, other));

        mvc.perform(get("/").param("q", "edgar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No está en Card Kingdom")))
                .andExpect(content().string(containsString("Sin precio · poner")))
                .andExpect(content().string(containsString("data-card-id=\"promo\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("data-card-id=\"other\""))));
    }

    @Test
    void showsCardKingdomResultsWhenTheSheetCannotBeRead() throws Exception {
        CardKingdomProduct product = new CardKingdomProduct();
        product.setName("Lightning Bolt");
        product.setEdition("Magic 2010");
        product.setSku("M10-0146");
        product.setFoil("false");
        CardKingdomProduct.ConditionValues values = new CardKingdomProduct.ConditionValues();
        values.setNmPrice("2.00");
        product.setConditionValues(values);
        when(cardKingdomApiService.searchProducts(anyString())).thenReturn(List.of(product));
        when(inventoryService.getInventoryCards()).thenThrow(new RuntimeException("sin credenciales"));

        mvc.perform(get("/").param("q", "Lightning Bolt"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Magic 2010")))
                .andExpect(content().string(containsString("no se pudo leer tu Sheet")));
    }
}
