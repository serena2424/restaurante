package com.barclub.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ClienteRequestDTO {
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;
    private String telefono;
    private String direccion;
}
