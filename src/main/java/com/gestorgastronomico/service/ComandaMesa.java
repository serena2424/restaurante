package com.gestorgastronomico.service;

import com.gestorgastronomico.entity.DetallePedido;
import com.gestorgastronomico.entity.EstadoPedido;
import com.gestorgastronomico.entity.Pedido;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reglas de la comanda de mesa por tarjetas: una mesa tiene una sola comanda
 * abierta y cada producto es una tarjeta con su propio estado.
 */
public final class ComandaMesa {

    /** Palabras que marcan una categoría como bebida mientras el admin no configure nada. */
    private static final List<String> PALABRAS_BEBIDA = List.of(
            "bebida", "trago", "gaseosa", "cerveza", "vino", "jugo", "cafe", "licuado", "coctel",
            "agua", "soda", "licor", "whisky", "aperitivo", "fernet", "vermut");

    private ComandaMesa() {
    }

    /**
     * El estado del pedido sale de sus tarjetas, para que el resto del sistema
     * (cobro, informes, listados) siga funcionando igual:
     * PENDIENTE si cocina tiene algo nuevo, PREPARACION si está cocinando,
     * LISTO si no queda nada en cocina, ENTREGADO cuando está cobrada y todo
     * entregado, CANCELADO si se cancelaron todas las tarjetas.
     */
    public static void recalcularEstado(Pedido pedido, LocalDateTime ahora) {
        if (!pedido.esComandaPorTarjetas() || pedido.getEstado() == EstadoPedido.CANCELADO) return;
        List<DetallePedido> activas = pedido.getDetalles().stream().filter(d -> d.esTarjeta() && !d.estaCancelada()).toList();

        EstadoPedido nuevo;
        if (activas.isEmpty()) {
            nuevo = pedido.estaPagado() ? EstadoPedido.ENTREGADO : EstadoPedido.CANCELADO;
        } else if (activas.stream().anyMatch(d -> d.pasaPorCocina() && d.getEstado() == EstadoPedido.PENDIENTE)) {
            nuevo = EstadoPedido.PENDIENTE;
        } else if (activas.stream().anyMatch(d -> d.getEstado() == EstadoPedido.PREPARACION)) {
            nuevo = EstadoPedido.PREPARACION;
        } else if (pedido.estaPagado() && activas.stream().allMatch(d -> d.getEstado() == EstadoPedido.ENTREGADO)) {
            nuevo = EstadoPedido.ENTREGADO;
        } else {
            nuevo = EstadoPedido.LISTO;
        }

        if (nuevo == pedido.getEstado()) return;
        pedido.setEstado(nuevo);
        if (nuevo == EstadoPedido.ENTREGADO) pedido.setEntregadoEn(ahora);
        if (nuevo == EstadoPedido.CANCELADO) pedido.setModificadoEn(ahora);
    }

    /** Lo que queda sin entregar (para avisar al cobrar). */
    public static List<DetallePedido> sinEntregar(Pedido pedido) {
        return pedido.getDetalles().stream()
                .filter(d -> d.esTarjeta() && !d.estaCancelada() && d.getEstado() != EstadoPedido.ENTREGADO)
                .toList();
    }

    /**
     * Si una categoría pasa por cocina. Con la lista configurada manda la lista;
     * sin configurar, se deduce por el nombre (Bebidas, Tragos, Cervezas...).
     */
    public static boolean categoriaVaACocina(String categoria, String categoriasSinCocina) {
        if (categoria == null) return true;
        String cat = normalizar(categoria);
        if (categoriasSinCocina == null) {
            return PALABRAS_BEBIDA.stream().noneMatch(cat::contains);
        }
        return !leerLista(categoriasSinCocina).contains(cat);
    }

    /** Lista guardada → nombres normalizados (sin tildes, minúsculas, sin espacios de más). */
    public static Set<String> leerLista(String texto) {
        if (texto == null || texto.isBlank()) return Set.of();
        return Arrays.stream(texto.split(","))
                .map(ComandaMesa::normalizar)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
