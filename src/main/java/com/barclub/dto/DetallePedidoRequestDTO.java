package com.barclub.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DetallePedidoRequestDTO {

    @NotNull(message = "El producto es obligatorio")
    private Long productoId;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad mínima es 1")
    @Max(value = 500, message = "La cantidad máxima por producto es 500")
    private Integer cantidad;

    // Variante elegida: "Entera", "Media", salsa, guarnición, etc.
    private String variante;
}
