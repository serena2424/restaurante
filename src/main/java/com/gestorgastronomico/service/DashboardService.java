package com.gestorgastronomico.service;

import com.gestorgastronomico.dto.ResumenDiaDTO;
import com.gestorgastronomico.entity.EstadoPedido;
import com.gestorgastronomico.entity.Reserva;
import com.gestorgastronomico.repository.PedidoRepository;
import com.gestorgastronomico.repository.ReservaRepository;
import com.gestorgastronomico.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final PedidoRepository pedidoRepository;
    private final VentaRepository ventaRepository;
    private final ReservaRepository reservaRepository;

    public ResumenDiaDTO resumenDelDia(LocalDate fecha) {
        Double totalVentas = ventaRepository.sumTotalByJornada(fecha);
        Long cantidadPedidos = ventaRepository.countByJornada(fecha);

        List<com.gestorgastronomico.entity.Pedido> activos = pedidoRepository.findPedidosActivos();
        long pedidosActivos = activos.size();
        long enPreparacion = activos.stream()
                .filter(p -> p.getEstado() == EstadoPedido.PREPARACION)
                .count();
        long entregados = pedidoRepository.findByFechaAndEstado(fecha, EstadoPedido.ENTREGADO).size();
        long reservasDelDia = reservaRepository
                .findByFechaAndEstado(fecha, Reserva.EstadoReserva.CONFIRMADA).size();

        double ticketPromedio = (cantidadPedidos != null && cantidadPedidos > 0 && totalVentas != null)
                ? totalVentas / cantidadPedidos
                : 0.0;

        return ResumenDiaDTO.builder()
                .fecha(fecha)
                .totalVentas(totalVentas != null ? totalVentas : 0.0)
                .cantidadPedidos(cantidadPedidos != null ? cantidadPedidos : 0L)
                .pedidosActivos(pedidosActivos)
                .pedidosEnPreparacion(enPreparacion)
                .pedidosEntregados(entregados)
                .reservasDelDia(reservasDelDia)
                .ticketPromedio(ticketPromedio)
                .build();
    }
}
