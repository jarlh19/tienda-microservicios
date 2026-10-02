# Tienda con microservicios

[English](README.md) · **Español**

Una tienda simple dividida en microservicios con **Spring Boot 3.5** y **Spring Cloud 2025.0**. Muestra
cuatro patrones con casos que se pueden reproducir: **Config Server**, **API Gateway**, **Circuit
Breaker** (Resilience4j) y **Saga** orquestada con compensaciones.

```
                         ┌──────────────┐
          cliente ──────▶│   gateway    │ :8080   Spring Cloud Gateway
                         └──────┬───────┘
              ┌─────────────────┼──────────────────┐
              ▼                 ▼                  ▼
     ┌────────────────┐ ┌────────────────┐ ┌────────────────┐
     │ pedidos :8081  │ │ inventario     │ │ pagos :8083    │
     │ orquesta la    │─▶ :8082          │ │                │
     │ saga           │ │ reservar /     │ │ cobrar /       │
     │ circuit breaker│─┼────────────────┼─▶ reembolsar     │
     └───────┬────────┘ └───────┬────────┘ └───────┬────────┘
             ▼                  ▼                  ▼
          [pedidos]       [inventario]          [pagos]        una base por servicio (PostgreSQL)

     config-server :8888 ◀── config-repo/   configuración de todos los servicios
```

## Qué hace cada pieza

| Servicio | Puerto | Responsabilidad |
|---|---|---|
| `config-server` | 8888 | Entrega a cada servicio su configuración desde `config-repo/` |
| `gateway` | 8080 | Puerta de entrada única; enruta `/api/pedidos` y las consultas (GET) de `/api/inventario` y `/api/pagos` |
| `pedidos-service` | 8081 | Crea pedidos y orquesta la saga: reservar stock → cobrar |
| `inventario-service` | 8082 | Catálogo y reservas de stock (con su compensación: liberar) |
| `pagos-service` | 8083 | Cobros (con su compensación: reembolsar). Rechaza montos mayores a 1000 |


## Tecnologías

Java 21 · Spring Boot 3.5 · Spring Cloud Config · Spring Cloud Gateway · Resilience4j · Spring Data
JPA · PostgreSQL 16 · Lombok · springdoc-openapi (Swagger) · JUnit 5 · Mockito · Docker Compose ·
GitHub Actions

## Cómo levantarlo

Solo hace falta Docker:

```bash
docker compose up --build -d
```

La primera vez tarda unos minutos porque compila los cinco servicios. Después arranca en menos de un
minuto; `pedidos-service` es el último en quedar listo. Para comprobar que todo está arriba:

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8080/api/inventario/productos
```

El primero debe responder `"status":"UP"`. Si un pedido por el gateway devuelve 500 en los primeros
segundos, es porque pedidos todavía está arrancando.

Para apagarlo: `docker compose down` (agrega `-v` para borrar también los datos).

**Swagger** de cada servicio: http://localhost:8081/swagger-ui.html (pedidos),
http://localhost:8082/swagger-ui.html (inventario) y http://localhost:8083/swagger-ui.html (pagos).

## Cómo ver cada patrón funcionando

Todos los comandos van por el gateway (puerto 8080). En Windows, córrelos en Git Bash. El catálogo
inicial tiene mouse (S/ 45.90), teclado (S/ 189.00), monitor (S/ 649.00) y laptop (S/ 2899.00).

### Saga: pedido confirmado

```bash
curl -X POST http://localhost:8080/api/pedidos -H "Content-Type: application/json" \
  -d '{"cliente":"Ana","lineas":[{"productoId":2,"cantidad":1},{"productoId":1,"cantidad":2}]}'
```

Responde `"estado": "CONFIRMADO"` con `"total": 280.80`, y el stock del teclado y del mouse baja.

### Saga: pago rechazado y compensación

La laptop cuesta más que el límite de pagos (1000). Se reserva el stock, el cobro se rechaza y la
saga **libera la reserva**:

```bash
curl -X POST http://localhost:8080/api/pedidos -H "Content-Type: application/json" \
  -d '{"cliente":"Luis","lineas":[{"productoId":4,"cantidad":1}]}'
```

Responde `"estado": "CANCELADO"` con `"motivo": "PAGO_RECHAZADO: ..."`. El stock de la laptop vuelve
a 3, y `GET /api/inventario/reservas/{id}` muestra la reserva en `LIBERADA`.

### Saga: sin stock

```bash
curl -X POST http://localhost:8080/api/pedidos -H "Content-Type: application/json" \
  -d '{"cliente":"Rosa","lineas":[{"productoId":1,"cantidad":60}]}'
```

Se cancela con `STOCK_INSUFICIENTE` sin intentar el cobro: no hubo pasos que deshacer.

### Circuit breaker

Apaga inventario y manda tres pedidos:

```bash
docker compose stop inventario-service
curl -X POST http://localhost:8080/api/pedidos -H "Content-Type: application/json" \
  -d '{"cliente":"Ana","lineas":[{"productoId":3,"cantidad":1}]}'
```

- Los dos primeros quedan en `COMPENSANDO` con `LIBERAR_STOCK` pendiente: la petición salió y no se
  sabe si la reserva llegó a hacerse, así que hay que liberarla cuando inventario vuelva.
- Con tres fallas el circuito se abre. El tercero se cancela al instante con
  `circuito abierto, la llamada no se intentó`.

El estado del circuito se ve en `http://localhost:8081/actuator/circuitbreakers`. Ahora prende
inventario:

```bash
docker compose start inventario-service
```

En menos de un minuto, sin intervenir: el circuito pasa a `HALF_OPEN` y luego a `CLOSED`, los
pedidos pendientes terminan en `CANCELADO` y un pedido nuevo vuelve a confirmarse.

### Compensación que se reintenta

```bash
docker compose stop pagos-service
curl -X POST http://localhost:8080/api/pedidos -H "Content-Type: application/json" \
  -d '{"cliente":"Luis","lineas":[{"productoId":2,"cantidad":2}]}'
docker compose start pagos-service
```

El stock se libera de inmediato, pero el reembolso queda pendiente (`REEMBOLSAR_PAGO`) porque pagos
no responde. Cuando pagos vuelve, el proceso de reintentos lo completa y el pedido pasa a `CANCELADO`.
El pago queda `ANULADO`: si ese cobro llegara tarde, no se aprobaría.

### Config Server

```bash
curl http://localhost:8888/pagos-service/default
```

Muestra la configuración que recibe pagos (`pagos-service.yml` más la común `application.yml`).
Para cambiar el límite de aprobación, edita `config-repo/pagos-service.yml` y reinicia solo ese
servicio con `docker compose restart pagos-service`. No hace falta recompilar.

### Gateway

```bash
curl http://localhost:8080/actuator/gateway/routes
```

Lista las rutas cargadas. Cada respuesta que pasa por el gateway trae la cabecera
`X-Servido-Por: gateway`. De inventario y pagos solo se publican las consultas (GET): reservar,
liberar, cobrar y reembolsar los llama pedidos-service por dentro, así que un
`POST /api/pagos/1/reembolso` por el gateway responde 404.

## Pruebas

```bash
mvn verify
```

Requiere Java 21 y Maven. Son 43 pruebas que no necesitan Docker (usan H2 en memoria y servidores
HTTP falsos):

- **Saga** (`SagaPedidoTest`, `PedidoTest`): todos los caminos. Confirmado, sin stock, pago
  rechazado, circuito abierto, inventario sin respuesta, pagos sin respuesta (reembolsa y luego
  libera, en ese orden), compensación que falla y se reintenta, saga interrumpida y error inesperado
  (responde con el id del pedido). También comprueba que el proceso de reintentos no pisa una
  compensación que todavía está en curso.
- **Circuit breaker** (`CircuitBreakerTest`): contra un inventario falso. Tres errores abren el
  circuito y la cuarta llamada ni sale; un 409 de negocio no lo abre; un 400 por contrato roto no se
  confunde con falta de stock; una respuesta lenta cuenta como falla por timeout.
- **Idempotencia** (`ReservaServiceTest`, `PagoApiTest`): reservar, liberar, cobrar y reembolsar dos
  veces no duplica nada, y una operación que llega después de su compensación se rechaza.
- **Gateway** (`EnrutamientoTest`): las rutas reales de `config-repo/gateway.yml`, incluido que las
  operaciones de la saga no se publican. **Config Server** (`ConfigServerApplicationTest`).

GitHub Actions corre las pruebas y construye las imágenes en cada push (`.github/workflows/ci.yml`).

## Estructura

```
config-repo/            configuración de todos los servicios (la sirve config-server)
config-server/          Spring Cloud Config Server
gateway/                Spring Cloud Gateway
inventario-service/     productos y reservas de stock
pagos-service/          cobros y reembolsos
pedidos-service/        pedidos y orquestador de la saga
  client/               InventarioClient y PagosClient, con @CircuitBreaker
  service/SagaPedido    los pasos de la saga y sus compensaciones
  service/ReintentosSaga  reintenta compensaciones y retoma sagas interrumpidas
docker-compose.yml      PostgreSQL + los cinco servicios
```
