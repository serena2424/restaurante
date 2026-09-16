package com.barclub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductoResponseDTO {
    private Long id;
    private String nombre;
    private String descripcion;
    private Double precio;
    private Double costo;
    private Double precioEntera;
    private String variantes;
    private Boolean activo;
    private String imagenUrl;
    private String categoria;
}
