package com.gestorgastronomico.config;

import com.gestorgastronomico.entity.CierreCaja;
import com.gestorgastronomico.entity.Venta;
import com.gestorgastronomico.repository.CierreCajaRepository;
import com.gestorgastronomico.repository.VentaRepository;
import com.gestorgastronomico.service.VentaService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, recalcula la jornada de todas las ventas según las cajas
 * registradas. Corrige ventas viejas sin jornada o calculadas con reglas anteriores.
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

    // Corre con la aplicación ya iniciada para que las transacciones del repositorio tengan efecto.
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void rellenar() {
        List<Venta> todas = ventaRepository.findAll();
        if (todas.isEmpty()) return;

        List<CierreCaja> cajas = cierreCajaRepository.findAll();
        // La caja abierta todavía no tiene registro de cierre: se toma desde su apertura.
        LocalDateTime aperturaCajaActual = null;
        try {
            java.util.Map<String, Object> estado = ventaService.estadoCaja();
            Object desde = estado.get("abiertaDesde");
            if (Boolean.TRUE.equals(estado.get("abierta")) && desde != null && !String.valueOf(desde).isBlank()) {
                aperturaCajaActual = LocalDateTime.parse(String.valueOf(desde));
            }
        } catch (Exception e) {
            log.warn("BackfillJornadaVentas: no se pudo leer la caja actual: {}", e.getMessage());
        }
        final LocalDateTime aperturaActual = aperturaCajaActual;
        int corregidas = 0;
        for (Venta v : todas) {
            if (v.getFecha() == null || v.getHora() == null) continue;
            LocalDateTime momento = LocalDateTime.of(v.getFecha(), v.getHora());

            // Caja en la que cayó la venta: apertura <= momento < cierre.
            LocalDateTime aperturaDeSuCaja = cajas.stream()
                    .filter(c -> c.getFechaApertura() != null && c.getFechaCierre() != null)
                    .filter(c -> !momento.isBefore(c.getFechaApertura()) && momento.isBefore(c.getFechaCierre()))
                    .map(CierreCaja::getFechaApertura)
                    .findFirst()
                    .orElse(aperturaActual != null && !momento.isBefore(aperturaActual) ? aperturaActual : null);

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
