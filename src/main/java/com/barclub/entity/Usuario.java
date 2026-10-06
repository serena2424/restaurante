package com.barclub.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Column(nullable = false)
    private String nombre;

    @Email(message = "El email debe ser válido")
    @NotBlank(message = "El email es obligatorio")
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    // Los usuarios ya no se borran de la base: se DESACTIVAN. Antes había acá
    // una relación con cascada (al borrar un usuario se borraban todos sus
    // pedidos y, con ellos, sus ventas) — borrar a un empleado que se fue
    // hacía desaparecer plata ya cobrada del historial. Un usuario inactivo
    // no puede iniciar sesión, pero todo lo que cargó queda registrado.
    // null = usuario de antes de que existiera este campo → se trata como activo.
    @Builder.Default
    private Boolean activo = true;

    public boolean estaActivo() {
        return !Boolean.FALSE.equals(activo);
    }
}
