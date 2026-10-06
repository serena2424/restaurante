package com.gestorgastronomico.config;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freno de fuerza bruta del login: después de varios intentos fallidos con el
 * mismo email, lo bloquea unos minutos. El contador vive en memoria (hay una
 * sola instancia del servidor) y se reinicia con el servidor.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_INTENTOS = 5;
    private static final Duration BLOQUEO = Duration.ofMinutes(10);

    private static class Estado {
        int fallosSeguidos = 0;
        Instant bloqueadoHasta = null;
    }

    private final ConcurrentHashMap<String, Estado> porEmail = new ConcurrentHashMap<>();

    private String clave(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Segundos restantes de bloqueo, o 0 si no está bloqueado. */
    public long segundosDeBloqueo(String email) {
        Estado e = porEmail.get(clave(email));
        if (e == null || e.bloqueadoHasta == null) return 0;
        long restante = Duration.between(Instant.now(), e.bloqueadoHasta).getSeconds();
        return Math.max(restante, 0);
    }

    public void registrarFallo(String email) {
        Estado e = porEmail.computeIfAbsent(clave(email), k -> new Estado());
        synchronized (e) {
            e.fallosSeguidos++;
            if (e.fallosSeguidos >= MAX_INTENTOS) {
                e.bloqueadoHasta = Instant.now().plus(BLOQUEO);
                e.fallosSeguidos = 0; // el próximo ciclo de fallos empieza de cero tras el bloqueo
            }
        }
    }

    public void registrarExito(String email) {
        porEmail.remove(clave(email));
    }
}
