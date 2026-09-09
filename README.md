# OrderFlow

[![CI](https://github.com/Abhilash-31/orderflow/actions/workflows/ci.yml/badge.svg)](https://github.com/Abhilash-31/orderflow/actions/workflows/ci.yml)

Event-driven order fulfillment platform — Java 21, Spring Boot 3, Apache Kafka, Postgres, Docker. Four microservices coordinate an order through inventory reservation, payment, and shipping using a choreographed Saga, with oversell-safe stock reservation and compensating transactions on failure.

This exists as a portfolio project demonstrating distributed-systems patterns that come up constantly in backend interviews: preventing overselling under concurrent load, keeping data consistent across services that each own their own database, and handling failure gracefully instead of leaving the system in a half-finished state.

## Architecture

```
                         ┌─────────────────┐
   POST /orders  ──────► │  Order Service   │──────► order.created (Kafka)
                         │  (orderdb)       │
                         └─────────▲────────┘
                                   │ status updates as events arrive
        ┌──────────────────────────┼───────────────────────────┐
        │                          │                            │
        ▼                          ▼                            ▼
┌───────────────┐         ┌────────────────┐          ┌──────────────────┐
│ Inventory Svc  │ ──────► │  Payment Svc    │ ───────► │  Shipping Svc     │
│ (inventorydb)  │ events  │  (paymentdb)    │ events   │  (shippingdb)     │
└───────────────┘         └────────────────┘          └──────────────────┘
   reserves stock            authorizes payment          creates shipment
   (conditional UPDATE,      (mock; deterministic         + publishes
   oversell-safe)            decline threshold)           order.shipped

On failure at any step → OrderCancelled published → Inventory releases
reservation, Payment refunds if it had already authorized.
```

Each service owns its own database — no service reaches into another's tables, and no service calls another synchronously. Everything downstream of the initial `POST /orders` is asynchronous, coordinated entirely through Kafka events. See `common-events/` for the full event contract.

### The Saga, step by step

1. **Order Service** accepts `POST /orders`, persists the order as `PENDING`, publishes `OrderCreated`.
2. **Inventory Service** consumes it and attempts to reserve stock for every line item in one DB transaction, using an atomic conditional `UPDATE ... WHERE (available - reserved) >= quantity` per item (see `InventoryRepository.tryReserve`) — this is what makes concurrent reservations for the same product safe without an explicit lock. Publishes `InventoryReserved` or `InventoryReservationFailed`.
3. **Payment Service** — which has been keeping its own local copy of each order's total (sourced from `OrderCreated`, not a synchronous call to Order Service) — reacts to `InventoryReserved` by authorizing (mocked; deterministically declines orders over `payment.decline-threshold`). Publishes `PaymentAuthorized` or `PaymentFailed`.
4. **Shipping Service** reacts to `PaymentAuthorized` by creating a shipment and publishing `OrderShipped`, which Order Service consumes to mark the order `COMPLETED`.
5. **On any failure** (`InventoryReservationFailed` or `PaymentFailed`), Order Service marks the order `CANCELLED` and publishes `OrderCancelled`. Inventory Service releases any reservation for that order; Payment Service refunds if it had already authorized. These are the compensating transactions that make the Saga pattern actually work — see `OrderSagaListener`, `InventoryEventListener.onOrderCancelled`, and `PaymentEventListener.onOrderCancelled`.

This is **choreography**, not orchestration: there's no central coordinator telling each service what to do next. Each service just reacts to events from the ones before it. (A Saga **orchestrator** implementation is a natural stretch goal — see Roadmap.)

## Running it locally

Requires Docker and Docker Compose.

```bash
docker compose up --build
```

This starts Kafka (KRaft mode, no Zookeeper), one Postgres instance hosting all four service databases, and all four services. Give it 30-60 seconds on first run while Maven builds each service image.

**Seed some stock**, then place an order:

```bash
curl -X POST localhost:8082/inventory/sku-widget/seed?quantity=10

curl -X POST localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cust-1","items":[{"productId":"sku-widget","quantity":2,"unitPrice":19.99}]}'
# → 202 Accepted, status: PENDING, save the returned orderId

curl localhost:8081/orders/<orderId>
# → poll this; within a second or two it should progress
#   PENDING -> INVENTORY_RESERVED -> PAYMENT_AUTHORIZED -> COMPLETED
```

**Demonstrate oversell prevention:** seed a product with quantity 1, then fire two orders for it back to back — one will complete, the other will land as `CANCELLED` with an inventory-related reason, and `GET /inventory/<productId>` will show zero sellable stock, never negative.

**Demonstrate the failure/compensation path:** place an order with a total over 1000 (the mock decline threshold) — it will reach `INVENTORY_RESERVED`, then `PAYMENT_AUTHORIZED` will never happen; instead the order lands as `CANCELLED` with a payment-related reason, and the inventory reservation is released (check `GET /inventory/<productId>` before and after to see the sellable quantity return).

## Running the tests

```bash
mvn test
```

The integration tests use Testcontainers, which needs Docker — they spin up real Postgres and Kafka containers rather than mocking them. The one worth reading first is `inventory-service`'s `OversellPreventionTest.concurrentReservations_forTheLastUnit_exactlyOneSucceeds` — it seeds exactly one unit of stock, fires ten concurrent reservation attempts at it, and asserts exactly one succeeds. That test is the actual proof behind the "prevents overselling under concurrent load" claim, not just a design intention.

## Running it on Kubernetes

There's a Helm chart at `deploy/helm/orderflow` that deploys the same four services plus Kafka and Postgres — StatefulSet for Postgres with a PVC, Deployments for everything else, readiness/liveness probes wired to each service's actuator health groups, resource requests/limits, the whole thing installable on a local `kind` cluster in a couple of commands. See `K8S.md` for the full walkthrough; it's the same three demo scenarios as above, just running as pods instead of compose containers. The `k8s-smoke-test` job in CI actually stands up a kind cluster and drives the happy-path demo through it on every push — not a `helm lint`/dry-run, a real cluster proving the chart genuinely works end to end.

## Current status / roadmap

**Built:** the four services, the full choreographed Saga (happy path + both failure/compensation branches), oversell-safe reservation, Docker Compose for local dev, per-service Dockerfiles, a Helm chart for Kubernetes (Postgres StatefulSet + PVC, Kafka, and the four services with health probes and resource limits), a GitHub Actions CI pipeline (build + test + image build + an end-to-end kind cluster smoke test), and a Kafka consumer retry/backoff policy in Payment Service.

**Not yet built** — in priority order:
- **Terraform (AWS).** VPC, EKS, RDS (one instance per service, replacing the single shared Postgres container), MSK or self-hosted Kafka on EKS, ECR — the natural next step now that the Helm chart exists to deploy with.
- **Observability.** Prometheus/Grafana dashboards (Actuator's `/actuator/prometheus` is already exposed on every service, just not scraped yet), OpenTelemetry tracing through Jaeger so a single order can be followed across all four services.
- **Resilience4j circuit breakers/bulkheads** around inter-service-adjacent calls (Payment Service's Kafka retry/backoff is a first step in this direction, not the full picture).
- **API Gateway** (Spring Cloud Gateway) as a single entry point instead of hitting each service's port directly.
- **Saga orchestrator** as an alternative to the current choreography implementation, to demonstrate both patterns.

## Module layout

```
orderflow/
├── common-events/       # shared Kafka event contracts (Java records)
├── order-service/       # order lifecycle + Saga state machine
├── inventory-service/   # stock, oversell-safe reservation
├── payment-service/     # mock payment authorization
├── shipping-service/    # shipment creation
├── deploy/
│   ├── kind-cluster-config.yaml
│   └── helm/
│       ├── orderflow/          # the Helm chart
│       └── build-and-load-images.sh
├── docker-compose.yml
├── K8S.md
├── RUNNING_LOCALLY.md
└── .github/workflows/ci.yml
```
