package com.gestorgastronomico.service;

import com.gestorgastronomico.entity.ConfigLocal;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class HorarioLocalTest {

    // Lunes 5 de octubre de 2026.
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);

    private final ConfigLocal semana = ConfigLocal.builder().dias("1,2,3,4,5")
            .mDesde("11:00").mHasta("15:00").nDesde("20:00").nHasta("01:00").build();

    @Test
    void abreEnLosTurnosDeLosDiasHabilitados() {
        assertTrue(HorarioLocal.abiertoEn(semana, LUNES.atTime(12, 0)));
        assertTrue(HorarioLocal.abiertoEn(semana, LUNES.atTime(22, 45)));
        assertFalse(HorarioLocal.abiertoEn(semana, LUNES.atTime(16, 0)));
        assertFalse(HorarioLocal.abiertoEn(semana, LUNES.plusDays(6).atTime(12, 0)));
    }

    @Test
    void elTurnoQueCruzaMedianoche_perteneceAlDiaEnQueEmpezo() {
        LocalDateTime martesMadrugada = LUNES.plusDays(1).atTime(0, 30);

        HorarioLocal.Turno turno = HorarioLocal.turnoDe(semana, martesMadrugada).orElseThrow();

        assertEquals(LUNES, turno.inicio());
        assertEquals(1, turno.franja());
    }

    @Test
    void laMadrugadaDelLunes_estaCerradaPorqueElDomingoNoAbre() {
        assertFalse(HorarioLocal.abiertoEn(semana, LUNES.atTime(0, 30)));
    }

    @Test
    void laHoraDeCierre_yaCuentaComoCerrado() {
        assertFalse(HorarioLocal.abiertoEn(semana, LUNES.atTime(15, 0)));
    }

    @Test
    void sinTurnosCargados_abreTodoElDiaEnLosDiasHabilitados() {
        ConfigLocal soloDias = ConfigLocal.builder().dias("1").build();

        assertTrue(HorarioLocal.abiertoEn(soloDias, LUNES.atTime(3, 0)));
        assertFalse(HorarioLocal.abiertoEn(soloDias, LUNES.plusDays(1).atTime(12, 0)));
    }

    @Test
    void validarTurnos_detectaErroresDeCarga() {
        assertNull(HorarioLocal.validarTurnos(semana));
        assertNotNull(HorarioLocal.validarTurnos(ConfigLocal.builder().mDesde("12:00").mHasta("12:00").build()));
        assertNotNull(HorarioLocal.validarTurnos(ConfigLocal.builder().mDesde("12:00").build()));
        assertNotNull(HorarioLocal.validarTurnos(ConfigLocal.builder()
                .mDesde("11:00").mHasta("15:00").nDesde("14:00").nHasta("16:00").build()));
    }
}
