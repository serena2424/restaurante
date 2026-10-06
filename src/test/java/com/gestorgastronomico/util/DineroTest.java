package com.gestorgastronomico.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DineroTest {

    @Test
    void subtotal_redondeaAlPesoHaciaArriba() {
        assertEquals(1500.0, Dinero.subtotal(1499.99, 1), 0.0);
        assertEquals(3000.0, Dinero.subtotal(1499.99, 2), 0.0);
        assertEquals(5000.0, Dinero.subtotal(2500.0, 2), 0.0);
    }

    @Test
    void montoEntero_noCambia() {
        assertEquals(12800.0, Dinero.alPesoHaciaArriba(12800.0), 0.0);
        assertEquals(12801.0, Dinero.alPesoHaciaArriba(12800.01), 0.0);
    }
}
