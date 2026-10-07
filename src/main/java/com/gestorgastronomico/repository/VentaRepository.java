package com.gestorgastronomico.repository;

import com.gestorgastronomico.entity.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Long> {

    /** Ventas de un día de trabajo (ver Venta.jornada). */
    List<Venta> findByJornada(LocalDate jornada);

    Optional<Venta> findByPedidoId(Long pedidoId);

    @Query("SELECT COALESCE(SUM(v.total), 0) FROM Venta v WHERE v.jornada = :jornada")
    Double sumTotalByJornada(LocalDate jornada);

    @Query("SELECT COUNT(v) FROM Venta v WHERE v.jornada = :jornada")
    Long countByJornada(LocalDate jornada);

    @Query("SELECT v FROM Venta v WHERE v.jornada BETWEEN :desde AND :hasta ORDER BY v.fecha DESC, v.hora DESC")
    List<Venta> findEntreJornadas(LocalDate desde, LocalDate hasta);

    /** [nombre, categoría, opción, unidades, ingreso] del más vendido al menos vendido. */
    @Query("SELECT pr.nombre, pr.categoria, d.variante, SUM(d.cantidad), SUM(d.subtotal) " +
           "FROM Venta v JOIN v.pedido p JOIN p.detalles d JOIN d.producto pr " +
           "WHERE v.jornada BETWEEN :desde AND :hasta AND (d.estado IS NULL OR d.estado <> 'CANCELADO') " +
           "GROUP BY pr.id, pr.nombre, pr.categoria, d.variante ORDER BY SUM(d.cantidad) DESC")
    List<Object[]> rankingProductosPorJornada(LocalDate desde, LocalDate hasta);

    /** Ventas desde un momento (inicio de la caja actual). */
    @Query("SELECT v FROM Venta v WHERE v.fecha > :fecha OR (v.fecha = :fecha AND v.hora >= :hora) ORDER BY v.fecha DESC, v.hora DESC")
    List<Venta> findDesde(LocalDate fecha, LocalTime hora);
}
