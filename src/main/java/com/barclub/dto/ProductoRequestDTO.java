package com.barclub.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductoRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 30, message = "El nombre no puede superar los 30 caracteres")
    private String nombre;

    private String descripcion;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
    @DecimalMax(value = "9999999", message = "El precio no puede superar $9.999.999")
    private Double precio;

    @DecimalMin(value = "0.0", message = "El costo no puede ser negativo")
    private Double costo;

    // Precio de la variante grande (pizza entera). Opcional.
    @DecimalMin(value = "0.0", message = "El precio de la variante no puede ser negativo")
    @DecimalMax(value = "9999999", message = "El precio de la variante no puede superar $9.999.999")
    private Double precioEntera;
    private String variantes;

    private Boolean activo = true;

    private String imagenUrl;

    @NotBlank(message = "La categoría es obligatoria")
    private String categoria;
}
