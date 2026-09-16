package com.barclub.repository;

import com.barclub.entity.Venta;
import com.barclub.entity.MetodoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Long> {

    List<Venta> findByFecha(LocalDate fecha);

    // "jornada" agrupa por turno de trabajo real (ver Venta.jornada), no por
    // fecha de calendario — así una caja que cruza la medianoche cuenta
    // entera para una sola noche en vez de partirse en dos días.
    List<Venta> findByJornada(LocalDate jornada);

    Optional<Venta> findByPedidoId(Long pedidoId);

    List<Venta> findByMetodoPago(MetodoPago metodoPago);

    @Query("SELECT COALESCE(SUM(v.total), 0) FROM Venta v WHERE v.jornada = :jornada")
    Double sumTotalByJornada(LocalDate jornada);

    @Query("SELECT COUNT(v) FROM Venta v WHERE v.jornada = :jornada")
    Long countByJornada(LocalDate jornada);

    // ==================== INFORMES POR PERÍODO ====================

    /** Ventas de un rango de fechas (informe semanal / mensual / personalizado). */
    @Query("SELECT v FROM Venta v WHERE v.fecha BETWEEN :desde AND :hasta ORDER BY v.fecha DESC, v.hora DESC")
    List<Venta> findEntreFechas(LocalDate desde, LocalDate hasta);

    /** [nombre, categoría, unidades, ingreso] por producto en el período, del más vendido al menos. */
    @Query("SELECT pr.nombre, pr.categoria, SUM(d.cantidad), SUM(d.subtotal) " +
           "FROM Venta v JOIN v.pedido p JOIN p.detalles d JOIN d.producto pr " +
           "WHERE v.fecha BETWEEN :desde AND :hasta " +
           "GROUP BY pr.id, pr.nombre, pr.categoria ORDER BY SUM(d.cantidad) DESC")
    List<Object[]> rankingProductos(LocalDate desde, LocalDate hasta);

    // Ventas desde un momento dado (cierre de caja)
    @Query("SELECT v FROM Venta v WHERE v.fecha > :fecha OR (v.fecha = :fecha AND v.hora >= :hora) ORDER BY v.fecha DESC, v.hora DESC")
    List<Venta> findDesde(LocalDate fecha, LocalTime hora);
}
