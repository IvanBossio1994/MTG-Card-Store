package com.tcg.bot.service;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.Reservation;
import com.tcg.bot.util.Amounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/** Los numeros del dia que se muestran arriba en Inicio. Todo sale del Sheet de la tienda. */
@Service
public class StoreSummaryService {

    private static final Logger log = LoggerFactory.getLogger(StoreSummaryService.class);

    private final InventoryStore store;
    private final Clock clock;

    @Autowired
    public StoreSummaryService(InventoryStore store) {
        this(store, Clock.system(ZoneId.of("America/Buenos_Aires")));
    }

    StoreSummaryService(InventoryStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public record Summary(
            String salesToday,
            int salesCountToday,
            int activeReservations,
            int reservationsDueToday,
            int unitsInStock,
            int distinctInStock,
            String stockValue,
            int cardsWithoutPrice,
            int wantedArrived
    ) {
    }

    public Summary summary() {
        LocalDate today = LocalDate.now(clock);
        double sales = 0;
        int salesCount = 0;
        int active = 0;
        int dueToday = 0;
        int units = 0;
        int distinct = 0;
        double value = 0;
        int withoutPrice = 0;
        int wantedArrived = 0;
        List<InventoryCard> stock = List.of();

        try {
            stock = store.getAllStockCards();
            for (InventoryCard card : stock) {
                int quantity = Math.max(Amounts.parseInt(card.getQuantity()), 0);
                if (quantity <= 0 || card.getName() == null || card.getName().isBlank()) {
                    continue;
                }
                units += quantity;
                distinct++;
                double price = Amounts.parse(card.getLocalPrice());
                if (price <= 0) {
                    withoutPrice++;
                }
                value += price * quantity;
            }
        } catch (Exception e) {
            log.debug("Resumen: no se pudo leer el inventario: {}", e.getMessage());
        }

        try {
            for (CashRegisterEntry entry : store.getCashRegisterEntries()) {
                if (entry.affectsCash() && today.toString().equals(entry.getDate())) {
                    sales += entry.signedTotal();
                    if (entry.isSale() && !entry.isVoided()) {
                        salesCount++;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Resumen: no se pudo leer la caja: {}", e.getMessage());
        }

        try {
            List<Reservation> lines = store.getReservations();
            var activeIds = lines.stream().filter(Reservation::isActive).map(Reservation::getReservationId).distinct().toList();
            active = activeIds.size();
            dueToday = (int) lines.stream()
                    .filter(Reservation::isActive)
                    .filter(line -> today.toString().equals(line.getExpiresOn()))
                    .map(Reservation::getReservationId)
                    .distinct()
                    .count();
        } catch (Exception e) {
            log.debug("Resumen: no se pudieron leer las reservas: {}", e.getMessage());
        }

        try {
            wantedArrived = WantedService.arrivedCount(store.getWantedCards(), stock);
        } catch (Exception e) {
            log.debug("Resumen: no se pudieron leer las cartas buscadas: {}", e.getMessage());
        }

        return new Summary(ars(sales), salesCount, active, dueToday, units, distinct, ars(value), withoutPrice, wantedArrived);
    }

    private static String ars(double value) {
        return com.tcg.bot.i18n.Money.number(value);
    }
}
