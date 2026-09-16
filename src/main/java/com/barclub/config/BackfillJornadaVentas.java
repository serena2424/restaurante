package com.barclub.config;

import com.barclub.entity.CierreCaja;
import com.barclub.entity.Venta;
import com.barclub.repository.CierreCajaRepository;
import com.barclub.repository.VentaRepository;
import com.barclub.service.VentaService;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corrige la jornada de las ventas al arrancar el servidor — tanto las que
 * quedaron vacías (ventas de antes de que ese campo existiera) como las que
 * quedaron mal calculadas por versiones anteriores de la lógica (por
 * ejemplo, antes de poner el límite de 24hs a cuánto puede "durar" una
 * caja abierta contando para el mismo día — una caja olvidada abierta
 * varios días mezclaba ventas de días distintos en una sola jornada).
 * Recalcula TODAS las ventas cada vez que arranca (no solo las vacías),
 * así que es un arreglo que se auto-corrige solo si en el futuro cambia la
 * lógica de nuevo — no hace falta acordarse de correr nada a mano.
 */
@Component
public class BackfillJornadaVentas {

    private static final Logger log = LoggerFactory.getLogger(BackfillJornadaVentas.class);

    private final VentaRepository ventaRepository;
    private final CierreCajaRepository cierreCajaRepository;
    private final VentaService ventaService;

    public BackfillJornadaVentas(VentaRepository ventaRepository, CierreCajaRepository cierreCajaRepository, VentaService ventaService) {
        this.ventaRepository = ventaRepository;
        this.cierreCajaRepository = cierreCajaRepository;
        this.ventaService = ventaService;
    }

    @PostConstruct
    @Transactional
    public void rellenar() {
        List<Venta> todas = ventaRepository.findAll();
        if (todas.isEmpty()) return;

        List<CierreCaja> cajas = cierreCajaRepository.findAll();
        int corregidas = 0;
        for (Venta v : todas) {
            if (v.getFecha() == null || v.getHora() == null) continue;
            LocalDateTime momento = LocalDateTime.of(v.getFecha(), v.getHora());

            // Buscar en qué caja cayó esta venta (apertura <= momento < cierre).
            LocalDateTime aperturaDeSuCaja = cajas.stream()
                    .filter(c -> c.getFechaApertura() != null && c.getFechaCierre() != null)
                    .filter(c -> !momento.isBefore(c.getFechaApertura()) && momento.isBefore(c.getFechaCierre()))
                    .map(CierreCaja::getFechaApertura)
                    .findFirst()
                    .orElse(null);

            LocalDate jornadaCorrecta = ventaService.calcularJornada(momento, aperturaDeSuCaja);

            if (!jornadaCorrecta.equals(v.getJornada())) {
                v.setJornada(jornadaCorrecta);
                corregidas++;
            }
        }
        if (corregidas > 0) {
            ventaRepository.saveAll(todas);
        }
        log.info("BackfillJornadaVentas: se corrigió la jornada de {} venta(s) (de {} revisadas).", corregidas, todas.size());
    }
}
