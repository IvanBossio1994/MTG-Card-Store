package com.tcg.bot.controller;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.service.CreditService;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import com.tcg.bot.service.StoreSettingsService;
import com.tcg.bot.util.Amounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/** Compras a clientes y crédito de tienda. */
@Controller
public class CustomersController {

    private static final Logger log = LoggerFactory.getLogger(CustomersController.class);

    private final CreditService creditService;
    private final StoreSettingsService storeSettingsService;

    public CustomersController(CreditService creditService, StoreSettingsService storeSettingsService) {
        this.creditService = creditService;
        this.storeSettingsService = storeSettingsService;
    }

    @GetMapping("/compras")
    public String buy(Model model) {
        addBase(model);
        addAccounts(model);
        return "buy";
    }

    @GetMapping("/clientes")
    public String customers(Model model) {
        addBase(model);
        addAccounts(model);
        return "customers";
    }

    /** Clientes con saldo, para elegir en el carrito al cobrar. */
    @GetMapping("/api/credito")
    @ResponseBody
    public List<Map<String, Object>> balances() {
        try {
            return creditService.accounts().stream()
                    .filter(account -> account.balance() >= 1)
                    .map(account -> Map.<String, Object>of("customer", account.customer(), "balance", Math.round(account.balance())))
                    .toList();
        } catch (Exception e) {
            log.debug("No se pudo leer el crédito: {}", e.getMessage());
            return List.of();
        }
    }

    @PostMapping("/clientes/credito")
    public String adjust(@RequestParam String customer,
                         @RequestParam(required = false) String contact,
                         @RequestParam String amount,
                         @RequestParam(defaultValue = "add") String operation,
                         @RequestParam(required = false) String reason,
                         RedirectAttributes redirect) {
        try {
            double value = Math.abs(Amounts.parse(amount));
            boolean subtract = "subtract".equals(operation);
            creditService.add(customer, contact, subtract ? -value : value,
                    reason == null || reason.isBlank() ? (subtract ? I18n.t("cust.manualSubtract") : I18n.t("cust.manualAdd")) : reason, "");
            redirect.addFlashAttribute("creditMessage", I18n.t("cust.nowHas", customer.trim(),
                    com.tcg.bot.i18n.Money.symbol() + " " + CreditService.ars(creditService.balance(customer))));
        } catch (StockOperationException e) {
            redirect.addFlashAttribute("creditError", e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo actualizar el crédito.", e);
            redirect.addFlashAttribute("creditError", I18n.t("err.saveSheet", e.getMessage()));
        }
        return "redirect:/clientes";
    }

    private void addBase(Model model) {
        model.addAttribute("storeName", storeSettingsService.getStoreName());
        model.addAttribute("hasStoreLogo", storeSettingsService.hasLogo());
    }

    private void addAccounts(Model model) {
        try {
            model.addAttribute("accounts", creditService.accounts());
        } catch (Exception e) {
            log.warn("No se pudo leer el crédito de clientes.", e);
            model.addAttribute("accounts", List.of());
            model.addAttribute("creditError", I18n.t("cust.err.read"));
        }
    }
}
