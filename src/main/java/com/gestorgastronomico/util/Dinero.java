package com.gestorgastronomico.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reglas de redondeo de montos. Los precios pueden tener centavos, pero lo que
 * se cobra se redondea al peso entero hacia arriba, línea por línea, para que
 * la suma de las líneas siempre coincida con el total.
 */
public final class Dinero {

    private Dinero() { }

    /** precio × cantidad, redondeado al peso entero hacia arriba. */
    public static double subtotal(double precioUnitario, int cantidad) {
        return BigDecimal.valueOf(precioUnitario)
                .multiply(BigDecimal.valueOf(cantidad))
                .setScale(0, RoundingMode.CEILING)
                .doubleValue();
    }

    /** Monto redondeado al peso entero hacia arriba. */
    public static double alPesoHaciaArriba(double monto) {
        return BigDecimal.valueOf(monto).setScale(0, RoundingMode.CEILING).doubleValue();
    }
}
