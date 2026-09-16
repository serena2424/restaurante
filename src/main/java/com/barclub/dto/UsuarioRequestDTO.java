package com.barclub.dto;

import com.barclub.entity.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// ========================
// USUARIO DTOs
// ========================

@Data
public class UsuarioRequestDTO {
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @Email
    @NotBlank(message = "El email es obligatorio")
    private String email;

    // Obligatoria al crear (validada en el service). En la edición puede venir
    // vacía o ausente: significa "no cambiar la contraseña actual".
    private String password;

    @NotNull(message = "El rol es obligatorio")
    private Rol rol;
}
