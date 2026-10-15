package org.example.kalkulationsprogramm.config;

import java.nio.charset.StandardCharsets;

import org.example.kalkulationsprogramm.service.Modul;
import org.example.kalkulationsprogramm.service.ModulSchalterService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sperrt die Pfade eines Moduls, solange es in den Einstellungen ausgeschaltet ist (für alle, auch Admins; die Daten bleiben unberührt).
 * Fehlt der Schalter-Dienst (zum Beispiel in schlanken Controller-Tests), gilt das Modul als eingeschaltet.
 */
public class ModulSperre implements HandlerInterceptor {

    private final Modul modul;
    private final ObjectProvider<ModulSchalterService> schalter;

    public ModulSperre(Modul modul, ObjectProvider<ModulSchalterService> schalter) {
        this.modul = modul;
        this.schalter = schalter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        ModulSchalterService dienst = schalter.getIfAvailable();
        if (dienst == null || dienst.istAktiv(modul)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":403,\"message\":\"" + modul.getAnzeige() + " ist ausgeschaltet.\"}");
        return false;
    }
}
