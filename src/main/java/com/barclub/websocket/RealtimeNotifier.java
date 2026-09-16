package com.barclub.websocket;

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
 * Punto único desde el que los services avisan "esto cambió" a todos los
 * paneles conectados. Deliberadamente NO manda los datos en sí (ver
 * WebSocketConfig) — solo el tipo de evento y una marca de tiempo, para que
 * el panel sepa qué volver a pedir por la API normal.
 *
 * Si el envío falla por lo que sea (por ejemplo, si en algún entorno el
 * WebSocket no está disponible), se ignora en silencio: el polling de
 * respaldo del panel se encarga igual, así que un fallo acá nunca debe
 * interrumpir la operación real (crear un pedido, cobrar, reservar, etc.).
 *
 * IMPORTANTE — orden con la transacción: los services que llaman a este
 * componente (PedidoService, ReservaService, VentaService) son
 * @Transactional a nivel de clase. Si mandáramos el aviso apenas se llama
 * acá, saldría ANTES de que la transacción termine de confirmarse en la
 * base — el panel recibiría el aviso, iría a pedir los datos nuevos, y
 * todavía no los encontraría (porque técnicamente todavía no están
 * guardados del todo). Esto pasaba de verdad: el aviso llegaba pero no
 * mostraba nada nuevo, y recién aparecía al recargar más tarde. Por eso acá
 * se espera a que la transacción confirme (afterCommit) antes de avisar; si
 * no hay ninguna transacción activa, se manda directo.
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
