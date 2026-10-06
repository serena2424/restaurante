# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Versionado semántico.

## [1.2.0] - 2026-10

Segunda ronda de pruebas (Reservas, Usuarios, Cocina, Mozo, Cajero y página pública).

### Agregado
- Pago anticipado: un pedido se puede cobrar mientras sigue en cocina; queda marcado como PAGADO y se entrega después.
- Cobro con "Paga con" y cálculo del vuelto.
- Corrección del medio de pago de un cobro (admin: cualquier venta; cajero: las de la caja abierta). Queda registrado el medio original, quién lo corrigió y cuándo.
- Reservas: editar, marcar "Llegó" y "No vino" desde el panel; límites de personas por reserva y por turno configurables.
- El mozo recibe un aviso con sonido cuando su pedido está listo y puede ver la cuenta de la mesa.
- Informe por período: accesos rápidos (Hoy, Esta semana, Este mes, Mes pasado), envíos aparte y ranking por opción.
- Endpoint `PATCH /api/config/estado-manual` para abrir o cerrar el local sin reenviar toda la configuración.
- Pruebas unitarias (servicios, horarios, redondeo, copias de seguridad) y prueba de permisos por rol; informe de cobertura con JaCoCo.
- Integración continua con GitHub Actions (`mvn verify`).

### Cambiado
- Paquete `com.barclub` renombrado a `com.gestorgastronomico`.
- Los montos se redondean al peso hacia arriba por línea, igual en el servidor, el panel y la web.
- Las reservas solo se aceptan dentro del horario de atención.
- "Borrar todo" de entregados pasa a "Ocultar entregados" (no borra datos).
- Las confirmaciones usan un cuadro propio en lugar del `confirm` del navegador.
- Fechas en formato dd/mm/aaaa y horas de 24 h en todo el panel.

### Corregido
- Abrir o cerrar el local desde el panel podía volver a guardar un costo de envío viejo.
- El costo de envío negativo se trata como cero.
- Fechas imposibles (31/02) responden un mensaje claro en lugar de un error genérico.
- Cocina solo ve los cancelados de las últimas 24 horas.

### Quitado
- Modo sin conexión de Usuarios (guardaba datos en el navegador).
- Lista de ventas duplicada oculta en Movimientos.

## [1.1.0] - 2026-10

Primera ronda de pruebas.

### Datos y caja
- Los usuarios no se borran: se desactivan y se pueden reactivar.
- Guardar la configuración no modifica el estado de la caja; cerrar una caja ya cerrada da error.
- Editar un pedido mantiene el envío en el total.
- "Abrir caja" registra la hora real de apertura.

### Seguridad y permisos
- Bloqueo por intentos fallidos por email.
- La API pública no expone emails del personal ni el estado de la caja.
- Validación de links de imagen y nombres de opción; los HTML escapan todo lo que muestran.
- La exportación a Excel no permite fórmulas en los nombres.
- Cocina y mozo no pueden cancelar pedidos ni editar reservas; el mozo solo ve sus pedidos.
- "Sin permiso" responde 403 (no cierra la sesión).
- Rol y estado del usuario se leen de la base en cada pedido.

### Validaciones
- Límites de largo con mensajes claros.
- El servidor rechaza pedidos web con el local cerrado, el tipo de entrega apagado o un medio de pago no aceptado.
- El informe por período agrupa por jornada.
