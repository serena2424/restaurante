package com.gestorgastronomico.service;

import com.gestorgastronomico.dto.SesionCajaDTO;
import com.gestorgastronomico.dto.VentaRequestDTO;
import com.gestorgastronomico.dto.VentaResponseDTO;
import com.gestorgastronomico.entity.*;
import com.gestorgastronomico.exception.BusinessException;
import com.gestorgastronomico.exception.ResourceNotFoundException;
import com.gestorgastronomico.repository.CierreCajaRepository;
import com.gestorgastronomico.repository.PedidoRepository;
import com.gestorgastronomico.repository.VentaRepository;
import com.gestorgastronomico.websocket.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class VentaService {

    private static final Logger logger = LoggerFactory.getLogger(VentaService.class);
    /** Una caja abierta más de este tiempo se considera olvidada: las ventas cuentan en su día real. */
    private static final int HORAS_MAXIMAS_DE_UNA_JORNADA = 24;

    private final VentaRepository ventaRepository;
    private final PedidoRepository pedidoRepository;
    private final ConfigLocalService configLocalService;
    private final RealtimeNotifier realtimeNotifier;
    private final CierreCajaRepository cierreCajaRepository;
    private final Clock clock;

    /**
     * Registra el cobro de un pedido. Con entregar = true (lo normal) el pedido
     * pasa a ENTREGADO; con false queda pagado y sigue su curso en cocina.
     */
    public VentaResponseDTO registrar(VentaRequestDTO dto) {
        if (Boolean.FALSE.equals(configLocalService.obtener().getCajaAbierta())) {
            throw new BusinessException("La caja está cerrada. Abrila antes de cobrar.");
        }
        Pedido pedido = pedidoRepository.findById(dto.getPedidoId())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", dto.getPedidoId()));
        if (pedido.getEstado() == EstadoPedido.ENTREGADO || pedido.getEstado() == EstadoPedido.CANCELADO) {
            throw new BusinessException("No se puede cobrar un pedido " + pedido.getEstado().name().toLowerCase() + ".");
        }
        if (pedido.estaPagado() || ventaRepository.findByPedidoId(pedido.getId()).isPresent()) {
            throw new BusinessException("Este pedido ya está cobrado.");
        }

        LocalDateTime ahora = LocalDateTime.now(clock);
        Venta venta = Venta.builder()
                .fecha(ahora.toLocalDate())
                .hora(ahora.toLocalTime())
                .jornada(jornadaActual())
                .total(pedido.getTotal())
                .metodoPago(dto.getMetodoPago())
                .pedido(pedido)
                .build();
        pedido.setVenta(venta);

        if (!Boolean.FALSE.equals(dto.getEntregar())) {
            pedido.setEstado(EstadoPedido.ENTREGADO);
            pedido.setEntregadoEn(ahora);
        }
        Venta guardada = ventaRepository.save(venta);
        pedidoRepository.save(pedido);
        realtimeNotifier.avisarPedidos();
        return toDTO(guardada);
    }

    /**
     * Corrige el medio de pago de un cobro mal cargado. El admin puede corregir
     * cualquier venta; el cajero, solo las de la caja abierta. Queda registrado
     * el medio original, quién lo cambió y cuándo.
     */
    public VentaResponseDTO corregirMetodoPago(Long ventaId, MetodoPago nuevo) {
        if (nuevo == null) throw new BusinessException("Elegí el medio de pago correcto.");
        Venta venta = ventaRepository.findById(ventaId).orElseThrow(() -> new ResourceNotFoundException("Venta", ventaId));
        if (venta.getMetodoPago() == nuevo) return toDTO(venta);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean esAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!esAdmin && !perteneceACajaAbierta(venta)) {
            throw new BusinessException("Solo se pueden corregir cobros de la caja abierta. Para uno anterior, pedíselo al admin.");
        }

        if (venta.getMetodoPagoOriginal() == null) venta.setMetodoPagoOriginal(venta.getMetodoPago());
        venta.setMetodoPago(nuevo);
        venta.setCorregidoPor(auth != null ? auth.getName() : null);
        venta.setCorregidoEn(LocalDateTime.now(clock));
        logger.info("Venta #{}: medio de pago corregido a {} por {}", ventaId, nuevo, venta.getCorregidoPor());
        Venta guardada = ventaRepository.save(venta);
        realtimeNotifier.avisarPedidos();
        return toDTO(guardada);
    }

    private boolean perteneceACajaAbierta(Venta venta) {
        if (Boolean.FALSE.equals(configLocalService.obtener().getCajaAbierta())) return false;
        LocalDateTime apertura = inicioCajaActual();
        LocalDateTime momento = venta.getFecha().atTime(venta.getHora());
        return apertura == null ? venta.getFecha().equals(LocalDate.now(clock)) : !momento.isBefore(apertura);
    }

    @Transactional(readOnly = true)
    public List<VentaResponseDTO> listarTodas() {
        return ventaRepository.findAll().stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<VentaResponseDTO> listarPorFecha(LocalDate jornada) {
        return ventaRepository.findByJornada(jornada).stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public Double totalDelDia(LocalDate jornada) {
        return ventaRepository.sumTotalByJornada(jornada);
    }

    @Transactional(readOnly = true)
    public VentaResponseDTO obtenerPorId(Long id) {
        return toDTO(ventaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Venta", id)));
    }

    /** Informe de un rango de jornadas: totales, envíos aparte, medios de pago y ranking. */
    @Transactional(readOnly = true)
    public Map<String, Object> informe(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) throw new BusinessException("Indicá las fechas del informe");
        if (hasta.isBefore(desde)) throw new BusinessException("La fecha final no puede ser anterior a la inicial");

        List<Venta> ventas = ventaRepository.findEntreJornadas(desde, hasta);
        double total = ventas.stream().mapToDouble(VentaService::totalDe).sum();
        double envios = ventas.stream().mapToDouble(VentaService::envioDe).sum();

        Map<String, Map<String, Object>> porPago = new LinkedHashMap<>();
        for (MetodoPago metodo : MetodoPago.values()) {
            List<Venta> delMetodo = ventas.stream().filter(v -> v.getMetodoPago() == metodo).toList();
            Map<String, Object> resumen = new HashMap<>();
            resumen.put("cantidad", delMetodo.size());
            resumen.put("total", delMetodo.stream().mapToDouble(VentaService::totalDe).sum());
            porPago.put(metodo.name(), resumen);
        }

        List<Map<String, Object>> productos = new ArrayList<>();
        for (Object[] fila : ventaRepository.rankingProductosPorJornada(desde, hasta)) {
            Map<String, Object> producto = new HashMap<>();
            producto.put("nombre", fila[0]);
            producto.put("categoria", fila[1]);
            producto.put("variante", fila[2]);
            producto.put("unidades", fila[3] != null ? ((Number) fila[3]).longValue() : 0L);
            producto.put("ingreso", fila[4] != null ? ((Number) fila[4]).doubleValue() : 0.0);
            productos.add(producto);
        }

        Map<String, Object> informe = new HashMap<>();
        informe.put("desde", desde.toString());
        informe.put("hasta", hasta.toString());
        informe.put("cantidadVentas", ventas.size());
        informe.put("total", total);
        informe.put("envios", envios);
        informe.put("totalSinEnvios", total - envios);
        informe.put("ticketPromedio", ventas.isEmpty() ? 0.0 : (total - envios) / ventas.size());
        informe.put("porMetodoPago", porPago);
        informe.put("productos", productos);
        informe.put("ventas", ventas.stream().map(this::toDTO).toList());
        return informe;
    }

    @Transactional(readOnly = true)
    public List<VentaResponseDTO> listarDesdeCierre() {
        LocalDateTime inicio = inicioCajaActual();
        if (inicio == null) return listarPorFecha(LocalDate.now(clock));
        return ventaRepository.findDesde(inicio.toLocalDate(), inicio.toLocalTime()).stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public Double totalDesdeCierre() {
        return listarDesdeCierre().stream().mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0.0).sum();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> estadoCaja() {
        ConfigLocal cfg = configLocalService.obtener();
        Map<String, Object> estado = new HashMap<>();
        estado.put("abiertaDesde", cfg.getCierreCaja());
        estado.put("abierta", !Boolean.FALSE.equals(cfg.getCajaAbierta()));
        return estado;
    }

    public Map<String, Object> cerrarCaja() {
        ConfigLocal cfg = configLocalService.obtener();
        if (Boolean.FALSE.equals(cfg.getCajaAbierta())) {
            throw new BusinessException("La caja ya está cerrada.");
        }
        List<VentaResponseDTO> ventas = listarDesdeCierre();
        double total = ventas.stream().mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0.0).sum();
        LocalDateTime ahora = LocalDateTime.now(clock).withNano(0);
        LocalDateTime apertura = inicioCajaActual();

        cierreCajaRepository.save(CierreCaja.builder()
                .fechaApertura(apertura != null ? apertura : ahora.toLocalDate().atStartOfDay())
                .fechaCierre(ahora)
                .totalVentas(total)
                .cantidadVentas(ventas.size())
                .build());

        cfg.setCierreCaja(ahora.toString());
        cfg.setCajaAbierta(false);
        configLocalService.guardar(cfg);

        Map<String, Object> resumen = new HashMap<>();
        resumen.put("cerradaEn", ahora.toString());
        resumen.put("totalCerrado", total);
        resumen.put("cantidadVentas", ventas.size());
        return resumen;
    }

    /** La caja nueva empieza ahora: las ventas anteriores quedan en la caja que se cerró. */
    public void abrirCaja() {
        ConfigLocal cfg = configLocalService.obtener();
        if (!Boolean.FALSE.equals(cfg.getCajaAbierta())) {
            throw new BusinessException("La caja ya está abierta.");
        }
        cfg.setCierreCaja(LocalDateTime.now(clock).withNano(0).toString());
        cfg.setCajaAbierta(true);
        configLocalService.guardar(cfg);
    }

    /**
     * Cajas de una jornada, cada una con su total: las cerradas, la abierta (si
     * es la jornada en curso) y, si quedaron ventas sin caja registrada (datos
     * de versiones anteriores), una tarjeta extra con esas.
     */
    @Transactional(readOnly = true)
    public List<SesionCajaDTO> sesionesDe(LocalDate jornada) {
        List<SesionCajaDTO> cajas = new ArrayList<>();
        cierreCajaRepository.findByFechaAperturaBetweenOrderByFechaAperturaAsc(
                        jornada.atStartOfDay(), jornada.atTime(LocalTime.MAX))
                .forEach(c -> cajas.add(SesionCajaDTO.builder()
                        .apertura(c.getFechaApertura())
                        .cierre(c.getFechaCierre())
                        .total(c.getTotalVentas())
                        .cantidadVentas(c.getCantidadVentas())
                        .abierta(false)
                        .build()));

        boolean abierta = !Boolean.FALSE.equals(configLocalService.obtener().getCajaAbierta());
        if (abierta && jornada.isEqual(jornadaActual())) {
            List<VentaResponseDTO> ventas = listarDesdeCierre();
            LocalDateTime inicio = inicioCajaActual();
            cajas.add(SesionCajaDTO.builder()
                    .apertura(inicio != null ? inicio : jornada.atStartOfDay())
                    .total(ventas.stream().mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0.0).sum())
                    .cantidadVentas(ventas.size())
                    .abierta(true)
                    .build());
        }

        int contadas = cajas.stream().mapToInt(SesionCajaDTO::getCantidadVentas).sum();
        List<VentaResponseDTO> delDia = listarPorFecha(jornada);
        if (delDia.size() > contadas) {
            double sueltas = delDia.stream().mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0.0).sum()
                    - cajas.stream().mapToDouble(SesionCajaDTO::getTotal).sum();
            cajas.add(SesionCajaDTO.builder()
                    .total(Math.max(sueltas, 0))
                    .cantidadVentas(delDia.size() - contadas)
                    .abierta(false)
                    .build());
        }
        return cajas;
    }

    /** Día de trabajo en curso: la fecha en que se abrió la caja actual. */
    public LocalDate jornadaActual() {
        return calcularJornada(LocalDateTime.now(clock), inicioCajaActual());
    }

    /**
     * Una venta cuenta para el día en que se abrió su caja, salvo que la caja
     * lleve más de 24 horas abierta (alguien se olvidó de cerrarla).
     */
    public LocalDate calcularJornada(LocalDateTime momentoVenta, LocalDateTime aperturaCaja) {
        if (aperturaCaja != null
                && !momentoVenta.isBefore(aperturaCaja)
                && momentoVenta.isBefore(aperturaCaja.plusHours(HORAS_MAXIMAS_DE_UNA_JORNADA))) {
            return aperturaCaja.toLocalDate();
        }
        return momentoVenta.toLocalDate();
    }

    private LocalDateTime inicioCajaActual() {
        String valor = configLocalService.obtener().getCierreCaja();
        if (valor == null || valor.isBlank()) return null;
        try {
            return LocalDateTime.parse(valor);
        } catch (Exception e) {
            logger.warn("Inicio de caja ilegible: {}", valor);
            return null;
        }
    }

    /** Borra las ventas de una jornada. Los pedidos entregados vuelven a LISTO para poder cobrarse de nuevo. */
    public void eliminarPorFecha(LocalDate jornada) {
        List<Venta> ventas = ventaRepository.findByJornada(jornada);
        ventas.forEach(this::liberarPedido);
        ventaRepository.deleteAll(ventas);
        realtimeNotifier.avisarPedidos();
    }

    /** Anula un cobro. Si el pedido ya estaba entregado, vuelve a LISTO. */
    public void eliminarPorId(Long id) {
        Venta venta = ventaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Venta", id));
        liberarPedido(venta);
        ventaRepository.delete(venta);
        realtimeNotifier.avisarPedidos();
    }

    private void liberarPedido(Venta venta) {
        Pedido pedido = venta.getPedido();
        if (pedido == null) return;
        pedido.setVenta(null);
        if (pedido.getEstado() == EstadoPedido.ENTREGADO) {
            pedido.setEstado(EstadoPedido.LISTO);
            pedido.setEntregadoEn(null);
        }
        pedidoRepository.save(pedido);
    }

    private static double totalDe(Venta v) {
        return v.getTotal() != null ? v.getTotal() : 0.0;
    }

    private static double envioDe(Venta v) {
        return v.getPedido() != null && v.getPedido().getCostoEnvio() != null ? v.getPedido().getCostoEnvio() : 0.0;
    }

    public VentaResponseDTO toDTO(Venta v) {
        Pedido pedido = v.getPedido();
        String nombre = null;
        if (pedido != null) {
            if (pedido.getNombreCliente() != null && !pedido.getNombreCliente().isBlank()) {
                nombre = pedido.getNombreCliente();
            } else if (pedido.getCliente() != null) {
                nombre = pedido.getCliente().getNombre();
            } else if (pedido.getMesa() != null && !pedido.getMesa().isBlank()) {
                nombre = "Mesa " + pedido.getMesa();
            }
        }
        return VentaResponseDTO.builder()
                .id(v.getId())
                .fecha(v.getFecha())
                .hora(v.getHora())
                .total(v.getTotal())
                .metodoPago(v.getMetodoPago())
                .pedidoId(pedido != null ? pedido.getId() : null)
                .nombreCliente(nombre)
                .tipoPedido(pedido != null && pedido.getTipo() != null ? pedido.getTipo().name() : null)
                .mesa(pedido != null ? pedido.getMesa() : null)
                .costoEnvio(envioDe(v))
                .metodoPagoOriginal(v.getMetodoPagoOriginal())
                .corregidoPor(v.getCorregidoPor())
                .corregidoEn(v.getCorregidoEn())
                .build();
    }
}
