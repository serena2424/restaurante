package com.barclub.repository;

import com.barclub.entity.CierreCaja;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CierreCajaRepository extends JpaRepository<CierreCaja, Long> {

    List<CierreCaja> findByFechaCierreBetweenOrderByFechaCierreAsc(LocalDateTime desde, LocalDateTime hasta);

    // Agrupa por cuándo se ABRIÓ cada caja (su "jornada"), no por cuándo se
    // cerró — una caja abierta a las 22:00 y cerrada a las 3am del día
    // siguiente pertenece a la noche en que abrió, no al día calendario en
    // que cerró.
    List<CierreCaja> findByFechaAperturaBetweenOrderByFechaAperturaAsc(LocalDateTime desde, LocalDateTime hasta);
}
