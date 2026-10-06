package com.gestorgastronomico.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/**
 * Permisos por rol. Las reglas se evalúan en orden: las más específicas van primero.
 * Sin sesión → 401 (el panel vuelve al login). Sin permiso → 403 (el panel avisa y no cierra la sesión).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String CAJERO = "CAJERO";
    private static final String MOZO = "MOZO";
    private static final String COCINA = "COCINA";

    private final JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Se responde directo (sin pasar por /error) para que un 403 no termine convertido en 401.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) ->
                    escribirError(response, HttpStatus.UNAUTHORIZED, "No autenticado"))
                .accessDeniedHandler((request, response, e) ->
                    escribirError(response, HttpStatus.FORBIDDEN, "No tenés permiso para hacer esto con tu usuario")))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                // Página pública
                .requestMatchers(HttpMethod.GET, "/api/productos/activos", "/api/productos/categoria/**",
                        "/api/productos/buscar", "/api/config", "/api/config/estado").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/pedidos", "/api/reservas",
                        "/api/usuarios/login", "/api/usuarios/reset-password").permitAll()
                // El canal en tiempo real solo avisa "algo cambió", sin datos.
                .requestMatchers("/ws/**").permitAll()

                // Administración
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").hasRole(ADMIN)
                .requestMatchers(HttpMethod.GET, "/api/usuarios/me").authenticated()
                .requestMatchers("/api/usuarios/**").hasRole(ADMIN)
                .requestMatchers(HttpMethod.PUT, "/api/config/**").hasRole(ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/config/**").hasRole(ADMIN)
                .requestMatchers(HttpMethod.PATCH, "/api/config/**").hasRole(ADMIN)
                .requestMatchers("/api/backups/**").hasRole(ADMIN)
                .requestMatchers(HttpMethod.DELETE, "/api/ventas/**").hasRole(ADMIN)

                // Plata, reservas y clientes
                .requestMatchers("/api/ventas/**", "/api/dashboard/**", "/api/clientes/**", "/api/reservas/**")
                    .hasAnyRole(ADMIN, CAJERO)

                // Menú: todos lo leen, admin y cajero lo editan
                .requestMatchers(HttpMethod.POST, "/api/productos/**").hasAnyRole(ADMIN, CAJERO)
                .requestMatchers(HttpMethod.PUT, "/api/productos/**").hasAnyRole(ADMIN, CAJERO)
                .requestMatchers(HttpMethod.PATCH, "/api/productos/**").hasAnyRole(ADMIN, CAJERO)
                .requestMatchers(HttpMethod.DELETE, "/api/productos/**").hasAnyRole(ADMIN, CAJERO)

                // Pedidos. El mozo solo ve y edita los suyos (lo controla PedidoService).
                .requestMatchers(HttpMethod.GET, "/api/pedidos", "/api/pedidos/fecha",
                        "/api/pedidos/entregados-desde-cierre").hasAnyRole(ADMIN, CAJERO, MOZO)
                .requestMatchers(HttpMethod.DELETE, "/api/pedidos/*/detalles/*").hasAnyRole(ADMIN, CAJERO, MOZO)
                .requestMatchers(HttpMethod.DELETE, "/api/pedidos/**").hasAnyRole(ADMIN, CAJERO)
                .requestMatchers(HttpMethod.PATCH, "/api/pedidos/*/detalles/*/cantidad",
                        "/api/pedidos/*/datos-cliente").hasAnyRole(ADMIN, CAJERO, MOZO)
                .requestMatchers(HttpMethod.POST, "/api/pedidos/**").hasAnyRole(ADMIN, CAJERO, MOZO)
                .requestMatchers(HttpMethod.PATCH, "/api/pedidos/*/cancelar").hasAnyRole(ADMIN, CAJERO)
                .requestMatchers(HttpMethod.PATCH, "/api/pedidos/*/estado").hasAnyRole(ADMIN, CAJERO, COCINA)

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void escribirError(HttpServletResponse response, HttpStatus status, String mensaje) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":" + status.value()
                + ",\"error\":\"" + mensaje + "\",\"message\":\"" + mensaje + "\"}");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS abierto a cualquier origen: la API se autentica con el token en el
     * header Authorization (no con cookies), así que no hay credenciales que robar.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/ws/**", config);
        return source;
    }
}
