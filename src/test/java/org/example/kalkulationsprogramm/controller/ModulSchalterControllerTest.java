package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.service.Modul;
import org.example.kalkulationsprogramm.service.ModulSchalterService;
import org.example.kalkulationsprogramm.service.wartung.WartungModulSchalterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ModulSchalterControllerTest {

    private final WartungModulSchalterService wartung = mock(WartungModulSchalterService.class);
    private final ModulSchalterService modul = mock(ModulSchalterService.class);
    private MockMvc mvc;

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> bereit(T dienst) {
        ObjectProvider<T> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(dienst);
        return p;
    }

    @BeforeEach
    void setUp() {
        when(wartung.istAktiv()).thenReturn(true);
        when(modul.istAktiv(Modul.MIETE)).thenReturn(true);
        when(modul.istAktiv(Modul.WEBSITE)).thenReturn(true);
        mvc = MockMvcBuilders.standaloneSetup(new ModulSchalterController(wartung, bereit(null), bereit(modul))).build();
    }

    @Test
    void featuresZeigtAlleModule() throws Exception {
        when(modul.istAktiv(Modul.MIETE)).thenReturn(false);

        mvc.perform(get("/api/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wartung").value(true))
                .andExpect(jsonPath("$.lager").value(true))
                .andExpect(jsonPath("$.miete").value(false))
                .andExpect(jsonPath("$.website").value(true));
    }

    @Test
    void mietmodulAusschalten() throws Exception {
        when(modul.istAktiv(Modul.MIETE)).thenReturn(false);

        mvc.perform(put("/api/settings/features/miete").contentType(MediaType.APPLICATION_JSON).content("{\"aktiv\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.miete").value(false));
        verify(modul).setzeAktiv(eq(Modul.MIETE), eq(false));
    }

    @Test
    void ohneAngabeWirdNichtGeschaltet() throws Exception {
        mvc.perform(put("/api/settings/features/miete").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        verify(modul, never()).setzeAktiv(eq(Modul.MIETE), eq(true));
    }

    @Test
    void unbekanntesModulGibt404() throws Exception {
        mvc.perform(put("/api/settings/features/gibtesnicht").contentType(MediaType.APPLICATION_JSON).content("{\"aktiv\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ohneSchalterDienstSindDieModuleAn() throws Exception {
        mvc = MockMvcBuilders.standaloneSetup(new ModulSchalterController(wartung, bereit(null), bereit(null))).build();

        mvc.perform(get("/api/features")).andExpect(jsonPath("$.miete").value(true)).andExpect(jsonPath("$.website").value(true));
        mvc.perform(put("/api/settings/features/miete").contentType(MediaType.APPLICATION_JSON).content("{\"aktiv\":false}")).andExpect(status().isNotFound());
    }
}
