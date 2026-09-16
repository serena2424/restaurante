package com.barclub.service;

import com.barclub.dto.VentaRequestDTO;
import com.barclub.dto.VentaResponseDTO;
import com.barclub.entity.ConfigLocal;
import com.barclub.entity.CierreCaja;
import com.barclub.entity.EstadoPedido;
import com.barclub.entity.MetodoPago;
import com.barclub.entity.Pedido;
import com.barclub.entity.Venta;
import com.barclub.exception.BusinessException;
import com.barclub.exception.ResourceNotFoundException;
import com.barclub.repository.PedidoRepository;
import com.barclub.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class VentaService {

    private final VentaRepository ventaRepository;
    private final PedidoRepository pedidoRepository;
    private final ConfigLocalService configLocalService;
    private final com.barclub.websocket.RealtimeNotifier realtimeNotifier;
    private final com.barclub.repository.CierreCajaRepository cierreCajaRepository;

    // ---- Registrar venta (cierra el pedido) ----
    public VentaResponseDTO registrar(VentaRequestDTO dto) {
        // La caja tiene que estar abierta para poder cobrar — si alguien
        // cerró la caja y nadie la reabrió todavía, no debería poder entrar
        // dinero "de la nada" sin quedar asociado a ninguna caja abierta.
        // Boolean.FALSE.equals(...) en vez de !getCajaAbierta(): si el valor
        // es null (caja de antes de que existiera este control), se trata
        // como abierta, no como cerrada.
        ConfigLocal cfgCaja = configLocalService.obtener();
        if (Boolean.FALSE.equals(cfgCaja.getCajaAbierta())) {
            throw new BusinessException("La caja está cerrada. Abrila antes de cobrar.");
        }

        Pedido pedido = pedidoRepository.findById(dto.getPedidoId())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", dto.getPedidoId()));

        // Se puede cobrar cualquier pedido que no esté ya entregado o cancelado.
        // Antes exigía estado LISTO, pero en la práctica el cajero cobra pedidos
        // que siguen en preparación, y eso hacía fallar el cobro.
        if (pedido.getEstado() == EstadoPedido.ENTREGADO
                || pedido.getEstado() == EstadoPedido.CANCELADO) {
            throw new BusinessException(
                "No se puede cobrar un pedido que ya está " + pedido.getEstado()
            );
        }

        // Verificar que no tenga ya una venta registrada
        if (ventaRepository.findByPedidoId(dto.getPedidoId()).isPresent()) {
            throw new BusinessException("Este pedido ya tiene una venta registrada");
        }

        // Crear la venta
        Venta venta = Venta.builder()
                .fecha(LocalDate.now())
                .hora(LocalTime.now())
                .jornada(jornadaActual())
                .total(pedido.getTotal())
                .metodoPago(dto.getMetodoPago())
                .pedido(pedido)
                .build();

        // Marcar pedido como ENTREGADO y sellar la hora de entrega
        pedido.setEstado(EstadoPedido.ENTREGADO);
        if (pedido.getEntregadoEn() == null) {
            pedido.setEntregadoEn(java.time.LocalDateTime.now());
        }
        pedidoRepository.save(pedido);
        realtimeNotifier.avisarPedidos();

        return toDTO(ventaRepository.save(venta));
    }

    // ---- Listar todas ----
    @Transactional(readOnly = true)
    public List<VentaResponseDTO> listarTodas() {
        return ventaRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ---- Ventas de una jornada (un "día de trabajo": ver Venta.jornada) ----
    @Transactional(readOnly = true)
    public List<VentaResponseDTO> listarPorFecha(LocalDate jornada) {
        return ventaRepository.findByJornada(jornada)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ---- Total de una jornada ----
    @Transactional(readOnly = true)
    public Double totalDelDia(LocalDate jornada) {
        return ventaRepository.sumTotalByJornada(jornada);
    }

    // ---- Informes por período ----
    @Transactional(readOnly = true)
    public Map<String, Object> informe(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) throw new BusinessException("Indicá las fechas del informe");
        if (hasta.isBefore(desde)) throw new BusinessException("La fecha final no puede ser anterior a la inicial");

        List<Venta> ventas = ventaRepository.findEntreFechas(desde, hasta);

        double total = ventas.stream().mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0.0).sum();

        // Desglose por método de pago
        Map<String, Map<String, Object>> porPago = new LinkedHashMap<>();
        for (MetodoPago mp : MetodoPago.values()) {
            List<Venta> delMetodo = ventas.stream()
                    .filter(v -> v.getMetodoPago() == mp).collect(Collectors.toList());
            Map<String, Object> m = new HashMap<>();
            m.put("cantidad", delMetodo.size());
            m.put("total", delMetodo.stream().mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0.0).sum());
            porPago.put(mp.name(), m);
        }

        // Ranking de productos
        List<Map<String, Object>> productos = new ArrayList<>();
        for (Object[] f : ventaRepository.rankingProductos(desde, hasta)) {
            Map<String, Object> m = new HashMap<>();
            m.put("nombre", f[0]);
            m.put("categoria", f[1]);
            m.put("unidades", f[2] != null ? ((Number) f[2]).longValue() : 0L);
            m.put("ingreso", f[3] != null ? ((Number) f[3]).doubleValue() : 0.0);
            productos.add(m);
        }

        Map<String, Object> r = new HashMap<>();
        r.put("desde", desde.toString());
        r.put("hasta", hasta.toString());
        r.put("cantidadVentas", ventas.size());
        r.put("total", total);
        r.put("ticketPromedio", ventas.isEmpty() ? 0.0 : total / ventas.size());
        r.put("porMetodoPago", porPago);
        r.put("productos", productos);
        r.put("ventas", ventas.stream().map(this::toDTO).collect(Collectors.toList()));
        return r;
    }

    // ---- Cierre de caja ----
    @Transactional(readOnly = true)
    public List<VentaResponseDTO> listarDesdeCierre() {
        LocalDateTime c = obtenerMomentoCierre();
        if (c == null) return listarPorFecha(LocalDate.now());
        return ventaRepository.findDesde(c.toLocalDate(), c.toLocalTime())
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Double totalDesdeCierre() {
        return listarDesdeCierre().stream()
                .mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0.0).sum();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> estadoCaja() {
        Map<String, Object> m = new HashMap<>();
        ConfigLocal cfg = configLocalService.obtener();
        m.put("abiertaDesde", cfg.getCierreCaja());
        // null (cajas de antes de este control) se informa como abierta = true.
        m.put("abierta", !Boolean.FALSE.equals(cfg.getCajaAbierta()));
        return m;
    }

    public Map<String, Object> cerrarCaja() {
        List<VentaResponseDTO> ventas = listarDesdeCierre();
        double total = ventas.stream()
                .mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0.0).sum();

        ConfigLocal cfg = configLocalService.obtener();
        LocalDateTime momentoApertura = obtenerMomentoCierre(); // el cierre anterior = cuándo abrió esta caja
        LocalDateTime ahoraDt = LocalDateTime.now().withNano(0);
        String ahora = ahoraDt.toString();

        // Guardar en el historial ANTES de pisar cierreCaja, así "Movimientos
        // de hoy" puede seguir mostrando esta caja después de cerrada, en vez
        // de que sus ventas "desaparezcan" de la vista (bug reportado en QA).
        cierreCajaRepository.save(CierreCaja.builder()
                .fechaApertura(momentoApertura != null ? momentoApertura : ahoraDt.toLocalDate().atStartOfDay())
                .fechaCierre(ahoraDt)
                .totalVentas(total)
                .cantidadVentas(ventas.size())
                .build());

        cfg.setCierreCaja(ahora);
        cfg.setCajaAbierta(false);
        configLocalService.guardar(cfg);

        Map<String, Object> m = new HashMap<>();
        m.put("cerradaEn", ahora);
        m.put("totalCerrado", total);
        m.put("cantidadVentas", ventas.size());
        return m;
    }

    // ---- Abrir una nueva caja (después de haber cerrado la anterior) ----
    // No hace falta tocar cierreCaja acá: ese valor ya marca "desde cuándo
    // se cuenta la caja actual" (quedó seteado en el último cierre). Abrir
    // caja solo habilita de nuevo el cobro.
    public void abrirCaja() {
        ConfigLocal cfg = configLocalService.obtener();
        cfg.setCajaAbierta(true);
        configLocalService.guardar(cfg);
    }

    // ---- Vista del día completo, separada por cada caja ----
    // Resuelve la ambigüedad reportada en QA sobre qué mostrar cuando hay
    // varias aperturas/cierres en un mismo día: se listan TODAS las cajas
    // de ESE día (cerradas + la actualmente abierta si corresponde), cada
    // una con su propio total, en vez de perder las ya cerradas. Funciona
    // tanto para "hoy" como para cualquier fecha del historial.
    @Transactional(readOnly = true)
    public List<com.barclub.dto.SesionCajaDTO> sesionesDe(LocalDate jornada) {
        List<com.barclub.dto.SesionCajaDTO> resultado = new ArrayList<>();

        cierreCajaRepository.findByFechaAperturaBetweenOrderByFechaAperturaAsc(
                        jornada.atStartOfDay(), jornada.atTime(LocalTime.MAX))
                .forEach(c -> resultado.add(com.barclub.dto.SesionCajaDTO.builder()
                        .apertura(c.getFechaApertura())
                        .cierre(c.getFechaCierre())
                        .total(c.getTotalVentas())
                        .cantidadVentas(c.getCantidadVentas())
                        .abierta(false)
                        .build()));

        // La caja actualmente abierta se agrega si la jornada consultada es
        // la que está corriendo AHORA (que no es necesariamente "hoy" según
        // el calendario — si son las 2am y la caja se abrió anoche, la
        // jornada actual sigue siendo la de ayer) Y ADEMÁS la caja
        // efectivamente sigue abierta — antes solo se chequeaba lo primero,
        // así que apenas se cerraba la caja igual seguía apareciendo acá
        // como "abierta" (bug real reportado en QA: el estado de arriba
        // decía "Cerrada" pero Movimientos mostraba una caja abierta).
        boolean cajaRealmenteAbierta = !Boolean.FALSE.equals(configLocalService.obtener().getCajaAbierta());
        if (cajaRealmenteAbierta && jornada.isEqual(jornadaActual())) {
            LocalDateTime momentoApertura = obtenerMomentoCierre();
            List<VentaResponseDTO> ventasAbierta = listarDesdeCierre();
            double totalAbierta = ventasAbierta.stream()
                    .mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0.0).sum();
            resultado.add(com.barclub.dto.SesionCajaDTO.builder()
                    .apertura(momentoApertura != null ? momentoApertura : jornada.atStartOfDay())
                    .cierre(null)
                    .total(totalAbierta)
                    .cantidadVentas(ventasAbierta.size())
                    .abierta(true)
                    .build());
        }

        // Si no encontramos ninguna caja registrada para esa jornada (fechas
        // de antes de que este historial existiera) pero SÍ hubo ventas,
        // armamos una sola tarjeta "sintética" con todo el día junto — así
        // el diseño se ve igual de prolijo en vez de que la sección
        // desaparezca del todo para fechas viejas (bug reportado en QA).
        // No tiene horario de apertura/cierre real porque nunca se registró.
        //
        // Además: puede pasar que haya ventas con esta jornada que NINGUNA
        // de las tarjetas de arriba cuenta (por ejemplo, si cayeron dentro
        // de una caja que en realidad abrió otro día — quedaban "sueltas",
        // sin aparecer en ningún lado, aunque el resumen de la derecha sí
        // las contaba). Se comparan los totales y, si falta gente, se
        // agrega una tarjeta aparte con lo que falte.
        int yaContadas = resultado.stream().mapToInt(com.barclub.dto.SesionCajaDTO::getCantidadVentas).sum();
        List<VentaResponseDTO> ventasDeLaJornada = listarPorFecha(jornada);
        if (ventasDeLaJornada.size() > yaContadas) {
            double totalSuelto = ventasDeLaJornada.stream()
                    .mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0.0).sum()
                    - resultado.stream().mapToDouble(com.barclub.dto.SesionCajaDTO::getTotal).sum();
            resultado.add(com.barclub.dto.SesionCajaDTO.builder()
                    .apertura(null)
                    .cierre(null)
                    .total(Math.max(totalSuelto, 0))
                    .cantidadVentas(ventasDeLaJornada.size() - yaContadas)
                    .abierta(false)
                    .build());
        }

        return resultado;
    }

    private LocalDateTime obtenerMomentoCierre() {
        try {
            String c = configLocalService.obtener().getCierreCaja();
            if (c == null || c.isBlank()) return null;
            return LocalDateTime.parse(c);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Jornada actual (el "día de trabajo" que está corriendo ahora) ----
    // Es la fecha calendario en que se abrió la caja actualmente abierta —
    // no la fecha de hoy. Así, una caja abierta a las 22:00 y todavía
    // corriendo a las 3am sigue contando como la noche de ayer, en vez de
    // que a medianoche el sistema "salte" solo a un día nuevo en medio del
    // turno (el problema real que motivó todo esto).
    public LocalDate jornadaActual() {
        return calcularJornada(LocalDateTime.now(), obtenerMomentoCierre());
    }

    // Une el momento real de una venta con la apertura de la caja bajo la
    // que cae, con un límite de 24 horas: si la caja lleva abierta más de
    // eso (alguien se olvidó de cerrarla, no es una noche cruzando la
    // medianoche), ya no tiene sentido seguir pegando todo al día en que
    // abrió — pasa a contar como el día real en que pasó. Sin este límite,
    // una caja olvidada abierta varios días mezclaba ventas de días
    // distintos en una sola jornada (bug real: una venta del 18 quedó
    // guardada con jornada 16, porque la caja seguía "abierta" desde ahí).
    public LocalDate calcularJornada(LocalDateTime momentoVenta, LocalDateTime aperturaCaja) {
        if (aperturaCaja != null
                && !momentoVenta.isBefore(aperturaCaja)
                && momentoVenta.isBefore(aperturaCaja.plusHours(24))) {
            return aperturaCaja.toLocalDate();
        }
        return momentoVenta.toLocalDate();
    }

    // ---- Obtener por id ----
    @Transactional(readOnly = true)
    public VentaResponseDTO obtenerPorId(Long id) {
        return toDTO(ventaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venta", id)));
    }

    // ---- Eliminar ventas de una fecha (borrado definitivo) ----
    public void eliminarPorFecha(LocalDate jornada) {
        List<Venta> ventas = ventaRepository.findByJornada(jornada);
        // Si se borra la venta, el pedido que quedó marcado como ENTREGADO por
        // ese cobro debe volver a un estado utilizable (LISTO). Si no, ese
        // pedido queda invisible para siempre: no aparece en "Pedidos activos"
        // (no está en PENDIENTE/PREPARACION/LISTO) y tampoco en Ventas (se
        // borró), y no se puede volver a cobrar (el registro de venta lo
        // bloquea mientras siga en ENTREGADO).
        for (Venta v : ventas) {
            Pedido pedido = v.getPedido();
            if (pedido != null && pedido.getEstado() == EstadoPedido.ENTREGADO) {
                pedido.setEstado(EstadoPedido.LISTO);
                pedidoRepository.save(pedido);
            }
        }
        ventaRepository.deleteAll(ventas);
        realtimeNotifier.avisarPedidos();
    }

    // ---- Eliminar una venta por id ----
    public void eliminarPorId(Long id) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venta", id));
        // Mismo motivo que en eliminarPorFecha: sin esto el pedido queda
        // huérfano (invisible en Pedidos activos y sin venta que lo respalde).
        Pedido pedido = venta.getPedido();
        if (pedido != null && pedido.getEstado() == EstadoPedido.ENTREGADO) {
            pedido.setEstado(EstadoPedido.LISTO);
            pedidoRepository.save(pedido);
        }
        ventaRepository.deleteById(id);
        realtimeNotifier.avisarPedidos();
    }

    // ---- Mapper ----
    public VentaResponseDTO toDTO(Venta v) {
        String nombreCliente = null;
        String tipoPedido = null;
        String mesa = null;
        if (v.getPedido() != null) {
            nombreCliente = v.getPedido().getNombreCliente() != null
                    ? v.getPedido().getNombreCliente()
                    : (v.getPedido().getCliente() != null ? v.getPedido().getCliente().getNombre() : "Sin nombre");
            tipoPedido = v.getPedido().getTipo() != null ? v.getPedido().getTipo().name() : null;
            mesa = v.getPedido().getMesa();
        }
        return VentaResponseDTO.builder()
                .id(v.getId())
                .fecha(v.getFecha())
                .hora(v.getHora())
                .total(v.getTotal())
                .metodoPago(v.getMetodoPago())
                .pedidoId(v.getPedido() != null ? v.getPedido().getId() : null)
                .nombreCliente(nombreCliente)
                .tipoPedido(tipoPedido)
                .mesa(mesa)
                .build();
    }
}
