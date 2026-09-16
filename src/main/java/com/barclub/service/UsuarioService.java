package com.barclub.service;

import com.barclub.dto.UsuarioRequestDTO;
import com.barclub.dto.UsuarioResponseDTO;
import com.barclub.entity.Usuario;
import com.barclub.entity.Rol;
import com.barclub.exception.BusinessException;
import com.barclub.exception.ResourceNotFoundException;
import com.barclub.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.master-key:admin2026}")
    private String masterKey;

    /**
     * Restablece la contraseña de un usuario validando la clave maestra.
     * La clave se verifica ACÁ, en el servidor: nunca viaja al HTML del panel.
     */
    // Protección contra fuerza bruta: máx. 5 claves incorrectas cada 10 minutos
    private static final int MAX_INTENTOS = 5;
    private static final long VENTANA_MS = 10 * 60 * 1000L;
    private int intentosFallidos = 0;
    private long ventanaInicio = 0;

    private synchronized void controlarIntentos() {
        long ahora = System.currentTimeMillis();
        if (ahora - ventanaInicio > VENTANA_MS) { ventanaInicio = ahora; intentosFallidos = 0; }
        if (intentosFallidos >= MAX_INTENTOS) {
            throw new BusinessException("Demasiados intentos fallidos. Esperá unos minutos y volvé a intentar.");
        }
    }
    private synchronized void registrarFallo() { intentosFallidos++; }
    private synchronized void limpiarIntentos() { intentosFallidos = 0; }

    public void resetPasswordConClaveMaestra(String email, String claveMaestra, String nuevaPassword) {
        controlarIntentos();
        // Comparación en tiempo constante para no filtrar información por timing
        boolean claveOk = claveMaestra != null && MessageDigest.isEqual(
                claveMaestra.getBytes(StandardCharsets.UTF_8),
                masterKey.getBytes(StandardCharsets.UTF_8));
        if (!claveOk) {
            registrarFallo();
            throw new BusinessException("Clave maestra incorrecta");
        }
        limpiarIntentos();
        if (email == null || email.isBlank()) {
            throw new BusinessException("Ingresá el email del usuario");
        }
        if (nuevaPassword == null || nuevaPassword.length() < 6) {
            throw new BusinessException("La contraseña debe tener al menos 6 caracteres");
        }
        Usuario usuario = usuarioRepository.findByEmail(email.trim())
                .orElseThrow(() -> new BusinessException("No existe un usuario con ese email"));
        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuarioRepository.save(usuario);
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listarTodos() {
        return usuarioRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO obtenerPorId(Long id) {
        return toDTO(usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id)));
    }

    public UsuarioResponseDTO crear(UsuarioRequestDTO dto) {
        if (usuarioRepository.existsByEmail(dto.getEmail())) {
            throw new BusinessException("Ya existe un usuario con el email: " + dto.getEmail());
        }
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new BusinessException("La contraseña es obligatoria para crear un usuario");
        }
        if (dto.getPassword().length() < 6) {
            throw new BusinessException("La contraseña debe tener al menos 6 caracteres");
        }
        Usuario usuario = Usuario.builder()
                .nombre(dto.getNombre())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .rol(dto.getRol())
                .build();
        return toDTO(usuarioRepository.save(usuario));
    }

    public UsuarioResponseDTO actualizar(Long id, UsuarioRequestDTO dto) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));

        // Si cambia el email, verificar que no esté en uso
        if (!usuario.getEmail().equals(dto.getEmail())
                && usuarioRepository.existsByEmail(dto.getEmail())) {
            throw new BusinessException("El email " + dto.getEmail() + " ya está en uso");
        }

        usuario.setNombre(dto.getNombre());
        usuario.setEmail(dto.getEmail());
        // Contraseña vacía o ausente = mantener la actual (permite editar nombre/rol sin tocarla)
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            if (dto.getPassword().length() < 6) {
                throw new BusinessException("La contraseña debe tener al menos 6 caracteres");
            }
            usuario.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        usuario.setRol(dto.getRol());
        return toDTO(usuarioRepository.save(usuario));
    }

    public void eliminar(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        // No permitir quedarse sin ningún administrador.
        if (usuario.getRol() == Rol.ADMIN && usuarioRepository.countByRol(Rol.ADMIN) <= 1) {
            throw new BusinessException("No se puede eliminar el único administrador del sistema.");
        }
        usuarioRepository.deleteById(id);
    }

    // Verifica email + contraseña. Devuelve el usuario solo si las credenciales son correctas.
    @Transactional(readOnly = true)
    public Optional<UsuarioResponseDTO> login(String email, String password) {
        if (email == null || password == null) return Optional.empty();
        // Freno de fuerza bruta: mismo control de intentos que el reset de contraseña.
        controlarIntentos();
        Optional<UsuarioResponseDTO> res = usuarioRepository.findByEmail(email.trim())
                .filter(u -> passwordEncoder.matches(password, u.getPassword()))
                .map(this::toDTO);
        if (res.isPresent()) { limpiarIntentos(); } else { registrarFallo(); }
        return res;
    }

    public UsuarioResponseDTO toDTO(Usuario u) {
        return UsuarioResponseDTO.builder()
                .id(u.getId())
                .nombre(u.getNombre())
                .email(u.getEmail())
                .rol(u.getRol())
                .build();
    }
}
