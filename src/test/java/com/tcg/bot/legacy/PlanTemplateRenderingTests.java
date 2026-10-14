package com.tcg.bot.legacy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PlanTemplateRenderingTests {

    @Autowired
    private ITemplateEngine templateEngine;

    @Test
    void limitedPlanBannerExplainsReducedModeAndOffersPremium() {
        String html = renderBanner(Map.of(
                "planLimited", true, "planPremium", false, "planSalesCount", 100, "planSalesLimit", 100
        ));

        assertThat(html).contains("Modo reducido del plan gratuito");
        assertThat(html).contains("Llegaste a las 100 ventas");
        assertThat(html).contains("Activar premium");
    }

    @Test
    void bannerWarnsWhenFewFreeSalesRemain() {
        String html = renderBanner(Map.of(
                "planLimited", false, "planPremium", false, "planSalesCount", 85, "planSalesLimit", 100
        ));

        assertThat(html).contains("Te quedan 15 ventas del plan gratuito");
        assertThat(html).doesNotContain("Modo reducido");
    }

    @Test
    void bannerStaysHiddenEarlyInFreePlanAndForPremium() {
        String early = renderBanner(Map.of(
                "planLimited", false, "planPremium", false, "planSalesCount", 10, "planSalesLimit", 100
        ));
        String premium = renderBanner(Map.of(
                "planLimited", false, "planPremium", true, "planSalesCount", 500, "planSalesLimit", 100
        ));

        assertThat(early).doesNotContain("plan-banner\"").doesNotContain("Te quedan");
        assertThat(premium).doesNotContain("Te quedan").doesNotContain("Modo reducido");
    }

    private String renderBanner(Map<String, Object> variables) {
        MockServletContext servletContext = new MockServletContext();
        var exchange = JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(new MockHttpServletRequest(servletContext), new MockHttpServletResponse());
        WebContext context = new WebContext(exchange);
        context.setVariables(variables);
        return templateEngine.process("fragments/layout", Set.of("planBanner"), context);
    }
}
