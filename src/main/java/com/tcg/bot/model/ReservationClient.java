package com.tcg.bot.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReservationClient {
    private String client;
    private String phone;
    private String dni;
    private String updatedAt;
    private int rowIndex;
}
