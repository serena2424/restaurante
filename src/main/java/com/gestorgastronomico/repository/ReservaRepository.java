package com.gestorgastronomico.repository;

import com.gestorgastronomico.entity.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    List<Reserva> findByFechaOrderByHoraAsc(LocalDate fecha);

    List<Reserva> findByFechaAndEstado(LocalDate fecha, Reserva.EstadoReserva estado);

    List<Reserva> findByFechaBetweenAndEstadoOrderByFechaAscHoraAsc(
            LocalDate desde, LocalDate hasta, Reserva.EstadoReserva estado);
}
