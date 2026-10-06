# Cambios — revisión de octubre 2026

Arreglos de la revisión completa (guía de bugs C1–C5, S1–S6, P1–P6, V1–V8).

## Antes de subir

1. **Railway → Variables**: cargá `JWT_SECRET` (32+ caracteres al azar) y `MASTER_KEY` (una clave tuya).
   - Sin `JWT_SECRET`, el sistema usa una clave al azar y las sesiones se cierran en cada reinicio.
   - Sin `MASTER_KEY` (o con la de fábrica), "¿Olvidaste tu contraseña?" queda desactivado.
2. `DB_PASSWORD` ya no tiene valor por defecto en el código: en Railway ya la tenés cargada; para correrlo en tu compu, definila como variable de entorno.
3. Las contraseñas que estaban escritas en `application.properties` y `application-test.properties` siguen en el historial de git: si las usás en algún lado, cambialas.

## Datos y plata
- C1: los usuarios ya no se borran, se **desactivan** (no pueden entrar; su historial queda). Se pueden reactivar.
- C2: guardar la Configuración ya no toca el estado de la caja. Cerrar una caja ya cerrada da error en vez de crear una caja vacía.
- C3/V8: editar un pedido mantiene el envío en el total y guarda el costo de lo agregado.
- C4: "Abrir caja" registra la hora real de apertura.
- C5: el recálculo de jornadas al arrancar respeta la caja abierta.

## Seguridad y permisos
- S1: el bloqueo por intentos fallidos es por email (ya no bloquea a todos).
- S3/S4: la API pública ya no muestra emails del personal, estado de caja ni el usuario interno del pedido.
- S5: el servidor solo acepta links de imagen reales y nombres de variante sin HTML; los HTML escapan todo lo que muestran.
- S6: la exportación a Excel no deja que un nombre se ejecute como fórmula.
- P1: se quitó el endpoint que permitía al cajero borrar las ventas del día.
- P2: cocina ya no puede cancelar pedidos por el cambio de estado.
- P3: cocina y mozo ya no pueden editar reservas.
- P4: el pedido queda a nombre de quien inició sesión (se ignora el usuarioId que manda el navegador).
- P5: "sin permiso" responde 403 y ya no saca a la persona al login.
- P6: el mozo solo recibe sus propios pedidos.
- El rol y el estado de cada usuario se leen de la base en cada pedido: desactivar a alguien o cambiarle el rol aplica al instante.

## Validaciones
- V1: límites de largo con mensaje claro (antes error 500).
- V2: no se aceptan opciones inventadas.
- V3: el servidor rechaza pedidos web con el local cerrado (manual), delivery/retiro apagados, medio de pago no aceptado o delivery con tarjeta.
- V7: el Informe por período agrupa por jornada, igual que Movimientos.
