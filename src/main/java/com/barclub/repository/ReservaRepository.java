package com.barclub.repository;

import com.barclub.entity.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    List<Reserva> findByFecha(LocalDate fecha);

    List<Reserva> findByFechaAndEstado(LocalDate fecha, Reserva.EstadoReserva estado);

    List<Reserva> findByFechaBetweenAndEstadoOrderByFechaAscHoraAsc(
        LocalDate desde, LocalDate hasta, Reserva.EstadoReserva estado
    );

    List<Reserva> findByClienteId(Long clienteId);

    List<Reserva> findByEstado(Reserva.EstadoReserva estado);

    boolean existsByFechaAndHoraAndEstado(
        java.time.LocalDate fecha,
        java.time.LocalTime hora,
        Reserva.EstadoReserva estado
    );
}
