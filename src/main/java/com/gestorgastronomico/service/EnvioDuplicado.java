package com.gestorgastronomico.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detecta el mismo pedido o reserva enviado dos veces en pocos segundos
 * (doble toque en el botón o un reintento de la red).
 */
@Component
public class EnvioDuplicado {

    static final long VENTANA_MS = 8_000;

    private final Map<String, Long> ultimos = new ConcurrentHashMap<>();
    private final Clock clock;

    public EnvioDuplicado(Clock clock) {
        this.clock = clock;
    }

    /** Registra la firma y devuelve true si ya se había recibido dentro de la ventana. */
    public synchronized boolean esRepetido(String firma) {
        long ahora = clock.millis();
        ultimos.values().removeIf(t -> ahora - t > VENTANA_MS);
        Long anterior = ultimos.putIfAbsent(firma, ahora);
        return anterior != null;
    }
}
