package com.barclub.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    /** Valor de fábrica de jwt.secret (público en el repo). */
    public static final String JWT_SECRET_DE_FABRICA = "CambiameEnProduccionSecretJWTGenericoDelSistema2026";

    private SecretKey key;

    // Si el servidor no tiene JWT_SECRET configurado, el secreto sería el de
    // fábrica, que está en el código público: cualquiera podría fabricarse un
    // token de ADMIN. En ese caso se genera uno al azar en cada arranque (lo
    // único que cambia es que, al reiniciar el servidor, hay que volver a
    // iniciar sesión). Configurando JWT_SECRET en Railway las sesiones
    // sobreviven a los reinicios.
    @jakarta.annotation.PostConstruct
    void inicializarClave() {
        if (secret == null || secret.isBlank() || JWT_SECRET_DE_FABRICA.equals(secret) || secret.getBytes().length < 32) {
            byte[] azar = new byte[64];
            new java.security.SecureRandom().nextBytes(azar);
            key = Keys.hmacShaKeyFor(azar);
            logger.warn("JWT_SECRET no configurado (o demasiado corto): se usa una clave aleatoria. Las sesiones se cierran en cada reinicio del servidor. Definí JWT_SECRET (32+ caracteres) en las variables de entorno.");
        } else {
            key = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private SecretKey getKey() {
        return key;
    }

    public String generarToken(String email, String rol) {
        return Jwts.builder()
                .subject(email)
                .claim("rol", rol)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getKey())
                .compact();
    }

    public String extraerEmail(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public String extraerRol(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("rol", String.class);
    }

    // Antes esto tragaba cualquier excepción sin dejar rastro — si un token
    // se rechazaba (firma que no coincide, vencido, malformado) no había
    // forma de saber cuál de esas tres cosas fue sin poder reproducirlo en
    // el momento. Ahora cada motivo de rechazo queda en el log de Railway,
    // así la próxima vez que pase se puede ver la causa exacta después.
    public boolean esValido(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            logger.warn("JWT rechazado (vencido): sub={}, exp={}", e.getClaims().getSubject(), e.getClaims().getExpiration());
            return false;
        } catch (io.jsonwebtoken.security.SignatureException e) {
            logger.warn("JWT rechazado (firma no coincide — la clave usada para firmar es distinta a la actual): {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warn("JWT rechazado ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }
}