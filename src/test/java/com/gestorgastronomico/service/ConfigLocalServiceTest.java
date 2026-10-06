package com.gestorgastronomico.service;

import com.gestorgastronomico.entity.ConfigLocal;
import com.gestorgastronomico.exception.BusinessException;
import com.gestorgastronomico.repository.ConfigLocalRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ConfigLocalServiceTest {

    private final ConfigLocalService service = new ConfigLocalService(mock(ConfigLocalRepository.class));

    @Test
    void configuracionCorrecta_pasaLaValidacion() {
        assertDoesNotThrow(() -> service.validar(valida().build()));
    }

    @Test
    void envioNegativo_seRechaza() {
        assertThrows(BusinessException.class, () -> service.validar(valida().costoDelivery(-500).build()));
    }

    @Test
    void nombreVacioOLargo_seRechaza() {
        assertThrows(BusinessException.class, () -> service.validar(valida().nombre("  ").build()));
        assertThrows(BusinessException.class, () -> service.validar(valida().nombre("x".repeat(61)).build()));
    }

    @Test
    void sinNingunMedioDePagoValido_seRechaza() {
        assertThrows(BusinessException.class, () -> service.validar(valida().pagosAceptados("CHEQUE").build()));
        assertDoesNotThrow(() -> service.validar(valida().pagosAceptados("").build()));
    }

    @Test
    void turnosQueSePisan_seRechazan() {
        assertThrows(BusinessException.class,
                () -> service.validar(valida().nDesde("14:00").nHasta("16:00").build()));
    }

    private static ConfigLocal.ConfigLocalBuilder valida() {
        return ConfigLocal.builder().nombre("La Esquina").costoDelivery(800)
                .pagosAceptados("EFECTIVO,TRANSFERENCIA")
                .mDesde("11:00").mHasta("15:00").nDesde("20:00").nHasta("01:00");
    }
}
