package com.barclub.config;

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
    private final com.barclub.repository.UsuarioRepository usuarioRepository;

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
                // El rol y el estado se leen de la BASE en cada pedido, no del
                // token: así, si el admin desactiva a un usuario o le cambia el
                // rol, la sesión que ese usuario tenga abierta deja de valer (o
                // pasa a tener el rol nuevo) al instante, en vez de seguir
                // funcionando con los permisos viejos hasta que venza el token.
                usuarioRepository.findByEmail(email)
                        .filter(com.barclub.entity.Usuario::estaActivo)
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
