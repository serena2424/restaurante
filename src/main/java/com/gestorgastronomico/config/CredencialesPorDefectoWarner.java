package com.gestorgastronomico.config;

import com.gestorgastronomico.repository.UsuarioRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, deja un aviso en el log si siguen las claves de fábrica
 * (JWT_SECRET, MASTER_KEY, DB_PASSWORD) o si algún usuario inicial conserva
 * su contraseña de fábrica. No cambia nada ni muestra contraseñas.
 */
@Component
public class CredencialesPorDefectoWarner {

    private static final Logger log = LoggerFactory.getLogger(CredencialesPorDefectoWarner.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${app.master-key}")
    private String masterKey;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public CredencialesPorDefectoWarner(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private static final String JWT_SECRET_DEFAULT = "CambiameEnProduccionSecretJWTGenericoDelSistema2026";
    private static final String MASTER_KEY_DEFAULT = "admin2026";

    // email -> contraseña con la que los crea DataInitializer
    private static final String[][] USUARIOS_DEFAULT = {
        {"admin@miapp.com", "admin123"},
        {"cajero@miapp.com", "cajero123"},
        {"cocina@miapp.com", "cocina123"},
        {"mozo@miapp.com", "mozo123"},
    };

    @PostConstruct
    public void avisarSiHayValoresPorDefecto() {
        boolean hayDefaults = false;
        StringBuilder detalle = new StringBuilder();

        if (JWT_SECRET_DEFAULT.equals(jwtSecret)) {
            detalle.append("\n  - JWT_SECRET: usando el valor de fábrica (definir la variable de entorno JWT_SECRET)");
            hayDefaults = true;
        }
        if (MASTER_KEY_DEFAULT.equals(masterKey)) {
            detalle.append("\n  - MASTER_KEY: usando el valor de fábrica (definir la variable de entorno MASTER_KEY)");
            hayDefaults = true;
        }
        if (dbPassword == null || dbPassword.isBlank()) {
            detalle.append("\n  - DB_PASSWORD: no está definida");
            hayDefaults = true;
        }

        try {
            for (String[] par : USUARIOS_DEFAULT) {
                usuarioRepository.findByEmail(par[0]).ifPresent(u -> {
                    if (passwordEncoder.matches(par[1], u.getPassword())) {
                        detalle.append("\n  - Usuario ").append(par[0])
                                .append(": todavía tiene la contraseña de fábrica");
                    }
                });
            }
        } catch (Exception e) {
            // En el primer arranque la tabla todavía puede no existir.
            log.debug("No se pudo chequear contraseñas de usuarios por defecto todavía: {}", e.getMessage());
        }
        if (detalle.toString().contains("contraseña de fábrica")) hayDefaults = true;

        if (hayDefaults) {
            log.warn("=====================================================================");
            log.warn("ATENCIÓN: este sistema está corriendo con credenciales de fábrica:{}", detalle);
            log.warn("Antes de entregarlo a un cliente real, definí las variables de entorno");
            log.warn("correspondientes y cambiá la contraseña de cualquier usuario semilla");
            log.warn("que siga apareciendo arriba (cada instalación debería tener las suyas).");
            log.warn("Para uso local/desarrollo esto no es un problema.");
            log.warn("=====================================================================");
        }
    }
}
