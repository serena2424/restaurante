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

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
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