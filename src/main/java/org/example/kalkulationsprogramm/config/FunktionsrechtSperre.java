package org.example.kalkulationsprogramm.config;

import java.nio.charset.StandardCharsets;

import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.service.FunktionsRechteService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Prüft für die Pfade einer {@link Funktion}, ob der angemeldete Benutzer am PC die Aktion darf (Lesen = Ansehen, POST = Anlegen, PUT und PATCH = Ändern,
 * DELETE = Löschen). Administratoren sind nicht eingeschränkt. Zugriffe ohne Benutzer-Anmeldung (zum Beispiel die Handy-App mit Zugangslink) prüft der
 * jeweilige Endpunkt selbst über {@link FunktionsRechteService#verlangeFuerToken}. Fehlt der Dienst (schlanke Controller-Tests), gilt alles als erlaubt.
 */
public class FunktionsrechtSperre implements HandlerInterceptor {

    private final Funktion funktion;
    private final ObjectProvider<FunktionsRechteService> dienst;

    public FunktionsrechtSperre(Funktion funktion, ObjectProvider<FunktionsRechteService> dienst) {
        this.funktion = funktion;
        this.dienst = dienst;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof FrontendUserPrincipal principal)) {
            return true;
        }
        if (auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            return true;
        }
        FunktionsRechteService service = dienst.getIfAvailable();
        FunktionsAktion aktion = FunktionsAktion.zuHttpMethode(request.getMethod());
        if (service == null || service.darfProfil(principal.getId(), funktion, aktion)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":403,\"message\":\"" + FunktionsRechteService.fehlermeldung(funktion, aktion) + "\"}");
        return false;
    }
}
