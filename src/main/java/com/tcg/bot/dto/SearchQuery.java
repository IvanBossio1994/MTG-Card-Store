package com.tcg.bot.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchQuery {

    private String name;

    private String setCode;

    private String set;

    private String collectorNumber;

    private Boolean foil;
}
