package com.gestorgastronomico.entity;

import com.gestorgastronomico.util.Dinero;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "detalle_pedidos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetallePedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    @Column(nullable = false)
    private Integer cantidad;

    @NotNull(message = "El precio unitario es obligatorio")
    @Column(nullable = false)
    private Double precioUnitario;

    @Column(nullable = false)
    private Double subtotal;

    // Costo del producto al momento del pedido, para que un cambio posterior no altere el historial.
    @Column
    private Double costoUnitario;

    /** Opción elegida: tamaño ("Entera") o acompañamiento ("Puré"). */
    @Column(length = 60)
    private String variante;

    /**
     * Estado de la tarjeta en una comanda de mesa. Null en retiro, delivery y
     * pedidos viejos: ahí manda el estado del pedido.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EstadoPedido estado;

    /** False en las bebidas y en todo lo que no pasa por cocina (lo entrega el mozo). */
    private Boolean vaACocina;

    /** Cuándo se cargó la tarjeta. */
    private LocalDateTime creadoEn;

    /** Último cambio de estado de la tarjeta. */
    private LocalDateTime estadoEn;

    /** Cambio de cantidad después de cargada, para que cocina lo note. Se borra al pasar a LISTO. */
    private LocalDateTime modificadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    public boolean esTarjeta() {
        return estado != null;
    }

    public boolean estaCancelada() {
        return estado == EstadoPedido.CANCELADO;
    }

    public boolean pasaPorCocina() {
        return !Boolean.FALSE.equals(vaACocina);
    }

    @PrePersist
    @PreUpdate
    public void calcularSubtotal() {
        if (cantidad != null && precioUnitario != null) {
            this.subtotal = Dinero.subtotal(precioUnitario, cantidad);
        }
    }
}
