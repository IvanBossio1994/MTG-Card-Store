package com.tcg.bot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CardKingdomProduct {

    private Integer id;

    private String name;

    private String edition;

    private String variation;

    private String sku;

    @JsonProperty("is_foil")
    private String foil;

    @JsonProperty("price_retail")
    private String retailPrice;

    @JsonProperty("qty_retail")
    private Integer retailQuantity;

    @JsonProperty("condition_values")
    private ConditionValues conditionValues;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConditionValues {

        @JsonProperty("nm_price")
        private String nmPrice;

        @JsonProperty("nm_qty")
        private Integer nmQty;

        @JsonProperty("ex_price")
        private String exPrice;

        @JsonProperty("vg_price")
        private String vgPrice;

        @JsonProperty("g_price")
        private String gPrice;

        @JsonProperty("ex_qty")
        private Integer exQty;

        @JsonProperty("vg_qty")
        private Integer vgQty;

        @JsonProperty("g_qty")
        private Integer gQty;
    }
}