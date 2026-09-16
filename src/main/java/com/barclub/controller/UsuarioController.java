package com.barclub.controller;

import com.barclub.config.JwtUtil;
import com.barclub.config.LoginRateLimiter;
import com.barclub.dto.LoginRequestDTO;
import com.barclub.dto.UsuarioRequestDTO;
import com.barclub.dto.UsuarioResponseDTO;
import com.barclub.exception.ErrorResponse;
import com.barclub.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Gestión de usuarios del sistema y autenticación")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final JwtUtil jwtUtil;
    private final LoginRateLimiter loginRateLimiter;

    @GetMapping
    @Operation(summary = "Listar todos los usuarios")
    @ApiResponse(responseCode = "200", description = "Lista de usuarios")
    public ResponseEntity<List<UsuarioResponseDTO>> listarTodos() {
        return ResponseEntity.ok(usuarioService.listarTodos());
    }

    @PostMapping("/login")
    @Operation(summary = "Login de usuario", description = "Valida email y contraseña. Devuelve los datos del usuario y el token JWT.")
    @ApiResponse(responseCode = "200", description = "Login exitoso")
    @ApiResponse(responseCode = "401", description = "Email o contraseña incorrectos",
            content = @Content(schema = @Schema(implementation = Map.class)))
    @ApiResponse(responseCode = "429", description = "Demasiados intentos fallidos, esperar antes de reintentar",
            content = @Content(schema = @Schema(implementation = Map.class)))
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO dto) {
        long bloqueoSeg = loginRateLimiter.segundosDeBloqueo(dto.getEmail());
        if (bloqueoSeg > 0) {
            long minutos = (bloqueoSeg + 59) / 60;
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Demasiados intentos fallidos. Probá de nuevo en " + minutos + " minuto(s)."));
        }
        return usuarioService.login(dto.getEmail(), dto.getPassword())
                .<ResponseEntity<?>>map(usuario -> {
                    loginRateLimiter.registrarExito(dto.getEmail());
                    String token = jwtUtil.generarToken(usuario.getEmail(), usuario.getRol().name());
                    return ResponseEntity.ok(Map.of(
                            "token", token,
                            "usuario", usuario
                    ));
                })
                .orElseGet(() -> {
                    loginRateLimiter.registrarFallo(dto.getEmail());
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", "Email o contraseña incorrectos"));
                });
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener usuario por ID")
    @ApiResponse(responseCode = "200", description = "Usuario encontrado")
    @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<UsuarioResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.obtenerPorId(id));
    }

    @PostMapping
    @Operation(summary = "Crear usuario")
    @ApiResponse(responseCode = "201", description = "Usuario creado")
    @ApiResponse(responseCode = "400", description = "Email ya en uso o datos inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<UsuarioResponseDTO> crear(@Valid @RequestBody UsuarioRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.crear(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar usuario")
    @ApiResponse(responseCode = "200", description = "Usuario actualizado")
    @ApiResponse(responseCode = "400", description = "Email ya en uso",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<UsuarioResponseDTO> actualizar(
            @PathVariable Long id, @Valid @RequestBody UsuarioRequestDTO dto) {
        return ResponseEntity.ok(usuarioService.actualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar usuario")
    @ApiResponse(responseCode = "204", description = "Usuario eliminado")
    @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        usuarioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Restablecer contraseña con clave maestra",
               description = "Valida la clave maestra en el servidor y actualiza la contraseña del usuario.")
    @ApiResponse(responseCode = "200", description = "Contraseña actualizada")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody Map<String, String> body) {
        usuarioService.resetPasswordConClaveMaestra(
                body.get("email"), body.get("claveMaestra"), body.get("nuevaPassword"));
        return ResponseEntity.ok(Map.of("mensaje", "Contraseña actualizada"));
    }
}
