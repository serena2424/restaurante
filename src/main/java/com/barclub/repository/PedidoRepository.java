package com.barclub.repository;

import com.barclub.entity.EstadoPedido;
import com.barclub.entity.Pedido;
import com.barclub.entity.TipoPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    List<Pedido> findByEstado(EstadoPedido estado);

    List<Pedido> findByFecha(LocalDate fecha);

    List<Pedido> findByFechaAndEstado(LocalDate fecha, EstadoPedido estado);

    List<Pedido> findByTipo(TipoPedido tipo);

    List<Pedido> findByClienteId(Long clienteId);

    // Pedidos activos (no entregados ni cancelados)
    @Query("SELECT p FROM Pedido p WHERE p.estado NOT IN ('ENTREGADO', 'CANCELADO') ORDER BY p.fecha DESC, p.hora DESC")
    List<Pedido> findPedidosActivos();

    @Query("SELECT p FROM Pedido p WHERE p.estado = 'ENTREGADO' AND p.entregadoEn >= :desde ORDER BY p.entregadoEn DESC")
    List<Pedido> findEntregadosDesde(@org.springframework.data.repository.query.Param("desde") java.time.LocalDateTime desde);

    // Total de ventas del día
    @Query("SELECT COALESCE(SUM(p.total), 0) FROM Pedido p WHERE p.fecha = :fecha AND p.estado = 'ENTREGADO'")
    Double sumTotalByFecha(LocalDate fecha);
}
