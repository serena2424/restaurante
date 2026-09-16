package com.barclub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración para PedidoController.
 * Levanta el contexto completo de Spring y valida que el contrato
 * de API (status HTTP + estructura JSON) se cumpla en cada endpoint.
 *
 * NOTA: Requiere que el backend esté conectado a la DB (barclub_db).
 * Si no hay DB disponible en CI, usar @SpringBootTest con un H2 en memoria
 * o mockear los services con @MockBean.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PedidoControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // -------------------------------------------------------
    // TEST 1: GET /api/pedidos/activos — debe retornar 200
    // -------------------------------------------------------
    @Test
    void listarPedidosActivos_debeRetornar200() throws Exception {
        mockMvc.perform(get("/api/pedidos/activos")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // -------------------------------------------------------
    // TEST 2: POST /api/pedidos — con datos válidos debe retornar 201
    // (requiere usuarioId=1 existente en la DB de test)
    // -------------------------------------------------------
    @Test
    void crearPedido_datosValidos_debeRetornar201() throws Exception {
        Map<String, Object> detalle = Map.of(
                "productoId", 1,
                "cantidad", 2
        );

        Map<String, Object> body = Map.of(
                "usuarioId", 1,
                "tipo", "LOCAL",
                "nombreCliente", "Test Integración",
                "telefonoCliente", "3447000000",
                "detalles", List.of(detalle)
        );

        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.tipo").value("LOCAL"));
    }

    // -------------------------------------------------------
    // TEST 3: POST /api/pedidos — JSON incompleto debe retornar 400
    // Valida que @Valid funciona y el ControllerAdvice responde bien
    // -------------------------------------------------------
    @Test
    void crearPedido_sinCamposObligatorios_debeRetornar400() throws Exception {
        // Body vacío — faltarán todos los campos obligatorios
        String bodyVacio = "{}";

        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyVacio))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------
    // TEST 4: GET /api/pedidos/{id} — ID inexistente debe retornar 404
    // Valida que el ControllerAdvice mapea ResourceNotFoundException a 404
    // -------------------------------------------------------
    @Test
    void obtenerPedido_idInexistente_debeRetornar404() throws Exception {
        mockMvc.perform(get("/api/pedidos/999999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists());
    }

    // -------------------------------------------------------
    // TEST 5: PATCH /api/pedidos/{id}/cancelar — ID inexistente -> 404
    // -------------------------------------------------------
    @Test
    void cancelarPedido_idInexistente_debeRetornar404() throws Exception {
        mockMvc.perform(patch("/api/pedidos/999999/cancelar")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------
    // TEST 6: GET /api/pedidos/estado/{estado} — estado inválido -> 400
    // -------------------------------------------------------
    @Test
    void listarPorEstado_estadoInvalido_debeRetornar400() throws Exception {
        mockMvc.perform(get("/api/pedidos/estado/ESTADO_INEXISTENTE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
