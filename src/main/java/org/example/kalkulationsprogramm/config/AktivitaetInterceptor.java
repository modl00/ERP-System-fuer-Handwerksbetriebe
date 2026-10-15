package org.example.kalkulationsprogramm.config;

import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.service.AktivitaetKatalog;
import org.example.kalkulationsprogramm.service.AktivitaetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Hält erfolgreiche, wichtige Änderungen im Aktivitätsprotokoll fest (Issue #93). Was protokolliert wird, bestimmt allein der {@link AktivitaetKatalog}; ohne
 * angemeldeten Benutzer (zum Beispiel die Handy-App mit Zugangslink) und bei Fehlern wird nichts festgehalten. Ein Fehler beim Protokollieren stört die Aktion nie.
 */
public class AktivitaetInterceptor implements HandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AktivitaetInterceptor.class);
    private static final Set<String> AENDERNDE_METHODEN = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final ObjectProvider<AktivitaetService> dienst;

    public AktivitaetInterceptor(ObjectProvider<AktivitaetService> dienst) {
        this.dienst = dienst;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            if (ex != null || response.getStatus() >= 400 || !AENDERNDE_METHODEN.contains(request.getMethod())) {
                return;
            }
            Object muster = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (!(muster instanceof String m)) {
                return;
            }
            var regel = AktivitaetKatalog.finde(request.getMethod(), m);
            if (regel.isEmpty()) {
                return;
            }
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof FrontendUserPrincipal principal)) {
                return;
            }
            AktivitaetService service = dienst.getIfAvailable();
            if (service == null) {
                return;
            }
            boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            service.erfasse(principal.getId(), principal.getDisplayName(), admin, regel.get(), erstesObjekt(request));
        } catch (RuntimeException e) {
            LOG.debug("Aktivität nicht protokolliert: {}", e.getMessage());
        }
    }

    /**
     * Die erste Nummer in der Adresse ist das Objekt (zum Beispiel die Projektnummer in /api/projekte/7/zeiten); beim Anlegen steht sie noch nicht in der Adresse,
     * dann gilt die Nummer aus der Antwort (siehe {@link AktivitaetObjektAdvice}).
     */
    @SuppressWarnings("unchecked")
    private static Long erstesObjekt(HttpServletRequest request) {
        if (request.getAttribute(AktivitaetObjektAdvice.ATTRIBUT) instanceof Long neu) {
            return neu;
        }
        Object variablen = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(variablen instanceof Map<?, ?> map) || map.isEmpty()) {
            return null;
        }
        Object erste = ((Map<String, String>) map).values().iterator().next();
        try {
            return erste == null ? null : Long.valueOf(erste.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
