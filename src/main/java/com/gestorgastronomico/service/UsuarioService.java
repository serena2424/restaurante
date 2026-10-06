package com.gestorgastronomico.service;

import com.gestorgastronomico.dto.UsuarioRequestDTO;
import com.gestorgastronomico.dto.UsuarioResponseDTO;
import com.gestorgastronomico.entity.Usuario;
import com.gestorgastronomico.entity.Rol;
import com.gestorgastronomico.exception.BusinessException;
import com.gestorgastronomico.exception.ResourceNotFoundException;
import com.gestorgastronomico.repository.UsuarioRepository;
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

    /** Valor de fábrica de la clave maestra (público en el repo): con este valor el reset queda deshabilitado. */
    public static final String MASTER_KEY_DE_FABRICA = "admin2026";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.master-key:" + MASTER_KEY_DE_FABRICA + "}")
    private String masterKey;

    // Límite de intentos con la clave maestra: 5 cada 10 minutos. Es global
    // porque la clave es una sola; el login normal tiene su propio límite por
    // email (LoginRateLimiter).
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

    /** Restablece una contraseña con la clave maestra, que se valida en el servidor. */
    public void resetPasswordConClaveMaestra(String email, String claveMaestra, String nuevaPassword) {
        // Con la clave de fábrica (pública en el repositorio) el reset queda apagado.
        if (masterKey == null || masterKey.isBlank() || MASTER_KEY_DE_FABRICA.equals(masterKey)) {
            throw new BusinessException("La recuperación con clave maestra está desactivada: falta configurar la variable MASTER_KEY en el servidor.");
        }
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
                .filter(Usuario::estaActivo)
                .orElseThrow(() -> new BusinessException("No existe un usuario activo con ese email"));
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
        Optional<Usuario> existente = usuarioRepository.findByEmail(dto.getEmail());
        if (existente.isPresent()) {
            throw new BusinessException(existente.get().estaActivo()
                    ? "Ya existe un usuario con el email: " + dto.getEmail()
                    : "Ese email pertenece a un usuario desactivado. Reactivalo desde la lista en vez de crearlo de nuevo.");
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
                .activo(true)
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
        // No dejar al sistema sin ningún administrador activo por un cambio de rol.
        if (usuario.getRol() == Rol.ADMIN && dto.getRol() != Rol.ADMIN && usuario.estaActivo()
                && usuarioRepository.countActivosByRol(Rol.ADMIN) <= 1) {
            throw new BusinessException("No se puede cambiar el rol del único administrador activo.");
        }

        usuario.setNombre(dto.getNombre());
        usuario.setEmail(dto.getEmail());
        // Contraseña vacía = se mantiene la actual.
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            if (dto.getPassword().length() < 6) {
                throw new BusinessException("La contraseña debe tener al menos 6 caracteres");
            }
            usuario.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        usuario.setRol(dto.getRol());

        return toDTO(usuarioRepository.save(usuario));
    }

    /**
     * Desactiva al usuario: no puede iniciar sesión y su sesión abierta deja de
     * valer, pero sus pedidos y ventas quedan en el historial.
     */
    public void eliminar(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        if (!usuario.estaActivo()) return; // ya estaba desactivado
        // No permitir quedarse sin ningún administrador activo.
        if (usuario.getRol() == Rol.ADMIN && usuarioRepository.countActivosByRol(Rol.ADMIN) <= 1) {
            throw new BusinessException("No se puede desactivar el único administrador del sistema.");
        }
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
    }

    public UsuarioResponseDTO reactivar(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        usuario.setActivo(true);
        return toDTO(usuarioRepository.save(usuario));
    }

    /** Devuelve el usuario si el email y la contraseña son correctos y está activo. */
    @Transactional(readOnly = true)
    public Optional<UsuarioResponseDTO> login(String email, String password) {
        if (email == null || password == null) return Optional.empty();
        return usuarioRepository.findByEmail(email.trim())
                .filter(Usuario::estaActivo)
                .filter(u -> passwordEncoder.matches(password, u.getPassword()))
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Optional<UsuarioResponseDTO> porEmail(String email) {
        if (email == null) return Optional.empty();
        return usuarioRepository.findByEmail(email).filter(Usuario::estaActivo).map(this::toDTO);
    }

    public UsuarioResponseDTO toDTO(Usuario u) {
        return UsuarioResponseDTO.builder()
                .id(u.getId())
                .nombre(u.getNombre())
                .email(u.getEmail())
                .rol(u.getRol())
                .activo(u.estaActivo())
                .build();
    }
}
