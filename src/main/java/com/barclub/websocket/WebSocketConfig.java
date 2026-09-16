package com.barclub.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Canal de avisos en tiempo real (pedidos y reservas). El panel se conecta acá
 * una sola vez al entrar y se queda escuchando; cuando algo cambia en el
 * servidor (nuevo pedido, cambio de estado, nueva reserva, cancelación), se
 * empuja un aviso al instante a todos los paneles conectados, en vez de que
 * cada pantalla tenga que ir preguntando cada 20-30 segundos.
 *
 * Importante: por este canal NUNCA viaja información sensible (nombres,
 * teléfonos, montos, etc.) — solo un aviso vacío tipo "algo cambió en
 * pedidos" o "algo cambió en reservas". Cuando el panel recibe ese aviso,
 * vuelve a pedir los datos reales por la API normal (con su token de sesión
 * de siempre). Así el WebSocket no necesita manejar autenticación por su
 * cuenta ni exponer datos protegidos.
 *
 * El polling periódico que ya existía en el panel se mantiene como
 * respaldo: si el WebSocket no llega a conectar (red restrictiva, backend
 * viejo sin este endpoint, etc.) todo sigue funcionando como antes, solo
 * que con el retraso de siempre en vez de al instante.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS()
                // El frontend (Netlify) y el backend (Railway) son dominios
                // distintos. La app no usa cookies de sesión para nada (el
                // login es con token, no con sesión de servidor), así que
                // sacamos ese requisito acá: si se deja en true (default),
                // el navegador puede bloquear la cookie entre-dominios y la
                // conexión nunca termina de establecerse, aunque el endpoint
                // esté activo y respondiendo bien a /ws/info.
                .setSessionCookieNeeded(false);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Los paneles se suscriben a estos "canales" (topics).
        registry.enableSimpleBroker("/topic");
        // No se usa en este proyecto (el panel no le manda mensajes al
        // servidor por este canal), pero es el prefijo estándar de Spring.
        registry.setApplicationDestinationPrefixes("/app");
    }
}
