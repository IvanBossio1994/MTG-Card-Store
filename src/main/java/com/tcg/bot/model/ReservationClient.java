package com.tcg.bot.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReservationClient {
    private String firstName;
    private String lastName;
    private String client;
    private String phone;
    private String dni;
    private String email;
    private String notes;
    private String points;
    private String updatedAt;
    private int rowIndex;

    public String getClient() {
        String fullName = ((firstName == null ? "" : firstName.trim()) + " "
                + (lastName == null ? "" : lastName.trim())).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }

        return client == null ? "" : client.trim();
    }

    public void setClient(String client) {
        this.client = client;
        if ((firstName == null || firstName.isBlank()) && client != null && !client.isBlank()) {
            this.firstName = client.trim();
        }
    }
}
