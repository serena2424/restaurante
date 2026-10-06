package com.gestorgastronomico.dto;

import com.gestorgastronomico.entity.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UsuarioRequestDTO {
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 60, message = "El nombre puede tener hasta 60 caracteres")
    private String nombre;

    @Email(message = "El email no es válido")
    @NotBlank(message = "El email es obligatorio")
    @Size(max = 100, message = "El email puede tener hasta 100 caracteres")
    private String email;

    /** Obligatoria al crear. Al editar, vacía significa "no cambiarla". */
    private String password;

    @NotNull(message = "El rol es obligatorio")
    private Rol rol;
}
