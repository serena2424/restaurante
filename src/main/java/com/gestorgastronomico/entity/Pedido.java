package com.gestorgastronomico.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedidos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "DATE")
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime hora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EstadoPedido estado = EstadoPedido.PENDIENTE;

    /** Cuándo pasó a ENTREGADO. */
    @Column
    private LocalDateTime entregadoEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoPedido tipo;

    /** Productos + envío. */
    @Column(nullable = false)
    @Builder.Default
    private Double total = 0.0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Cliente cliente;

    /**
     * Usuario responsable. Los pedidos de la web quedan a nombre del primer admin.
     * Sin cascada: desactivar un usuario no toca sus pedidos ni sus ventas.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Usuario usuario;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<DetallePedido> detalles = new ArrayList<>();

    /** Cobro del pedido. Si existe, el pedido está pagado. */
    @OneToOne(mappedBy = "pedido", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Venta venta;

    private LocalTime horarioEntrega;
    private String nombreCliente;
    private String telefonoCliente;
    private String direccionEntrega;

    @Column(length = 20)
    private String mesa;

    /** Quién lo cargó desde el panel. Null en los pedidos hechos desde la web. */
    private Long creadoPorUsuarioId;
    private String creadoPorNombre;

    /**
     * Última edición desde que el pedido entró o pasó a LISTO. En un pedido
     * cancelado guarda el momento de la cancelación.
     */
    private LocalDateTime modificadoEn;

    /**
     * Productos antes de la primera edición (JSON: nombre, categoría, variante,
     * cantidad). Cocina lo usa para marcar qué cambió. Se borra al pasar a LISTO.
     */
    @Column(columnDefinition = "TEXT")
    private String detalleSnapshotAntesEdicion;

    /** Medio de pago que eligió el cliente al pedir. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MetodoPago metodoPagoPreferido;

    /** Envío cobrado (0 en retiro y en el local). */
    @Column
    private Double costoEnvio;

    public boolean estaPagado() {
        return venta != null;
    }
}
