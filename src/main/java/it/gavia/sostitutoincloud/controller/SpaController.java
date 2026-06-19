package it.gavia.sostitutoincloud.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SpaController — serve la Single Page Application React da Tomcat.
 *
 * <p>Vengono mappate ESPLICITAMENTE solo le rotte client-side di React Router:
 * ogni richiesta a una di queste viene inoltrata a {@code /index.html} così che
 * il router lato browser gestisca la navigazione.
 *
 * <p>Soluzione a route esplicite (più sicura e prevedibile di un catch-all):
 * <ul>
 *   <li>Le richieste {@code /api/**} restano ai @RestController — non sono mappate qui.</li>
 *   <li>I file statici ({@code /assets/**}, *.js, *.css, *.png, ...) NON sono mappati
 *       e vengono serviti direttamente da Tomcat, senza passare di qui.</li>
 * </ul>
 */
@Log4j2
@Controller
public class SpaController {

    /**
     * Inoltra le rotte note della SPA a index.html.
     * Aggiungere qui eventuali nuove rotte introdotte in React Router.
     */
    @GetMapping({
            "/",
            "/dashboard",
            "/bookings",
            "/bookings/**",
            "/owners",
            "/owners/**",
            "/properties",
            "/properties/**",
            "/users",
            "/ota",
            "/tourist-tax",
            "/tourist-tax/**",
            "/documents",
            "/f24",
            "/settlements",
            "/cu",
            "/audit",
            "/settings",
            "/import/**",
            "/reconciliation",
            "/admin",
            "/admin/**",
            "/owner",
            "/owner/**",
            "/login"
    })
    public String forward() {
        log.debug("[SpaController] forward verso /index.html");
        return "forward:/index.html";
    }
}
