package com.gestorgastronomico.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final com.gestorgastronomico.repository.UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.esValido(token)) {
                String email = jwtUtil.extraerEmail(token);
                // Rol y estado salen de la base en cada pedido: desactivar a alguien o cambiarle el rol aplica al instante.
                usuarioRepository.findByEmail(email)
                        .filter(com.gestorgastronomico.entity.Usuario::estaActivo)
                        .ifPresent(u -> {
                            var auth = new UsernamePasswordAuthenticationToken(
                                    u.getEmail(), null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + u.getRol().name()))
                            );
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        });
            }
        }

        filterChain.doFilter(request, response);
    }
}
