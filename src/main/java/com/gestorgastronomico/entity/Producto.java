package com.gestorgastronomico.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(name = "productos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre del producto es obligatorio")
    @Column(nullable = false)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor a 0")
    @Column(nullable = false)
    private Double precio;

    /** Costo de mercadería (opcional). */
    @Column
    private Double costo;

    /** Precio de "Entera" en el esquema viejo Media/Entera. Los productos nuevos usan variantes. */
    @Column
    private Double precioEntera;

    /** Opciones con precio propio, en JSON: [{"nombre":"Media","precio":6000},{"nombre":"Entera","precio":11500}]. */
    @Column(columnDefinition = "TEXT")
    private String variantes;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Column(length = 500)
    private String imagenUrl;

    @NotBlank(message = "La categoría es obligatoria")
    @Column(nullable = false)
    private String categoria;
}
