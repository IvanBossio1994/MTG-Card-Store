package com.tcg.bot.service;

import com.tcg.bot.model.ReservationClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleSheetsServiceMigrationTests {

    private final GoogleSheetsService service = new GoogleSheetsService(null, null);

    @Test
    void normalizesImportedConditionToAppCodes() throws Exception {
        Method method = GoogleSheetsService.class.getDeclaredMethod("normalizeCondition", String.class);
        method.setAccessible(true);

        assertThat((String) method.invoke(service, "NearMint")).isEqualTo("NM");
        assertThat((String) method.invoke(service, "Mint")).isEqualTo("NM");
        assertThat((String) method.invoke(service, "Lightly Played")).isEqualTo("EX");
    }

    @Test
    void defaultsImportedLanguageToEnglishCode() throws Exception {
        Method method = GoogleSheetsService.class.getDeclaredMethod("normalizeLanguage", String.class);
        method.setAccessible(true);

        assertThat((String) method.invoke(service, "")).isEqualTo("EN");
        assertThat((String) method.invoke(service, "English")).isEqualTo("EN");
        assertThat((String) method.invoke(service, "Spanish")).isEqualTo("ES");
    }

    @Test
    void normalizesImportedPrintingToAppValues() throws Exception {
        Method method = GoogleSheetsService.class.getDeclaredMethod("normalizePrinting", String.class);
        method.setAccessible(true);

        assertThat((String) method.invoke(service, "Foil")).isEqualTo("foil");
        assertThat((String) method.invoke(service, "Normal")).isEqualTo("nonfoil");
        assertThat((String) method.invoke(service, "No foil")).isEqualTo("nonfoil");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reservationClientRowValuesUseExpandedClientsColumns() throws Exception {
        Method method = GoogleSheetsService.class.getDeclaredMethod("clientRowValues", ReservationClient.class);
        method.setAccessible(true);

        ReservationClient client = new ReservationClient();
        client.setFirstName("Jorge");
        client.setLastName("Macri");
        client.setDni("3666666");
        client.setPhone("3777777");
        client.setEmail("jorge.macri@hotmail.com");
        client.setNotes("prueba 7");
        client.setPoints("125000");
        client.setUpdatedAt("2026-07-20 12:30:00");

        List<Object> row = (List<Object>) method.invoke(service, client);

        assertThat(row).containsExactly(
                "Jorge",
                "Macri",
                "3666666",
                "3777777",
                "jorge.macri@hotmail.com",
                "prueba 7",
                "125000",
                "2026-07-20 12:30:00"
        );
    }

    @Test
    void reservationClientFromRowReadsExpandedClientsColumns() throws Exception {
        Method method = GoogleSheetsService.class.getDeclaredMethod("reservationClientFromRow", List.class, int.class);
        method.setAccessible(true);

        ReservationClient client = (ReservationClient) method.invoke(
                service,
                List.of(
                        "Jorge",
                        "Macri",
                        "3666666",
                        "3777777",
                        "jorge.macri@hotmail.com",
                        "prueba 7",
                        "125000",
                        "2026-07-20 12:30:00"
                ),
                4
        );

        assertThat(client.getRowIndex()).isEqualTo(4);
        assertThat(client.getFirstName()).isEqualTo("Jorge");
        assertThat(client.getLastName()).isEqualTo("Macri");
        assertThat(client.getClient()).isEqualTo("Jorge Macri");
        assertThat(client.getDni()).isEqualTo("3666666");
        assertThat(client.getPhone()).isEqualTo("3777777");
        assertThat(client.getEmail()).isEqualTo("jorge.macri@hotmail.com");
        assertThat(client.getNotes()).isEqualTo("prueba 7");
        assertThat(client.getPoints()).isEqualTo("125000");
        assertThat(client.getUpdatedAt()).isEqualTo("2026-07-20 12:30:00");
    }
}
