package com.gestorgastronomico.service;

import com.gestorgastronomico.dto.UsuarioRequestDTO;
import com.gestorgastronomico.dto.UsuarioResponseDTO;
import com.gestorgastronomico.entity.Rol;
import com.gestorgastronomico.entity.Usuario;
import com.gestorgastronomico.exception.BusinessException;
import com.gestorgastronomico.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private UsuarioService usuarioService;
    private Usuario prueba;

    @BeforeEach
    void setUp() {
        usuarioService = new UsuarioService(usuarioRepository, passwordEncoder);
        prueba = Usuario.builder().id(6L).nombre("Prueba").email("Prueba@miapp.com").rol(Rol.CAJERO).activo(true).build();
        lenient().when(usuarioRepository.findById(6L)).thenReturn(Optional.of(prueba));
    }

    @Test
    void editarUsuarioConMayusculasEnElEmail_noLoConfundeConOtro() {
        // La base compara sin mayúsculas, así que devuelve al mismo usuario.
        when(usuarioRepository.findByEmail("prueba@miapp.com")).thenReturn(Optional.of(prueba));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponseDTO editado = usuarioService.actualizar(6L, pedido("Prueba QA", "Prueba@miapp.com"));

        assertEquals("Prueba QA", editado.getNombre());
        assertEquals("prueba@miapp.com", editado.getEmail());
    }

    @Test
    void emailDeOtroUsuario_seRechaza() {
        Usuario mozo = Usuario.builder().id(4L).email("mozo@miapp.com").rol(Rol.MOZO).activo(true).build();
        when(usuarioRepository.findByEmail("mozo@miapp.com")).thenReturn(Optional.of(mozo));

        BusinessException error = assertThrows(BusinessException.class,
                () -> usuarioService.actualizar(6L, pedido("Prueba", "Mozo@miapp.com")));

        assertEquals("El email mozo@miapp.com ya está en uso", error.getMessage());
    }

    @Test
    void normalizarEmail_sacaEspaciosYMayusculas() {
        assertEquals("juan@x.com", UsuarioService.normalizarEmail("  Juan@X.com "));
        assertNull(UsuarioService.normalizarEmail(null));
    }

    private static UsuarioRequestDTO pedido(String nombre, String email) {
        UsuarioRequestDTO dto = new UsuarioRequestDTO();
        dto.setNombre(nombre);
        dto.setEmail(email);
        dto.setRol(Rol.CAJERO);
        return dto;
    }
}
