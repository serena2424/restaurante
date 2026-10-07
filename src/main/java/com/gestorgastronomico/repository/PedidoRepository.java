package com.gestorgastronomico.repository;

import com.gestorgastronomico.entity.EstadoPedido;
import com.gestorgastronomico.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    List<Pedido> findByEstado(EstadoPedido estado);

    List<Pedido> findByFecha(LocalDate fecha);

    List<Pedido> findByFechaAndEstado(LocalDate fecha, EstadoPedido estado);

    @Query("SELECT p FROM Pedido p WHERE p.estado NOT IN ('ENTREGADO', 'CANCELADO') ORDER BY p.fecha DESC, p.hora DESC")
    List<Pedido> findPedidosActivos();

    /** Comanda de mesa abierta (sin cobrar) para ese número de mesa. */
    @Query("SELECT p FROM Pedido p WHERE p.tipo = 'LOCAL' AND p.mesa = :mesa AND p.porTarjetas = true " +
           "AND p.estado NOT IN ('ENTREGADO', 'CANCELADO') " +
           "AND NOT EXISTS (SELECT v FROM Venta v WHERE v.pedido = p) ORDER BY p.id DESC")
    List<Pedido> findComandasAbiertasDeMesa(@Param("mesa") String mesa);

    @Query("SELECT p FROM Pedido p WHERE p.estado = 'ENTREGADO' AND p.entregadoEn >= :desde ORDER BY p.entregadoEn DESC")
    List<Pedido> findEntregadosDesde(@Param("desde") LocalDateTime desde);
}
