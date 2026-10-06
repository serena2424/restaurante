package com.gestorgastronomico.repository;

import com.gestorgastronomico.entity.CierreCaja;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CierreCajaRepository extends JpaRepository<CierreCaja, Long> {

    List<CierreCaja> findByFechaCierreBetweenOrderByFechaCierreAsc(LocalDateTime desde, LocalDateTime hasta);

    /** Cajas abiertas en un rango: una caja pertenece al día en que se abrió. */
    List<CierreCaja> findByFechaAperturaBetweenOrderByFechaAperturaAsc(LocalDateTime desde, LocalDateTime hasta);
}
