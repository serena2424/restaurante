package com.barclub.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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

    // Momento exacto en que el pedido pasó a ENTREGADO. Sirve para mostrar en el
    // tablero lo entregado desde el último cierre de caja, igual en todos los
    // dispositivos (no depende del navegador).
    @Column
    private LocalDateTime entregadoEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoPedido tipo;

    @Column(nullable = false)
    @Builder.Default
    private Double total = 0.0;

    // Cliente opcional
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Cliente cliente;

    // Usuario que registró el pedido (con cascada)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Usuario usuario;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<DetallePedido> detalles = new ArrayList<>();

    @OneToOne(mappedBy = "pedido", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Venta venta;

    private LocalTime horarioEntrega;
    private String nombreCliente;
    private String telefonoCliente;
    private String direccionEntrega;

    // Nº de mesa (pedidos en salón, rol MOZO)
    @Column(length = 20)
    private String mesa;

    // Qué usuario del panel cargó este pedido (null para pedidos hechos por
    // el cliente desde la página pública, que no requiere login). Se usa
    // para que cada Mozo solo pueda ver/editar los pedidos de sus propias
    // mesas, sin mezclarse con los de otros mozos.
    private Long creadoPorUsuarioId;
    private String creadoPorNombre;

    // Marca cuándo se editó el pedido después de creado (agregar/sacar
    // productos, cambiar cantidad, corregir datos del cliente). Null si
    // nunca se tocó desde que se creó. Sirve para que Cocina vea que una
    // comanda cambió después de haberla mirado, sin tener que revisarla
    // manualmente cada vez (pedido silencioso reportado en QA).
    private java.time.LocalDateTime modificadoEn;

    // Foto (snapshot) de los productos del pedido justo ANTES de la primera
    // edición del ciclo de cambios actual (JSON simple: [{nombre,variante,
    // cantidad}]). Se guarda solo la primera vez que se toca el pedido desde
    // que quedó "limpio" (createado o pasado a LISTO), así Cocina puede
    // comparar contra el pedido original y mostrar qué cambió (plato sacado
    // tachado, plato nuevo en verde, etc.) hasta que el pedido pase a LISTO,
    // momento en el que se limpia junto con modificadoEn.
    @Column(columnDefinition = "TEXT")
    private String detalleSnapshotAntesEdicion;

    // Método de pago que eligió el cliente al hacer el pedido (referencia para
    // el cajero al cobrar; puede ser null y el cajero lo cambia si hace falta).
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MetodoPago metodoPagoPreferido;

    // Costo de envío cobrado al cliente (0 en retiro y en el local). Se suma al total.
    @Column
    private Double costoEnvio;
}
