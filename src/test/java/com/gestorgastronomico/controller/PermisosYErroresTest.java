package com.gestorgastronomico.controller;

import com.gestorgastronomico.config.JacksonConfig;
import com.gestorgastronomico.config.JwtUtil;
import com.gestorgastronomico.config.SecurityConfig;
import com.gestorgastronomico.entity.ConfigLocal;
import com.gestorgastronomico.repository.UsuarioRepository;
import com.gestorgastronomico.service.ConfigLocalService;
import com.gestorgastronomico.service.PedidoService;
import com.gestorgastronomico.service.ReservaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controla los permisos por rol y que los errores lleguen con un mensaje claro.
 * Los servicios están simulados: no hace falta base de datos.
 */
@WebMvcTest(controllers = {PedidoController.class, ReservaController.class, ConfigLocalController.class})
@Import({SecurityConfig.class, JacksonConfig.class})
class PermisosYErroresTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private PedidoService pedidoService;
    @MockBean private ReservaService reservaService;
    @MockBean private ConfigLocalService configLocalService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private UsuarioRepository usuarioRepository;

    @Test
    void sinSesion_losPedidosActivosPidenLogin() throws Exception {
        mockMvc.perform(get("/api/pedidos/activos")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "COCINA")
    void cocina_veLosActivosPeroNoElHistorial() throws Exception {
        when(pedidoService.listarActivos()).thenReturn(List.of());

        mockMvc.perform(get("/api/pedidos/activos")).andExpect(status().isOk());
        mockMvc.perform(get("/api/pedidos")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/pedidos/fecha").param("fecha", "2026-10-06")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MOZO")
    void mozo_noPuedeCancelarNiCambiarEstados() throws Exception {
        mockMvc.perform(patch("/api/pedidos/1/cancelar")).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/pedidos/1/estado").param("estado", "LISTO")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COCINA")
    void cocina_noPuedeVerReservas() throws Exception {
        mockMvc.perform(get("/api/reservas/hoy")).andExpect(status().isForbidden());
    }

    @Test
    void laConfiguracionSinImagenes_esPublica() throws Exception {
        when(configLocalService.obtenerSinImagenes())
                .thenReturn(ConfigLocal.builder().id(1L).nombre("La Esquina").build());

        mockMvc.perform(get("/api/config/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("La Esquina"));
    }

    @Test
    void fechaImposible_respondeConMensajeClaro() throws Exception {
        String reserva = """
                {"nombreCliente":"Ana","fecha":"2027-02-31","hora":"21:00","cantidadPersonas":2,"telefono":"3447123456"}
                """;

        mockMvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(reserva))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La fecha o la hora no es válida."));
    }

    @Test
    void datoFaltante_respondeConElMensajeDelCampo() throws Exception {
        String reserva = """
                {"nombreCliente":"Ana","fecha":"2027-02-10","hora":"21:00","telefono":"3447123456"}
                """;

        mockMvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(reserva))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Indicá la cantidad de personas"));
    }
}
