package com.gestorgastronomico.service;

import com.gestorgastronomico.dto.VentaRequestDTO;
import com.gestorgastronomico.dto.VentaResponseDTO;
import com.gestorgastronomico.entity.*;
import com.gestorgastronomico.exception.BusinessException;
import com.gestorgastronomico.repository.CierreCajaRepository;
import com.gestorgastronomico.repository.PedidoRepository;
import com.gestorgastronomico.repository.VentaRepository;
import com.gestorgastronomico.websocket.RealtimeNotifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VentaServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-06T01:00:00Z"), ZONA); // 5/10 22:00
    private static final LocalDateTime APERTURA = LocalDateTime.of(2026, 10, 5, 20, 0);

    @Mock private VentaRepository ventaRepository;
    @Mock private PedidoRepository pedidoRepository;
    @Mock private ConfigLocalService configLocalService;
    @Mock private RealtimeNotifier realtimeNotifier;
    @Mock private CierreCajaRepository cierreCajaRepository;

    private VentaService ventaService;
    private ConfigLocal config;

    @BeforeEach
    void setUp() {
        ventaService = new VentaService(ventaRepository, pedidoRepository, configLocalService,
                realtimeNotifier, cierreCajaRepository, RELOJ);
        config = ConfigLocal.builder().id(1L).cajaAbierta(true).cierreCaja(APERTURA.toString()).build();
        when(configLocalService.obtener()).thenReturn(config);
        when(ventaRepository.save(any(Venta.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cobrar_entregaElPedido() {
        Pedido pedido = pedido(1L, EstadoPedido.LISTO, 12800.0, 800.0);

        ventaService.registrar(cobro(1L, MetodoPago.EFECTIVO, true));

        assertEquals(EstadoPedido.ENTREGADO, pedido.getEstado());
        assertTrue(pedido.estaPagado());
    }

    @Test
    void cobrarSinEntregar_dejaElPedidoEnCocina() {
        Pedido pedido = pedido(2L, EstadoPedido.PENDIENTE, 5000.0, 0.0);

        ventaService.registrar(cobro(2L, MetodoPago.TRANSFERENCIA, false));

        assertEquals(EstadoPedido.PENDIENTE, pedido.getEstado());
        assertTrue(pedido.estaPagado());
    }

    @Test
    void cobrarConLaCajaCerrada_seRechaza() {
        config.setCajaAbierta(false);
        pedido(3L, EstadoPedido.LISTO, 1000.0, 0.0);

        assertThrows(BusinessException.class, () -> ventaService.registrar(cobro(3L, MetodoPago.EFECTIVO, true)));
    }

    @Test
    void cajero_corrigeUnCobroDeLaCajaAbiertaYQuedaRegistrado() {
        iniciarSesion("cajero@test.com", "CAJERO");
        Venta venta = venta(10L, LocalDate.of(2026, 10, 5), LocalTime.of(21, 30));

        VentaResponseDTO corregida = ventaService.corregirMetodoPago(10L, MetodoPago.TRANSFERENCIA);

        assertEquals(MetodoPago.TRANSFERENCIA, corregida.getMetodoPago());
        assertEquals(MetodoPago.EFECTIVO, corregida.getMetodoPagoOriginal());
        assertEquals("cajero@test.com", venta.getCorregidoPor());
    }

    @Test
    void cajero_noPuedeCorregirUnCobroDeUnaCajaAnterior() {
        iniciarSesion("cajero@test.com", "CAJERO");
        venta(11L, LocalDate.of(2026, 10, 4), LocalTime.of(21, 30));

        assertThrows(BusinessException.class, () -> ventaService.corregirMetodoPago(11L, MetodoPago.TARJETA));
    }

    @Test
    void admin_puedeCorregirCualquierCobro() {
        iniciarSesion("admin@test.com", "ADMIN");
        venta(12L, LocalDate.of(2026, 10, 1), LocalTime.of(13, 0));

        VentaResponseDTO corregida = ventaService.corregirMetodoPago(12L, MetodoPago.TARJETA);

        assertEquals(MetodoPago.TARJETA, corregida.getMetodoPago());
    }

    @Test
    void informe_separaLosEnvios() {
        Venta delivery = Venta.builder().total(12800.0).metodoPago(MetodoPago.EFECTIVO)
                .pedido(Pedido.builder().id(1L).tipo(TipoPedido.DELIVERY).costoEnvio(800.0).build()).build();
        Venta mesa = Venta.builder().total(9500.0).metodoPago(MetodoPago.TARJETA)
                .pedido(Pedido.builder().id(2L).tipo(TipoPedido.LOCAL).mesa("7").costoEnvio(0.0).build()).build();
        when(ventaRepository.findEntreJornadas(any(), any())).thenReturn(List.of(delivery, mesa));
        when(ventaRepository.rankingProductosPorJornada(any(), any())).thenReturn(List.of());

        Map<String, Object> informe = ventaService.informe(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5));

        assertEquals(22300.0, (Double) informe.get("total"), 0.001);
        assertEquals(800.0, (Double) informe.get("envios"), 0.001);
        assertEquals(21500.0, (Double) informe.get("totalSinEnvios"), 0.001);
    }

    @Test
    void ventaDeMesaSinNombre_muestraElNumeroDeMesa() {
        Venta venta = Venta.builder().id(1L).total(9500.0).metodoPago(MetodoPago.EFECTIVO)
                .pedido(Pedido.builder().id(2L).tipo(TipoPedido.LOCAL).mesa("7").build()).build();

        assertEquals("Mesa 7", ventaService.toDTO(venta).getNombreCliente());
    }

    @Test
    void ventaDespuesDeMedianoche_cuentaParaElDiaEnQueAbrioLaCaja() {
        LocalDate jornada = ventaService.calcularJornada(LocalDateTime.of(2026, 10, 6, 0, 30), APERTURA);

        assertEquals(LocalDate.of(2026, 10, 5), jornada);
    }

    @Test
    void cajaOlvidadaMasDe24Horas_cuentaParaElDiaReal() {
        LocalDate jornada = ventaService.calcularJornada(LocalDateTime.of(2026, 10, 7, 1, 0), APERTURA);

        assertEquals(LocalDate.of(2026, 10, 7), jornada);
    }

    private Pedido pedido(Long id, EstadoPedido estado, double total, double envio) {
        Pedido pedido = Pedido.builder().id(id).estado(estado).tipo(TipoPedido.RETIRO)
                .total(total).costoEnvio(envio).build();
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));
        when(ventaRepository.findByPedidoId(id)).thenReturn(Optional.empty());
        return pedido;
    }

    private Venta venta(Long id, LocalDate fecha, LocalTime hora) {
        Venta venta = Venta.builder().id(id).fecha(fecha).hora(hora).total(1000.0)
                .metodoPago(MetodoPago.EFECTIVO).build();
        when(ventaRepository.findById(id)).thenReturn(Optional.of(venta));
        return venta;
    }

    private static VentaRequestDTO cobro(Long pedidoId, MetodoPago metodo, boolean entregar) {
        VentaRequestDTO dto = new VentaRequestDTO();
        dto.setPedidoId(pedidoId);
        dto.setMetodoPago(metodo);
        dto.setEntregar(entregar);
        return dto;
    }

    private static void iniciarSesion(String email, String rol) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }
}
