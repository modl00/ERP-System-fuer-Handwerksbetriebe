package org.example.kalkulationsprogramm.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.kalkulationsprogramm.service.wartung.WartungModulSchalterService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * Sperrt {@code /api/wartung/**}, solange das Wartungsmodul in den Einstellungen ausgeschaltet ist. Das gilt für alle,
 * auch für Admins: Ausgeschaltet heißt ausgeschaltet; wieder einschalten geht über die Einstellungen. Die Daten bleiben
 * unberührt. Fehlt der Schalter-Dienst (z. B. in schlanken Controller-Tests), gilt das Modul als eingeschaltet.
 */
public class WartungModulSperre implements HandlerInterceptor {

    private final ObjectProvider<WartungModulSchalterService> schalter;

    public WartungModulSperre(ObjectProvider<WartungModulSchalterService> schalter) {
        this.schalter = schalter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        WartungModulSchalterService dienst = schalter.getIfAvailable();
        if (dienst == null || dienst.istAktiv()) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":403,\"message\":\"Das Wartungsmodul ist ausgeschaltet.\"}");
        return false;
    }
}
