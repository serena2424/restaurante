package com.gestorgastronomico.websocket;

import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Avisa a los paneles conectados que algo cambió (pedidos o reservas). No
 * manda datos: el panel vuelve a pedirlos por la API con su sesión.
 *
 * El aviso sale después de que la transacción se confirma; si saliera antes,
 * el panel pediría los datos y todavía no los encontraría. Si el envío falla,
 * se ignora: el panel también consulta cada tanto por su cuenta.
 */
@Component
@RequiredArgsConstructor
public class RealtimeNotifier {

    private static final Logger log = LoggerFactory.getLogger(RealtimeNotifier.class);

    private final SimpMessagingTemplate template;

    public void avisarPedidos() {
        enviar("/topic/pedidos", "pedidos");
    }

    public void avisarReservas() {
        enviar("/topic/reservas", "reservas");
    }

    private void enviar(String destino, String tipo) {
        boolean transaccionActiva = TransactionSynchronizationManager.isSynchronizationActive();
        log.info("AVISO EN TIEMPO REAL: solicitado para '{}' (transacción activa: {})", tipo, transaccionActiva);
        if (transaccionActiva) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("AVISO EN TIEMPO REAL: afterCommit disparado para '{}', enviando ahora", tipo);
                    enviarAhora(destino, tipo);
                }
            });
        } else {
            enviarAhora(destino, tipo);
        }
    }

    private void enviarAhora(String destino, String tipo) {
        try {
            template.convertAndSend(destino, Map.of("tipo", tipo, "en", Instant.now().toString()));
            log.info("AVISO EN TIEMPO REAL: enviado OK a {} (tipo={})", destino, tipo);
        } catch (Exception e) {
            log.warn("No se pudo enviar el aviso en tiempo real de '{}' (el polling de respaldo lo cubre igual): {}", tipo, e.getMessage());
        }
    }
}
