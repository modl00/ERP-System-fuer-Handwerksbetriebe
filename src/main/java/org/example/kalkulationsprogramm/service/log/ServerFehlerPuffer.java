package org.example.kalkulationsprogramm.service.log;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Merkt sich die letzten Fehler des Servers im Arbeitsspeicher (#255), damit ein Fehlerbericht aus dem Programm sie mitschicken kann, auch im Docker-Betrieb ohne Logdatei.
 * Gemerkt werden nur Meldungen ab {@code ERROR}, höchstens {@value #MAX} und nur kurz (Meldung gekürzt, Fehlerart, die ersten Stellen im eigenen Code). Nichts davon wird
 * auf die Festplatte geschrieben oder ohne Bestätigung eines Administrators weitergegeben.
 */
@Component
public class ServerFehlerPuffer {

    static final int MAX = 200;
    private static final int MAX_MELDUNG = 400;
    private static final int MAX_STELLEN = 5;

    /** Ein gemerkter Fehler. {@code stellen} sind Stellen im eigenen Code (org.example), neueste zuerst. */
    public record Eintrag(Instant zeit, String logger, String meldung, String fehlerart, List<String> stellen) { }

    private final Deque<Eintrag> eintraege = new ConcurrentLinkedDeque<>();
    private final AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
        @Override
        protected void append(ILoggingEvent e) {
            if (e.getLevel().isGreaterOrEqual(Level.ERROR)) {
                merke(aus(e));
            }
        }
    };

    @PostConstruct
    void anmelden() {
        if (LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext kontext) {
            appender.setContext(kontext);
            appender.setName("ServerFehlerPuffer");
            appender.start();
            Logger root = kontext.getLogger(Logger.ROOT_LOGGER_NAME);
            root.addAppender(appender);
        }
    }

    @PreDestroy
    void abmelden() {
        if (LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext kontext) {
            kontext.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
        }
        appender.stop();
    }

    void merke(Eintrag e) {
        eintraege.addLast(e);
        while (eintraege.size() > MAX) {
            eintraege.pollFirst();
        }
    }

    /** Die letzten Fehler, älteste zuerst; höchstens {@code anzahl}, nicht älter als {@code seit}. */
    public List<Eintrag> letzte(int anzahl, Instant seit) {
        List<Eintrag> alle = new ArrayList<>(eintraege);
        List<Eintrag> jung = new ArrayList<>();
        for (Eintrag e : alle) {
            if (seit == null || !e.zeit().isBefore(seit)) {
                jung.add(e);
            }
        }
        int von = Math.max(0, jung.size() - Math.max(0, anzahl));
        return jung.subList(von, jung.size());
    }

    static Eintrag aus(ILoggingEvent e) {
        String meldung = kuerze(e.getFormattedMessage(), MAX_MELDUNG);
        IThrowableProxy t = e.getThrowableProxy();
        String art = t == null ? null : t.getClassName();
        List<String> stellen = new ArrayList<>();
        if (t != null && t.getStackTraceElementProxyArray() != null) {
            for (StackTraceElementProxy s : t.getStackTraceElementProxyArray()) {
                String zeile = s.getStackTraceElement().toString();
                if (zeile.startsWith("org.example.")) {
                    stellen.add(zeile);
                    if (stellen.size() >= MAX_STELLEN) {
                        break;
                    }
                }
            }
        }
        String logger = e.getLoggerName();
        return new Eintrag(Instant.ofEpochMilli(e.getTimeStamp()), logger == null ? "" : logger.substring(Math.max(0, logger.lastIndexOf('.') + 1)), meldung, art, stellen);
    }

    private static String kuerze(String s, int max) {
        if (s == null) {
            return "";
        }
        String einzeilig = s.replaceAll("\\s+", " ").strip();
        return einzeilig.length() <= max ? einzeilig : einzeilig.substring(0, max) + " …";
    }
}
