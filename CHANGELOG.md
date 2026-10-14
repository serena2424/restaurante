# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Versionado semántico.

## [1.4.0] - 2026-10

Comanda por mesa con tarjetas.

### Agregado
- Una mesa tiene una sola comanda abierta hasta cobrarla. Lo que se carga después para esa mesa se suma a la misma comanda.
- Cada producto de la mesa es una tarjeta con su estado: nuevo, en preparación, listo, entregado. Cocina avanza cada plato con un toque y el mozo lo entrega.
- Las categorías marcadas "No va a cocina" (bebidas) no llegan a cocina: las entrega el mozo y suman en la cuenta.
- Un plato cancelado queda tachado en la comanda y no se cobra. Una comanda cancelada queda tachada a la vista.
- Cocina: cajas por mesa, sonido cuando la mesa agrega o cancela un plato, impresión opcional de lo nuevo.
- Cobro de la mesa entera; si queda algo sin entregar, avisa antes. La mesa se cierra sola cuando está cobrada y todo entregado.

### Cambiado
- Mozo y cocina pueden cancelar comandas de mesa (retiro y delivery los sigue cancelando el cajero).
- El ranking de productos no cuenta los platos cancelados.

## [1.3.0] - 2026-10

Tercera ronda de pruebas (todas las pestañas y roles).

### Agregado
- Aviso en Pedidos y Ventas cuando la caja lleva más de 24 horas abierta.
- "Reservas de hoy" incluye las de la madrugada del turno de la noche, marcadas "(madrugada)".
- La web muestra el máximo de personas por reserva y avisa antes de enviar.

### Cambiado
- Las cajas se muestran en cada día en que tuvieron ventas, con todas sus ventas y de la más vieja a la más nueva.
- Los precios del menú van en pesos enteros.
- Los emails de usuario se guardan en minúscula y se comparan sin importar mayúsculas.
- Quien carga un pedido no recibe el aviso de "pedido nuevo" de ese pedido.
- Si un cobro corregido vuelve a su medio original, deja de figurar como corregido; se registra el nombre de quien corrige.

### Corregido
- Cocina no puede crear pedidos.
- Un usuario con mayúsculas en el email no se podía editar.
- Al cobrar no se puede confirmar si "Paga con" es menor que el total.
- Al corregir el cobro de un delivery ya no se ofrece tarjeta.
- Las categorías de Menú y de Nuevo pedido ya no se afectan entre sí.
- Los horarios de Nuevo pedido se recalculan al abrir la pestaña.
- El tablero y la pantalla de cocina se actualizan siempre después de cada acción.
- Mensajes de error de Configuración con un solo punto.
- Reservas para después de medianoche con la fecha de hoy: mensaje que explica elegir el día siguiente.

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
