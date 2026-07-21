package com.tcg.bot.service;

import com.tcg.bot.model.CardReservation;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.ReservationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WhatsappOrderService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappOrderService.class);
    private static final ZoneId APP_ZONE = ZoneId.of("America/Buenos_Aires");
    private static final DateTimeFormatter SHEET_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern LEADING_QUANTITY = Pattern.compile("^\\s*(\\d+)\\s*x?\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_QUANTITY = Pattern.compile("^\\s*(.+?)\\s+x\\s*(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SET_CODE = Pattern.compile("[\\[(]([A-Za-z0-9]{2,8})[\\])]");

    private final StoreSettingsService storeSettingsService;
    private final InventoryService inventoryService;
    private final WhatsappClient whatsappClient;
    private final WhatsappNotificationService notificationService;
    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();
    private final Set<String> processedMessageIds = ConcurrentHashMap.newKeySet();

    public WhatsappOrderService(
            StoreSettingsService storeSettingsService,
            InventoryService inventoryService,
            WhatsappClient whatsappClient,
            WhatsappNotificationService notificationService
    ) {
        this.storeSettingsService = storeSettingsService;
        this.inventoryService = inventoryService;
        this.whatsappClient = whatsappClient;
        this.notificationService = notificationService;
    }

    public void handleIncomingText(String from, String messageId, String profileName, String text) {
        if (from == null || from.isBlank() || text == null || text.isBlank()) {
            return;
        }

        if (messageId != null && !messageId.isBlank() && !processedMessageIds.add(messageId)) {
            return;
        }

        if (processedMessageIds.size() > 300) {
            processedMessageIds.clear();
        }

        String phone = digitsOnly(from);
        try {
            whatsappClient.sendText(phone, responseFor(phone, profileName, text.trim()));
        } catch (Exception e) {
            log.warn("No se pudo procesar el mensaje de WhatsApp.", e);
            whatsappClient.sendText(
                    phone,
                    "No pude consultar el sistema despues de 3 intentos. Te deriva un humano de la tienda."
            );
        }
    }

    private String responseFor(String phone, String profileName, String text) throws Exception {
        if (!storeSettingsService.hasWhatsappConfigured()) {
            return "WhatsApp no esta configurado en la tienda.";
        }

        if (!isOpenNow()) {
            return "El local esta cerrado por hoy. Dejanos tu mensaje y te responde un humano cuando vuelva a estar operativo.";
        }

        Conversation conversation = conversations.computeIfAbsent(phone, unused -> new Conversation());
        conversation.phone = phone;

        String normalized = normalize(text);
        if (normalized.equals("cancelar")) {
            conversations.remove(phone);
            return "Listo, cancele el pedido en curso.";
        }

        if (normalized.equals("ayuda") || normalized.equals("-ayuda") || normalized.equals("help")) {
            return helpText();
        }

        ReservationClient client = findClientByPhone(phone);
        if (client != null) {
            conversation.clientName = client.getClient();
            conversation.dni = client.getDni();
        }

        if (conversation.stage == Stage.ASK_NAME) {
            conversation.clientName = text.trim();
            conversation.stage = Stage.ASK_DNI;
            return "Gracias " + conversation.clientName + ". Pasame tu DNI para registrar el pedido.";
        }

        if (conversation.stage == Stage.ASK_DNI) {
            String dni = digitsOnly(text);
            if (dni.length() < 6) {
                return "Necesito un DNI valido para registrar el pedido.";
            }
            conversation.dni = dni;
            return confirmOrder(conversation);
        }

        if (conversation.stage == Stage.ASK_CONFIRM_ADD && isYes(normalized)) {
            addPendingSelection(conversation, 1);
            return "La agregue al pedido. Escribi otra carta, pega una lista o manda confirmar.";
        }

        if (conversation.stage == Stage.ASK_CONFIRM_ADD && isNo(normalized)) {
            conversation.pendingSelection = null;
            conversation.stage = Stage.IDLE;
            return "No la agregue. Podes consultar otra carta o escribir pedido.";
        }

        if (conversation.stage == Stage.ASK_VARIANT) {
            SelectionResult selection = selectFromPending(conversation, normalized);
            if (selection.selected() != null) {
                conversation.pendingSelection = selection.selected();
                conversation.stage = Stage.ASK_CONFIRM_ADD;
                return selection.selected().summary() + "\nLa agrego al pedido? Responde si o no.";
            }
            return selection.message();
        }

        if (normalized.equals("pedido")) {
            conversation.stage = Stage.ASK_LIST;
            return greeting(client, profileName) + "Pega la lista del pedido, una carta por linea. Ej: 2 Sol Ring";
        }

        if (conversation.stage == Stage.ASK_LIST || looksLikeOrderList(text)) {
            List<OrderLine> lines = parseOrderLines(text);
            if (lines.isEmpty()) {
                return "No pude leer la lista. Usa una carta por linea, por ejemplo: 2 Sol Ring";
            }

            List<OrderItem> items = resolveOrderLines(lines);
            conversation.items.addAll(items);
            conversation.stage = Stage.IDLE;
            return orderDraftResponse(conversation, items);
        }

        if (normalized.equals("confirmar")) {
            if (conversation.items.isEmpty()) {
                return "Todavia no hay cartas en el pedido. Escribi pedido o consulta una carta.";
            }
            if (client == null && (isBlank(conversation.clientName) || isBlank(conversation.dni))) {
                conversation.stage = Stage.ASK_NAME;
                return "Para registrar tu primer pedido necesito tu nombre y DNI. Como te llamas?";
            }
            return confirmOrder(conversation);
        }

        return stockResponse(conversation, client, profileName, text);
    }

    private String stockResponse(
            Conversation conversation,
            ReservationClient client,
            String profileName,
            String text
    ) throws Exception {
        String query = cleanedStockQuery(text);
        if (query.isBlank()) {
            return helpText();
        }

        List<StockOption> options = stockOptions(query);
        if (options.isEmpty()) {
            conversation.pendingSelection = new StockOption(null, query, "", "", "", "", 0, 0);
            conversation.stage = Stage.ASK_CONFIRM_ADD;
            return greeting(client, profileName)
                    + "No encontre stock disponible de " + query + ". Queres dejarla reservada igual?";
        }

        conversation.pendingOptions = options;
        conversation.stage = Stage.ASK_VARIANT;
        int total = options.stream().mapToInt(StockOption::availableQuantity).sum();
        StockOption cheapest = cheapest(options);
        List<String> lines = new ArrayList<>();
        lines.add(greeting(client, profileName)
                + "Tenemos " + total + " " + query + " disponible(s).");
        lines.add("La mas barata disponible es:");
        lines.add(cheapest.summary());
        lines.add("Queres esa, alguna edicion especifica o la mas barata disponible?");
        return String.join("\n", lines);
    }

    private String orderDraftResponse(Conversation conversation, List<OrderItem> addedItems) {
        List<String> lines = new ArrayList<>();
        lines.add("Agregue " + addedItems.size() + " linea(s) al pedido:");
        for (OrderItem item : addedItems) {
            lines.add("- " + item.quantity + "x " + item.name + priceSuffix(item.localPrice));
        }
        lines.add("Para guardar el pedido escribi confirmar. Para cancelar escribi cancelar.");
        return String.join("\n", lines);
    }

    private String confirmOrder(Conversation conversation) throws Exception {
        if (conversation.items.isEmpty()) {
            return "No hay cartas para confirmar.";
        }

        LocalDateTime now = LocalDateTime.now(APP_ZONE);
        List<CardReservation> reservations = new ArrayList<>();
        for (OrderItem item : conversation.items) {
            CardReservation reservation = new CardReservation();
            reservation.setId("RSV-WA-" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                    + "-" + Long.toUnsignedString(System.nanoTime(), 36));
            reservation.setStatus(CardReservation.STATUS_RESERVED);
            reservation.setName(item.name);
            reservation.setSetName(item.setName);
            reservation.setSetCode(item.setCode);
            reservation.setCollectorNumber(item.collectorNumber);
            reservation.setPrinting(item.printing);
            reservation.setCondition(item.condition);
            reservation.setQuantity(String.valueOf(item.quantity));
            reservation.setClient(conversation.clientName);
            reservation.setPhone(conversation.phone);
            reservation.setDni(conversation.dni);
            reservation.setReservationDate(now.format(SHEET_DATE_TIME_FORMAT));
            reservation.setPickupDate("A convenir");
            reservation.setPaymentDate("");
            reservation.setNotes("WhatsApp - cualquier estado/edicion");
            reservations.add(reservation);
        }

        retry(() -> inventoryService.appendReservations(reservations));
        retry(() -> inventoryService.upsertReservationClient(
                conversation.clientName,
                conversation.phone,
                conversation.dni,
                now.format(SHEET_DATE_TIME_FORMAT)
        ));
        notificationService.orderCreated(conversation.clientName, conversation.phone, reservations.size());

        int lineCount = conversation.items.size();
        conversations.remove(conversation.phone);
        return "Listo " + conversation.clientName + ". Tu pedido quedo reservado con "
                + lineCount + " carta(s). La tienda lo ve en Pedidos/Reservas.";
    }

    private void retry(CheckedRunnable runnable) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                runnable.run();
                return;
            } catch (Exception e) {
                last = e;
                Thread.sleep(300L * attempt);
            }
        }
        throw last;
    }

    private List<OrderItem> resolveOrderLines(List<OrderLine> lines) throws Exception {
        List<OrderItem> items = new ArrayList<>();
        for (OrderLine line : lines) {
            StockOption option = cheapest(stockOptions(line.name()));
            if (option == null) {
                items.add(new OrderItem(line.quantity(), line.name(), "", "", "", "", "", 0));
                continue;
            }
            items.add(orderItem(option, line.quantity()));
        }
        return items;
    }

    private List<StockOption> stockOptions(String query) throws Exception {
        String normalizedQuery = normalize(query);
        List<InventoryCard> cards = retryInventoryCards();
        List<CardReservation> reservations = retryReservations();
        Map<String, Integer> reservedByStock = reservedByStockKey(reservations);
        List<StockOption> options = new ArrayList<>();

        for (InventoryCard card : cards) {
            if (card.getName() == null || !normalize(card.getName()).contains(normalizedQuery)) {
                continue;
            }

            int available = Math.max(quantity(card) - reservedByStock.getOrDefault(stockKey(card), 0), 0);
            if (available <= 0) {
                continue;
            }

            options.add(new StockOption(
                    card,
                    card.getName(),
                    blankToEmpty(card.getSetName()),
                    blankToEmpty(card.getSetCode()),
                    blankToEmpty(card.getCollectorNumber()),
                    displayPrinting(card.getPrinting()),
                    available,
                    parsePrice(card.getLocalPrice())
            ));
        }

        return options.stream()
                .sorted(Comparator
                        .comparingDouble((StockOption option) -> option.localPrice() <= 0 ? Double.MAX_VALUE : option.localPrice())
                        .thenComparing(StockOption::name)
                        .thenComparing(StockOption::setName))
                .limit(8)
                .toList();
    }

    private List<InventoryCard> retryInventoryCards() throws Exception {
        Holder<List<InventoryCard>> holder = new Holder<>();
        retry(() -> holder.value = inventoryService.getInventoryCards());
        return holder.value == null ? List.of() : holder.value;
    }

    private List<CardReservation> retryReservations() throws Exception {
        Holder<List<CardReservation>> holder = new Holder<>();
        retry(() -> holder.value = inventoryService.getReservations());
        return holder.value == null ? List.of() : holder.value;
    }

    private Map<String, Integer> reservedByStockKey(List<CardReservation> reservations) {
        Map<String, Integer> reserved = new LinkedHashMap<>();
        for (CardReservation reservation : reservations) {
            if (!CardReservation.STATUS_RESERVED.equalsIgnoreCase(reservation.getStatus())) {
                continue;
            }
            String key = reservationKey(reservation);
            if (key.isBlank()) {
                continue;
            }
            reserved.merge(key, reservation.reservationQuantity(), Integer::sum);
        }
        return reserved;
    }

    private SelectionResult selectFromPending(Conversation conversation, String normalizedText) {
        List<StockOption> options = conversation.pendingOptions;
        if (options == null || options.isEmpty()) {
            conversation.stage = Stage.IDLE;
            return new SelectionResult(null, "No tengo una seleccion pendiente. Volve a consultar la carta.");
        }

        if (normalizedText.contains("barata") || normalizedText.equals("si")) {
            return new SelectionResult(cheapest(options), "");
        }

        for (StockOption option : options) {
            String haystack = normalize(option.setName() + " " + option.setCode() + " " + option.collectorNumber()
                    + " " + option.printing() + " " + option.name());
            if (haystack.contains(normalizedText)) {
                return new SelectionResult(option, "");
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("Tengo estas opciones. Responde con la edicion, codigo o 'la mas barata':");
        for (StockOption option : options) {
            lines.add("- " + option.summary());
        }
        return new SelectionResult(null, String.join("\n", lines));
    }

    private void addPendingSelection(Conversation conversation, int quantity) {
        StockOption option = conversation.pendingSelection;
        conversation.items.add(orderItem(option, quantity));
        conversation.pendingSelection = null;
        conversation.pendingOptions = List.of();
        conversation.stage = Stage.IDLE;
    }

    private OrderItem orderItem(StockOption option, int quantity) {
        if (option == null || option.card() == null) {
            return new OrderItem(quantity, option == null ? "" : option.name(), "", "", "", "", "", 0);
        }

        InventoryCard card = option.card();
        return new OrderItem(
                quantity,
                card.getName(),
                blankToEmpty(card.getSetName()),
                blankToEmpty(card.getSetCode()),
                blankToEmpty(card.getCollectorNumber()),
                displayPrinting(card.getPrinting()),
                blankToEmpty(card.getCondition()),
                option.localPrice()
        );
    }

    private StockOption cheapest(List<StockOption> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        return options.stream()
                .min(Comparator
                        .comparingDouble((StockOption option) -> option.localPrice() <= 0 ? Double.MAX_VALUE : option.localPrice())
                        .thenComparing(StockOption::name))
                .orElse(null);
    }

    private ReservationClient findClientByPhone(String phone) throws Exception {
        String normalizedPhone = digitsOnly(phone);
        Holder<ReservationClient> holder = new Holder<>();
        retry(() -> holder.value = inventoryService.getReservationClients()
                .stream()
                .filter(client -> digitsOnly(client.getPhone()).equals(normalizedPhone))
                .findFirst()
                .orElse(null));
        return holder.value;
    }

    private boolean isOpenNow() {
        if (storeSettingsService.isWhatsappAlwaysOn()) {
            return true;
        }

        LocalTime now = LocalTime.now(APP_ZONE);
        LocalTime opening = LocalTime.parse(storeSettingsService.getWhatsappOpeningTime());
        LocalTime closing = LocalTime.parse(storeSettingsService.getWhatsappClosingTime());
        if (opening.equals(closing)) {
            return true;
        }
        if (opening.isBefore(closing)) {
            return !now.isBefore(opening) && now.isBefore(closing);
        }
        return !now.isBefore(opening) || now.isBefore(closing);
    }

    private List<OrderLine> parseOrderLines(String text) {
        List<OrderLine> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String rawLine : text.split("\\R")) {
            OrderLine line = parseOrderLine(rawLine);
            if (line == null) {
                continue;
            }
            String key = normalize(line.name());
            if (!seen.add(key)) {
                int index = lines.indexOf(lines.stream()
                        .filter(existing -> normalize(existing.name()).equals(key))
                        .findFirst()
                        .orElse(line));
                if (index >= 0) {
                    OrderLine previous = lines.get(index);
                    lines.set(index, new OrderLine(previous.quantity() + line.quantity(), previous.name()));
                }
                continue;
            }
            lines.add(line);
        }
        return lines;
    }

    private OrderLine parseOrderLine(String rawLine) {
        if (rawLine == null) {
            return null;
        }
        String line = rawLine.trim();
        if (line.isBlank() || line.startsWith("//") || line.startsWith("#")) {
            return null;
        }

        int quantity = 1;
        Matcher leading = LEADING_QUANTITY.matcher(line);
        Matcher trailing = TRAILING_QUANTITY.matcher(line);
        if (leading.matches()) {
            quantity = Integer.parseInt(leading.group(1));
            line = leading.group(2).trim();
        } else if (trailing.matches()) {
            line = trailing.group(1).trim();
            quantity = Integer.parseInt(trailing.group(2));
        }

        line = SET_CODE.matcher(line).replaceAll("").trim();
        line = line.replaceAll("(?i)\\bnon[- ]?foil\\b", "")
                .replaceAll("(?i)\\bfoil\\b", "")
                .replaceAll("\\s+", " ")
                .trim();

        return line.isBlank() || quantity <= 0 ? null : new OrderLine(quantity, line);
    }

    private boolean looksLikeOrderList(String text) {
        return text.contains("\n") || LEADING_QUANTITY.matcher(text).matches() || TRAILING_QUANTITY.matcher(text).matches();
    }

    private String cleanedStockQuery(String text) {
        return text.replaceFirst("(?i)^\\s*(stock|consulta|consultar|tenes|tienen|hay|quiero|queria saber si tienen)\\s+", "")
                .replace("?", "")
                .trim();
    }

    private String greeting(ReservationClient client, String profileName) {
        if (client != null && client.getClient() != null && !client.getClient().isBlank()) {
            return "Hola " + client.getClient() + ". ";
        }
        if (profileName != null && !profileName.isBlank()) {
            return "Hola " + profileName + ". ";
        }
        return "";
    }

    private String helpText() {
        return String.join("\n",
                "Comandos disponibles:",
                "- stock Sol Ring",
                "- pedido",
                "- confirmar",
                "- cancelar",
                "Tambien podes pegar una lista, una carta por linea: 2 Sol Ring"
        );
    }

    private boolean isYes(String normalized) {
        return normalized.equals("si") || normalized.equals("s") || normalized.equals("dale") || normalized.equals("ok");
    }

    private boolean isNo(String normalized) {
        return normalized.equals("no") || normalized.equals("n");
    }

    private String stockKey(InventoryCard card) {
        return normalize(card.getName()) + "|"
                + normalize(card.getSetCode()) + "|"
                + normalize(card.getCollectorNumber()) + "|"
                + normalize(card.getPrinting()) + "|"
                + normalize(card.getCondition());
    }

    private String reservationKey(CardReservation reservation) {
        if (isBlank(reservation.getSetCode()) && isBlank(reservation.getCollectorNumber())) {
            return "";
        }
        return normalize(reservation.getName()) + "|"
                + normalize(reservation.getSetCode()) + "|"
                + normalize(reservation.getCollectorNumber()) + "|"
                + normalize(reservation.getPrinting()) + "|"
                + normalize(reservation.getCondition());
    }

    private int quantity(InventoryCard card) {
        try {
            return Math.max(Integer.parseInt(card.getQuantity().trim()), 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private double parsePrice(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(value.replace(".", "").replace(",", ".").replaceAll("[^0-9.]", ""));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private String priceSuffix(double price) {
        return price <= 0 ? "" : " - $" + String.format(Locale.US, "%.0f", price);
    }

    private String displayPrinting(String printing) {
        return printing != null && printing.equalsIgnoreCase("foil") ? "Foil" : "No Foil";
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return normalized.replaceAll("\\s+", " ");
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D+", "");
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private static class Holder<T> {
        private T value;
    }

    private enum Stage {
        IDLE,
        ASK_LIST,
        ASK_VARIANT,
        ASK_CONFIRM_ADD,
        ASK_NAME,
        ASK_DNI
    }

    private static class Conversation {
        private String phone = "";
        private String clientName = "";
        private String dni = "";
        private Stage stage = Stage.IDLE;
        private List<StockOption> pendingOptions = List.of();
        private StockOption pendingSelection;
        // ponytail: sessions are in-memory for the first WhatsApp cut; persist them if stores need restart-safe carts.
        private final List<OrderItem> items = new ArrayList<>();
    }

    private record StockOption(
            InventoryCard card,
            String name,
            String setName,
            String setCode,
            String collectorNumber,
            String printing,
            int availableQuantity,
            double localPrice
    ) {
        String summary() {
            String set = setCode.isBlank() ? setName : setCode;
            return name + " - " + (set.isBlank() ? "edicion sin especificar" : set)
                    + " - " + printing
                    + " - " + availableQuantity + " disponible(s)"
                    + (localPrice <= 0 ? "" : " - $" + String.format(Locale.US, "%.0f", localPrice));
        }
    }

    private record SelectionResult(StockOption selected, String message) {
    }

    private record OrderLine(int quantity, String name) {
    }

    private record OrderItem(
            int quantity,
            String name,
            String setName,
            String setCode,
            String collectorNumber,
            String printing,
            String condition,
            double localPrice
    ) {
    }
}
