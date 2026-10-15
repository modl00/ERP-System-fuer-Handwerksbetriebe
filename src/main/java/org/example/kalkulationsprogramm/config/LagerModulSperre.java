package org.example.kalkulationsprogramm.config;

import java.nio.charset.StandardCharsets;

import org.example.kalkulationsprogramm.service.LagerModulSchalterService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sperrt {@code /api/lager/**}, solange das Lagermodul in den Einstellungen ausgeschaltet ist (für alle, auch Admins; die Daten bleiben unberührt).
 * Fehlt der Schalter-Dienst (zum Beispiel in schlanken Controller-Tests), gilt das Modul als eingeschaltet.
 */
public class LagerModulSperre implements HandlerInterceptor {

    private final ObjectProvider<LagerModulSchalterService> schalter;

    public LagerModulSperre(ObjectProvider<LagerModulSchalterService> schalter) {
        this.schalter = schalter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        LagerModulSchalterService dienst = schalter.getIfAvailable();
        if (dienst == null || dienst.istAktiv()) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":403,\"message\":\"Das Lagermodul ist ausgeschaltet.\"}");
        return false;
    }
}
