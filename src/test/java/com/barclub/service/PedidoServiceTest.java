package com.barclub.service;

import com.barclub.dto.DetallePedidoRequestDTO;
import com.barclub.dto.PedidoRequestDTO;
import com.barclub.dto.PedidoResponseDTO;
import com.barclub.entity.*;
import com.barclub.exception.BusinessException;
import com.barclub.exception.ResourceNotFoundException;
import com.barclub.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock private PedidoRepository pedidoRepository;
    @Mock private ProductoRepository productoRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private VentaRepository ventaRepository;
    @Mock private ProductoService productoService;
    @Mock private ClienteService clienteService;
    @Mock private UsuarioService usuarioService;

    @InjectMocks
    private PedidoService pedidoService;

    private Usuario usuarioMock;
    private Producto productoMock;

    @BeforeEach
    void setUp() {
        usuarioMock = Usuario.builder()
                .id(1L)
                .nombre("Admin")
                .email("admin@barclub.com")
                .rol(Rol.ADMIN)
                .build();

        productoMock = Producto.builder()
                .id(10L)
                .nombre("Pizza Muzzarella")
                .precio(1500.0)
                .activo(true)
                .categoria("Pizzas")
                .build();
    }

    // -------------------------------------------------------
    // TEST 1: Camino feliz — crear pedido LOCAL correctamente
    // -------------------------------------------------------
    @Test
    void crearPedido_local_debeRetornarPedidoCreado() {
        // Given
        DetallePedidoRequestDTO detalle = new DetallePedidoRequestDTO();
        detalle.setProductoId(10L);
        detalle.setCantidad(2);

        PedidoRequestDTO dto = new PedidoRequestDTO();
        dto.setUsuarioId(1L);
        dto.setTipo(TipoPedido.LOCAL);
        dto.setNombreCliente("Juan");
        dto.setDetalles(List.of(detalle));

        Pedido pedidoGuardado = Pedido.builder()
                .id(1L)
                .fecha(LocalDate.now())
                .hora(LocalTime.now())
                .estado(EstadoPedido.PENDIENTE)
                .tipo(TipoPedido.LOCAL)
                .total(3000.0)
                .usuario(usuarioMock)
                .nombreCliente("Juan")
                .build();

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioMock));
        when(productoRepository.findById(10L)).thenReturn(Optional.of(productoMock));
        when(pedidoRepository.save(any(Pedido.class))).thenReturn(pedidoGuardado);

        // When
        PedidoResponseDTO resultado = pedidoService.crear(dto);

        // Then
        assertNotNull(resultado);
        assertEquals(1L, resultado.getId());
        verify(pedidoRepository, atLeastOnce()).save(any(Pedido.class));
    }

    // -------------------------------------------------------
    // TEST 2: Delivery sin dirección debe lanzar excepción
    // -------------------------------------------------------
    @Test
    void crearPedido_deliverySinDireccion_debeLanzarBusinessException() {
        // Given
        PedidoRequestDTO dto = new PedidoRequestDTO();
        dto.setUsuarioId(1L);
        dto.setTipo(TipoPedido.DELIVERY);
        dto.setNombreCliente("María");
        dto.setDireccionEntrega(""); // vacía
        dto.setDetalles(List.of());

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioMock));

        // When & Then
        assertThrows(BusinessException.class, () -> pedidoService.crear(dto));
    }

    // -------------------------------------------------------
    // TEST 3: Producto inactivo en pedido debe lanzar excepción
    // -------------------------------------------------------
    @Test
    void crearPedido_conProductoInactivo_debeLanzarBusinessException() {
        // Given
        productoMock.setActivo(false); // producto deshabilitado

        DetallePedidoRequestDTO detalle = new DetallePedidoRequestDTO();
        detalle.setProductoId(10L);
        detalle.setCantidad(1);

        PedidoRequestDTO dto = new PedidoRequestDTO();
        dto.setUsuarioId(1L);
        dto.setTipo(TipoPedido.LOCAL);
        dto.setNombreCliente("Carlos");
        dto.setDetalles(List.of(detalle));

        Pedido pedidoVacio = Pedido.builder()
                .id(99L).fecha(LocalDate.now()).hora(LocalTime.now())
                .estado(EstadoPedido.PENDIENTE).tipo(TipoPedido.LOCAL)
                .total(0.0).usuario(usuarioMock).nombreCliente("Carlos")
                .build();

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioMock));
        when(pedidoRepository.save(any(Pedido.class))).thenReturn(pedidoVacio);
        when(productoRepository.findById(10L)).thenReturn(Optional.of(productoMock));

        // When & Then
        assertThrows(BusinessException.class, () -> pedidoService.crear(dto));
    }

    // -------------------------------------------------------
    // TEST 4: Usuario inexistente debe lanzar ResourceNotFoundException
    // -------------------------------------------------------
    @Test
    void crearPedido_usuarioInexistente_debeLanzarNotFoundException() {
        // Given
        PedidoRequestDTO dto = new PedidoRequestDTO();
        dto.setUsuarioId(999L);
        dto.setTipo(TipoPedido.LOCAL);
        dto.setDetalles(List.of());

        when(usuarioRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> pedidoService.crear(dto));
    }

    // -------------------------------------------------------
    // TEST 5: Transición de estado válida PENDIENTE -> PREPARACION
    // -------------------------------------------------------
    @Test
    void cambiarEstado_transicionValida_debeActualizarEstado() {
        // Given
        Pedido pedido = Pedido.builder()
                .id(1L).fecha(LocalDate.now()).hora(LocalTime.now())
                .estado(EstadoPedido.PENDIENTE).tipo(TipoPedido.LOCAL)
                .total(1500.0).usuario(usuarioMock).nombreCliente("Ana")
                .build();

        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenReturn(pedido);

        // When
        pedidoService.cambiarEstado(1L, EstadoPedido.PREPARACION);

        // Then
        assertEquals(EstadoPedido.PREPARACION, pedido.getEstado());
        verify(pedidoRepository).save(pedido);
    }

    // -------------------------------------------------------
    // TEST 6: Transición inválida debe lanzar BusinessException
    // -------------------------------------------------------
    @Test
    void cambiarEstado_transicionInvalida_debeLanzarBusinessException() {
        // Given — intentar pasar de LISTO directamente a PENDIENTE (inválido)
        Pedido pedido = Pedido.builder()
                .id(1L).fecha(LocalDate.now()).hora(LocalTime.now())
                .estado(EstadoPedido.LISTO).tipo(TipoPedido.LOCAL)
                .total(1500.0).usuario(usuarioMock).nombreCliente("Pedro")
                .build();

        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));

        // When & Then
        assertThrows(BusinessException.class,
                () -> pedidoService.cambiarEstado(1L, EstadoPedido.PENDIENTE));
    }

    // -------------------------------------------------------
    // TEST 7: Cancelar pedido entregado debe lanzar excepción
    // -------------------------------------------------------
    @Test
    void cancelar_pedidoEntregado_debeLanzarBusinessException() {
        // Given
        Pedido pedido = Pedido.builder()
                .id(1L).fecha(LocalDate.now()).hora(LocalTime.now())
                .estado(EstadoPedido.ENTREGADO).tipo(TipoPedido.LOCAL)
                .total(1500.0).usuario(usuarioMock).nombreCliente("Luis")
                .build();

        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));

        // When & Then
        assertThrows(BusinessException.class, () -> pedidoService.cancelar(1L));
    }
}
