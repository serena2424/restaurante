package com.gestorgastronomico.service;

import com.gestorgastronomico.entity.ConfigLocal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Interpreta los horarios de atención cargados en la configuración: días
 * habilitados (0 = domingo … 6 = sábado, igual que en el panel) y hasta dos
 * turnos. El turno de la noche puede cruzar la medianoche (20:00 a 01:00).
 * Es la misma regla que usan la web y el panel para mostrar ABIERTO/CERRADO.
 */
public final class HorarioLocal {

    /** Turno en el que cae un momento: fecha en que empezó y número de franja (0 o 1). */
    public record Turno(LocalDate inicio, int franja) { }

    private record Franja(int desde, int hasta) {
        boolean cruzaMedianoche() { return hasta < desde; }
    }

    private HorarioLocal() { }

    public static boolean tieneFranjas(ConfigLocal cfg) {
        return !franjas(cfg).isEmpty();
    }

    public static boolean abiertoEn(ConfigLocal cfg, LocalDateTime momento) {
        return turnoDe(cfg, momento).isPresent();
    }

    /**
     * Devuelve el turno que contiene el momento indicado, o vacío si el local
     * está cerrado a esa hora. Sin turnos cargados, cada día habilitado cuenta
     * como un único turno.
     */
    public static Optional<Turno> turnoDe(ConfigLocal cfg, LocalDateTime momento) {
        Set<Integer> dias = dias(cfg.getDias());
        LocalDate hoy = momento.toLocalDate();
        LocalDate ayer = hoy.minusDays(1);
        int minutos = momento.getHour() * 60 + momento.getMinute();

        List<Franja> franjas = franjas(cfg);
        if (franjas.isEmpty()) {
            return habil(dias, hoy) ? Optional.of(new Turno(hoy, 0)) : Optional.empty();
        }
        for (int i = 0; i < franjas.size(); i++) {
            Franja f = franjas.get(i);
            if (!f.cruzaMedianoche()) {
                if (habil(dias, hoy) && minutos >= f.desde() && minutos < f.hasta()) {
                    return Optional.of(new Turno(hoy, i));
                }
            } else {
                if (habil(dias, hoy) && minutos >= f.desde()) return Optional.of(new Turno(hoy, i));
                if (habil(dias, ayer) && minutos < f.hasta()) return Optional.of(new Turno(ayer, i));
            }
        }
        return Optional.empty();
    }

    /** Valida los turnos antes de guardar la configuración. Devuelve el error o null si están bien. */
    public static String validarTurnos(ConfigLocal cfg) {
        String errorMediodia = validarFranja("mediodía", cfg.getMDesde(), cfg.getMHasta());
        if (errorMediodia != null) return errorMediodia;
        String errorNoche = validarFranja("noche", cfg.getNDesde(), cfg.getNHasta());
        if (errorNoche != null) return errorNoche;

        Integer md = minutos(cfg.getMDesde()), mh = minutos(cfg.getMHasta());
        Integer nd = minutos(cfg.getNDesde()), nh = minutos(cfg.getNHasta());
        if (md == null || nd == null) return null;
        if (mh < md) return "El turno del mediodía no puede terminar al día siguiente.";
        int finNoche = nh < nd ? nh + 1440 : nh;
        boolean sePisan = md < finNoche && nd < mh;
        return sePisan ? "Los turnos del mediodía y de la noche se pisan." : null;
    }

    private static String validarFranja(String nombre, String desde, String hasta) {
        boolean hayDesde = desde != null && !desde.isBlank();
        boolean hayHasta = hasta != null && !hasta.isBlank();
        if (!hayDesde && !hayHasta) return null;
        if (!hayDesde || !hayHasta) return "Completá la hora de apertura y de cierre del turno " + nombre + ".";
        Integer d = minutos(desde), h = minutos(hasta);
        if (d == null || h == null) return "La hora del turno " + nombre + " no es válida.";
        if (d.equals(h)) return "El turno " + nombre + " abre y cierra a la misma hora.";
        return null;
    }

    private static List<Franja> franjas(ConfigLocal cfg) {
        List<Franja> lista = new ArrayList<>();
        agregar(lista, cfg.getMDesde(), cfg.getMHasta());
        agregar(lista, cfg.getNDesde(), cfg.getNHasta());
        return lista;
    }

    private static void agregar(List<Franja> lista, String desde, String hasta) {
        Integer d = minutos(desde), h = minutos(hasta);
        if (d != null && h != null && !d.equals(h)) lista.add(new Franja(d, h));
    }

    static Integer minutos(String hora) {
        if (hora == null || hora.isBlank()) return null;
        try {
            LocalTime t = LocalTime.parse(hora.trim().length() == 4 ? "0" + hora.trim() : hora.trim());
            return t.getHour() * 60 + t.getMinute();
        } catch (Exception e) {
            return null;
        }
    }

    private static Set<Integer> dias(String texto) {
        Set<Integer> dias = new HashSet<>();
        if (texto == null || texto.isBlank()) return dias;
        for (String parte : texto.split(",")) {
            try {
                int n = Integer.parseInt(parte.trim());
                if (n >= 0 && n <= 6) dias.add(n);
            } catch (NumberFormatException ignorado) {
                // valor suelto que no es un día: se ignora, igual que en la web
            }
        }
        return dias;
    }

    private static boolean habil(Set<Integer> dias, LocalDate fecha) {
        return dias.isEmpty() || dias.contains(fecha.getDayOfWeek().getValue() % 7);
    }
}
