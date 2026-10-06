package com.barclub.service;

import com.barclub.dto.DetallePedidoRequestDTO;
import com.barclub.dto.PedidoRequestDTO;
import com.barclub.dto.PedidoResponseDTO;
import com.barclub.entity.*;
import com.barclub.exception.BusinessException;
import com.barclub.repository.*;
import com.barclub.websocket.RealtimeNotifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de PedidoService (sin base de datos). Cubren los bugs
 * arreglados en la revisión de octubre 2026: envío que se perdía al editar,
 * cocina cancelando por /estado, opciones inventadas, reglas del local para
 * pedidos públicos y el usuarioId que mandaba el cliente.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PedidoServiceTest {

    @Mock private PedidoRepository pedidoRepository;
    @Mock private ProductoRepository productoRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private VentaRepository ventaRepository;
    @Mock private ProductoService productoService;
    @Mock private ConfigLocalService configLocalService;
    @Mock private ClienteService clienteService;
    @Mock private UsuarioService usuarioService;
    @Mock private RealtimeNotifier realtimeNotifier;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PedidoService pedidoService;

    private Usuario admin;
    private Usuario mozo;
    private Producto plato;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext(); // sin sesión = pedido de la página pública
        admin = Usuario.builder().id(1L).nombre("Admin").email("admin@test.com").rol(Rol.ADMIN).activo(true).build();
        mozo  = Usuario.builder().id(4L).nombre("Mozo").email("mozo@test.com").rol(Rol.MOZO).activo(true).build();
        plato = Producto.builder().id(10L).nombre("Milanesa").precio(1000.0).costo(400.0).activo(true).categoria("Platos").build();

        when(usuarioRepository.findAll()).thenReturn(List.of(admin, mozo));
        when(usuarioRepository.findById(4L)).thenReturn(Optional.of(mozo));
        when(productoRepository.findById(10L)).thenReturn(Optional.of(plato));
        when(configLocalService.obtener()).thenReturn(ConfigLocal.builder().id(1L).estadoManual("auto").build());
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private PedidoRequestDTO pedidoPublico(TipoPedido tipo, String variante) {
        DetallePedidoRequestDTO d = new DetallePedidoRequestDTO();
        d.setProductoId(10L);
        d.setCantidad(1);
        d.setVariante(variante);
        PedidoRequestDTO dto = new PedidoRequestDTO();
        dto.setTipo(tipo);
        dto.setNombreCliente("Cliente");
        // Teléfono distinto en cada prueba para no chocar con el anti-duplicado.
        dto.setTelefonoCliente(UUID.randomUUID().toString().substring(0, 12));
        dto.setDireccionEntrega("Calle 123");
        dto.setDetalles(List.of(d));
        return dto;
    }

    @Test
    void editarDelivery_conservaElCostoDeEnvioEnElTotal() {
        Pedido pedido = Pedido.builder()
                .id(5L).fecha(LocalDate.now()).hora(LocalTime.now())
                .estado(EstadoPedido.PENDIENTE).tipo(TipoPedido.DELIVERY)
                .usuario(admin).costoEnvio(500.0).total(1500.0)
                .detalles(new ArrayList<>())
                .build();
        pedido.getDetalles().add(DetallePedido.builder().id(1L).pedido(pedido).producto(plato)
                .cantidad(1).precioUnitario(1000.0).subtotal(1000.0).build());
        when(pedidoRepository.findById(5L)).thenReturn(Optional.of(pedido));

        DetallePedidoRequestDTO otro = new DetallePedidoRequestDTO();
        otro.setProductoId(10L);
        otro.setCantidad(1);
        PedidoResponseDTO r = pedidoService.agregarDetalle(5L, otro);

        assertEquals(2500.0, r.getTotal(), 0.001, "2 x $1.000 + $500 de envío");
    }

    @Test
    void cambiarEstado_aCancelado_seRechaza() {
        Pedido pedido = Pedido.builder().id(6L).estado(EstadoPedido.PENDIENTE).tipo(TipoPedido.RETIRO).build();
        when(pedidoRepository.findById(6L)).thenReturn(Optional.of(pedido));
        assertThrows(BusinessException.class, () -> pedidoService.cambiarEstado(6L, EstadoPedido.CANCELADO));
        assertEquals(EstadoPedido.PENDIENTE, pedido.getEstado());
    }

    @Test
    void opcionInventada_enProductoSinOpciones_seRechaza() {
        assertThrows(BusinessException.class,
                () -> pedidoService.crear(pedidoPublico(TipoPedido.DELIVERY, "Gigante gratis")));
    }

    @Test
    void localCerrado_rechazaPedidoPublico() {
        when(configLocalService.obtener()).thenReturn(ConfigLocal.builder().id(1L).estadoManual("close").build());
        assertThrows(BusinessException.class, () -> pedidoService.crear(pedidoPublico(TipoPedido.RETIRO, null)));
    }

    @Test
    void deliveryPublicoConTarjeta_seRechaza() {
        PedidoRequestDTO dto = pedidoPublico(TipoPedido.DELIVERY, null);
        dto.setMetodoPagoPreferido(MetodoPago.TARJETA);
        assertThrows(BusinessException.class, () -> pedidoService.crear(dto));
    }

    @Test
    void pedidoPublico_ignoraElUsuarioIdQueMandaElCliente() {
        PedidoRequestDTO dto = pedidoPublico(TipoPedido.DELIVERY, null);
        dto.setUsuarioId(4L); // el id del mozo: antes esto hacía fallar (o asignaba) el pedido

        pedidoService.crear(dto);

        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoRepository, atLeastOnce()).save(captor.capture());
        Pedido guardado = captor.getValue();
        assertEquals(admin.getId(), guardado.getUsuario().getId());
        assertNull(guardado.getCreadoPorUsuarioId());
    }

    @Test
    void pedidoPublico_calculaPrecioYEnvioEnElServidor() {
        when(configLocalService.obtener()).thenReturn(ConfigLocal.builder().id(1L).estadoManual("auto").costoDelivery(300).build());
        PedidoResponseDTO r = pedidoService.crear(pedidoPublico(TipoPedido.DELIVERY, null));
        assertEquals(1300.0, r.getTotal(), 0.001);
    }
}
