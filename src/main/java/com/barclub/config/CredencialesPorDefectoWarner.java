package com.barclub.config;

import com.barclub.repository.UsuarioRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Aviso de arranque (solo en el log, no bloquea nada) cuando el sistema sigue
 * usando las contraseñas/claves de fábrica en vez de las variables de entorno
 * (JWT_SECRET, MASTER_KEY, DB_PASSWORD), o cuando alguno de los 4 usuarios
 * semilla (admin/cajero/cocina/mozo @miapp.com) todavía tiene la contraseña
 * con la que los crea DataInitializer. Pensado para instalaciones que van a
 * un cliente real: sirve para no olvidarse de cambiarlas antes de entregar.
 *
 * No cambia ningún valor ni comportamiento existente, ni loguea ninguna
 * contraseña — solo compara con BCrypt y deja constancia en el log al
 * iniciar. Development/uso local sigue funcionando exactamente igual.
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

    // email de fábrica -> contraseña de fábrica (las mismas que carga DataInitializer)
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
            // Si la tabla de usuarios no existe todavía (primer arranque antes del
            // DataInitializer) no hace falta romper nada por esto.
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
