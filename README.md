# Store with microservices

**English** · [Español](README.es.md)

A simple store split into microservices with **Spring Boot 3.5** and **Spring Cloud 2025.0**. It
shows four patterns with scenarios you can reproduce: **Config Server**, **API Gateway**, **Circuit
Breaker** (Resilience4j) and an orchestrated **Saga** with compensations.

```
                         ┌──────────────┐
           client ──────▶│   gateway    │ :8080   Spring Cloud Gateway
                         └──────┬───────┘
              ┌─────────────────┼──────────────────┐
              ▼                 ▼                  ▼
     ┌────────────────┐ ┌────────────────┐ ┌────────────────┐
     │ pedidos :8081  │ │ inventario     │ │ pagos :8083    │
     │ orchestrates   │─▶ :8082          │ │                │
     │ the saga       │ │ reserve /      │ │ charge /       │
     │ circuit breaker│─┼────────────────┼─▶ refund         │
     └───────┬────────┘ └───────┬────────┘ └───────┬────────┘
             ▼                  ▼                  ▼
          [pedidos]       [inventario]          [pagos]        one database per service (PostgreSQL)

     config-server :8888 ◀── config-repo/   configuration for every service
```

## What each piece does

| Service | Port | Responsibility |
|---|---|---|
| `config-server` | 8888 | Serves each service its configuration from `config-repo/` |
| `gateway` | 8080 | Single entry point; routes `/api/pedidos` and the read-only (GET) endpoints of `/api/inventario` and `/api/pagos` |
| `pedidos-service` | 8081 | Creates orders and orchestrates the saga: reserve stock → charge |
| `inventario-service` | 8082 | Catalog and stock reservations (with its compensation: release) |
| `pagos-service` | 8083 | Payments (with its compensation: refund). Rejects amounts over 1000 |

## Tech stack

Java 21 · Spring Boot 3.5 · Spring Cloud Config · Spring Cloud Gateway · Resilience4j · Spring Data
JPA · PostgreSQL 16 · Lombok · springdoc-openapi (Swagger) · JUnit 5 · Mockito · Docker Compose ·
GitHub Actions

## Running it

Only Docker is needed:

```bash
docker compose up --build -d
```

The first run takes a few minutes because it builds the five services. After that it starts in under
a minute; `pedidos-service` is the last one to be ready. To check everything is up:

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8080/api/inventario/productos
```

The first one should return `"status":"UP"`. If an order through the gateway returns 500 in the first
few seconds, the orders service is still starting.

To stop it: `docker compose down` (add `-v` to also delete the data).

**Swagger** for each service: http://localhost:8081/swagger-ui.html (orders),
http://localhost:8082/swagger-ui.html (inventory) and http://localhost:8083/swagger-ui.html (payments).

## Seeing each pattern in action

All commands go through the gateway (port 8080). On Windows, run them in Git Bash. The initial
catalog has a mouse (S/ 45.90), keyboard (S/ 189.00), monitor (S/ 649.00) and laptop (S/ 2899.00).

### Saga: order confirmed

```bash
curl -X POST http://localhost:8080/api/pedidos -H "Content-Type: application/json" \
  -d '{"cliente":"Ana","lineas":[{"productoId":2,"cantidad":1},{"productoId":1,"cantidad":2}]}'
```

Returns `"estado": "CONFIRMADO"` with `"total": 280.80`, and the keyboard and mouse stock goes down.

### Saga: payment rejected and compensation

The laptop costs more than the payment limit (1000). Stock is reserved, the charge is rejected and the
saga **releases the reservation**:

```bash
curl -X POST http://localhost:8080/api/pedidos -H "Content-Type: application/json" \
  -d '{"cliente":"Luis","lineas":[{"productoId":4,"cantidad":1}]}'
```

Returns `"estado": "CANCELADO"` with `"motivo": "PAGO_RECHAZADO: ..."`. The laptop stock goes back
to 3, and `GET /api/inventario/reservas/{id}` shows the reservation as `LIBERADA`.

### Saga: out of stock

```bash
curl -X POST http://localhost:8080/api/pedidos -H "Content-Type: application/json" \
  -d '{"cliente":"Rosa","lineas":[{"productoId":1,"cantidad":60}]}'
```

It is cancelled with `STOCK_INSUFICIENTE` without attempting the charge: there were no steps to undo.

### Circuit breaker

Stop inventory and send three orders:

```bash
docker compose stop inventario-service
curl -X POST http://localhost:8080/api/pedidos -H "Content-Type: application/json" \
  -d '{"cliente":"Ana","lineas":[{"productoId":3,"cantidad":1}]}'
```

- The first two end up `COMPENSANDO` with `LIBERAR_STOCK` pending: the request went out and it is
  unknown whether the reservation was made, so it has to be released once inventory is back.
- After three failures the circuit opens. The third order is cancelled immediately with
  `circuito abierto, la llamada no se intentó` (circuit open, the call was not attempted).

The circuit state is at `http://localhost:8081/actuator/circuitbreakers`. Now start inventory again:

```bash
docker compose start inventario-service
```

In under a minute, with no intervention: the circuit goes to `HALF_OPEN` and then `CLOSED`, the
pending orders end up `CANCELADO` and a new order is confirmed again.

### Compensation that is retried

```bash
docker compose stop pagos-service
curl -X POST http://localhost:8080/api/pedidos -H "Content-Type: application/json" \
  -d '{"cliente":"Luis","lineas":[{"productoId":2,"cantidad":2}]}'
docker compose start pagos-service
```

Stock is released right away, but the refund stays pending (`REEMBOLSAR_PAGO`) because payments is
not responding. When payments comes back, the retry process completes it and the order moves to
`CANCELADO`. The payment is marked `ANULADO`: if that charge arrived late, it would not be approved.

### Config Server

```bash
curl http://localhost:8888/pagos-service/default
```

Shows the configuration that payments receives (`pagos-service.yml` plus the shared
`application.yml`). To change the approval limit, edit `config-repo/pagos-service.yml` and restart
only that service with `docker compose restart pagos-service`. No rebuild needed.

### Gateway

```bash
curl http://localhost:8080/actuator/gateway/routes
```

Lists the loaded routes. Every response that goes through the gateway carries the
`X-Servido-Por: gateway` header. Only the read endpoints (GET) of inventory and payments are
published: reserve, release, charge and refund are called internally by pedidos-service, so a
`POST /api/pagos/1/reembolso` through the gateway returns 404.

## Tests

```bash
mvn verify
```

Requires Java 21 and Maven. 43 tests that do not need Docker (they use in-memory H2 and fake HTTP
servers):

- **Saga** (`SagaPedidoTest`, `PedidoTest`): every path. Confirmed, out of stock, payment rejected,
  circuit open, inventory not responding, payments not responding (refunds and then releases, in that
  order), compensation that fails and is retried, interrupted saga and unexpected error (responds with
  the order id). It also checks that the retry process does not step on a compensation still in
  progress.
- **Circuit breaker** (`CircuitBreakerTest`): against a fake inventory. Three errors open the circuit
  and the fourth call never goes out; a business 409 does not open it; a 400 from a broken contract is
  not mistaken for missing stock; a slow response counts as a timeout failure.
- **Idempotency** (`ReservaServiceTest`, `PagoApiTest`): reserving, releasing, charging and refunding
  twice duplicates nothing, and an operation that arrives after its compensation is rejected.
- **Gateway** (`EnrutamientoTest`): the real routes from `config-repo/gateway.yml`, including that the
  saga operations are not published. **Config Server** (`ConfigServerApplicationTest`).

GitHub Actions runs the tests and builds the images on every push (`.github/workflows/ci.yml`).

## Structure

```
config-repo/            configuration for every service (served by config-server)
config-server/          Spring Cloud Config Server
gateway/                Spring Cloud Gateway
inventario-service/     products and stock reservations
pagos-service/          payments and refunds
pedidos-service/        orders and the saga orchestrator
  client/               InventarioClient and PagosClient, with @CircuitBreaker
  service/SagaPedido    the saga steps and their compensations
  service/ReintentosSaga  retries compensations and resumes interrupted sagas
docker-compose.yml      PostgreSQL + the five services
```
