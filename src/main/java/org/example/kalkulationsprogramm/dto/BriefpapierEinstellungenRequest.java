package org.example.kalkulationsprogramm.dto;

import jakarta.validation.constraints.NotNull;

/** Setzt nur das briefpapierEnthaeltKopf-Flag der Firmeninformation. */
public class BriefpapierEinstellungenRequest {

    @NotNull
    private Boolean briefpapierEnthaeltKopf;

    public Boolean getBriefpapierEnthaeltKopf() { return briefpapierEnthaeltKopf; }
    public void setBriefpapierEnthaeltKopf(Boolean briefpapierEnthaeltKopf) {
        this.briefpapierEnthaeltKopf = briefpapierEnthaeltKopf;
    }
}
