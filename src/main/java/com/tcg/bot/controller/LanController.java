package com.tcg.bot.controller;

import com.tcg.bot.config.LanAccess;
import com.tcg.bot.config.LanConnector;
import com.tcg.bot.config.LocalOnlyFilter;
import com.tcg.bot.i18n.I18n;
import com.tcg.bot.service.StoreSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.util.List;

/**
 * Segunda caja en el celular. En la PC: {@code /segunda-caja} para prenderla, ver el link y el PIN.
 * En el celular: {@code /celular} para poner el PIN.
 */
@Controller
public class LanController {

    private static final Logger log = LoggerFactory.getLogger(LanController.class);

    private final LanAccess lan;
    private final LanConnector connector;
    private final StoreSettingsService storeSettingsService;

    public LanController(LanAccess lan, LanConnector connector, StoreSettingsService storeSettingsService) {
        this.lan = lan;
        this.connector = connector;
        this.storeSettingsService = storeSettingsService;
    }

    @GetMapping("/segunda-caja")
    public String settings(Model model) {
        model.addAttribute("storeName", storeSettingsService.getStoreName());
        model.addAttribute("hasStoreLogo", storeSettingsService.hasLogo());
        model.addAttribute("lanEnabled", lan.isEnabled());
        model.addAttribute("lanOpen", connector.isOpen());
        model.addAttribute("lanPin", lan.getPin());
        List<String> links = lan.addresses().stream().map(ip -> "http://" + ip + ":" + lan.getPort()).toList();
        model.addAttribute("lanLinks", links);
        return "lan";
    }

    @PostMapping("/segunda-caja")
    public String toggle(@RequestParam boolean enabled, RedirectAttributes redirect) {
        try {
            if (enabled) {
                connector.open();
            } else {
                connector.close();
            }
            lan.setEnabled(enabled);
            redirect.addFlashAttribute("lanMessage", I18n.t(enabled ? "lan.on" : "lan.off"));
        } catch (Exception e) {
            log.warn("No se pudo {} la caja del celular.", enabled ? "abrir" : "cerrar", e);
            redirect.addFlashAttribute("lanError", I18n.t("lan.err.port", lan.getPort(), e.getMessage()));
        }
        return "redirect:/segunda-caja";
    }

    @PostMapping("/segunda-caja/pin")
    public String changePin(RedirectAttributes redirect) {
        try {
            lan.changePin();
            redirect.addFlashAttribute("lanMessage", I18n.t("lan.pinChanged"));
        } catch (Exception e) {
            redirect.addFlashAttribute("lanError", e.getMessage());
        }
        return "redirect:/segunda-caja";
    }

    @GetMapping("/celular")
    public String phoneLogin(Model model) {
        model.addAttribute("storeName", storeSettingsService.getStoreName());
        model.addAttribute("locked", lan.isLocked());
        return "lan-login";
    }

    @PostMapping("/celular")
    public String phoneEnter(@RequestParam(defaultValue = "") String pin, HttpServletRequest request,
                             HttpServletResponse response, RedirectAttributes redirect) {
        String session = lan.login(pin);
        if (session == null) {
            redirect.addFlashAttribute("loginError", I18n.t(lan.isLocked() ? "lan.login.locked" : "lan.login.wrong"));
            return "redirect:/celular";
        }
        ResponseCookie cookie = ResponseCookie.from(LanAccess.COOKIE, session)
                .httpOnly(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofDays(30))
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
        return "redirect:/";
    }

    /** {@code remoteRegister} en todas las pantallas: true si las abre el celular (menú corto). */
    @ControllerAdvice(annotations = Controller.class)
    public static class RemoteModelAdvice {
        @ModelAttribute("remoteRegister")
        public boolean remoteRegister(HttpServletRequest request) {
            return Boolean.TRUE.equals(request.getAttribute(LocalOnlyFilter.REMOTE_ATTRIBUTE));
        }
    }
}
