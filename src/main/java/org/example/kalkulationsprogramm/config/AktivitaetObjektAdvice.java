package org.example.kalkulationsprogramm.config;

import java.lang.reflect.Method;
import java.util.Map;

import org.example.kalkulationsprogramm.service.AktivitaetKatalog;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Merkt sich bei neu angelegten Objekten die Nummer aus der Antwort, damit das Aktivitätsprotokoll auch bei "Projekt angelegt" sagen kann, welches Projekt
 * gemeint ist (die Adresse nennt es dort noch nicht). Wirkt nur bei Aktionen aus dem {@link AktivitaetKatalog}; die Antwort wird nie verändert.
 */
@RestControllerAdvice
public class AktivitaetObjektAdvice implements ResponseBodyAdvice<Object> {

    /** Name des Request-Attributs, in dem die Nummer des neuen Objekts steht. */
    public static final String ATTRIBUT = AktivitaetObjektAdvice.class.getName() + ".objektId";

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType, Class<? extends HttpMessageConverter<?>> converterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        try {
            if (body != null && request instanceof ServletServerHttpRequest servlet && "POST".equals(servlet.getMethod().name())) {
                HttpServletRequest http = servlet.getServletRequest();
                Object muster = http.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                if (muster instanceof String m && AktivitaetKatalog.finde("POST", m).filter(r -> r.objektTyp() != null).isPresent()) {
                    Long id = idVon(body);
                    if (id != null) {
                        http.setAttribute(ATTRIBUT, id);
                    }
                }
            }
        } catch (RuntimeException ignoriert) {
            // Das Protokoll darf die Antwort nie stören.
        }
        return body;
    }

    static Long idVon(Object body) {
        if (body instanceof Map<?, ?> map) {
            return alsLong(map.get("id"));
        }
        for (String name : new String[] { "getId", "id" }) {
            try {
                Method m = body.getClass().getMethod(name);
                if (m.getParameterCount() == 0) {
                    return alsLong(m.invoke(body));
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                // nächster Versuch
            }
        }
        return null;
    }

    private static Long alsLong(Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }
}
