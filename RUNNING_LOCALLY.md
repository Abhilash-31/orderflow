# Running OrderFlow locally — full setup & execution guide

This walks through everything from "empty laptop" to "order flowing through all four services," including the two demo scenarios worth showing in an interview.

## 1. Tools to install

You need Docker either way. Java and Maven are only required if you want to run the test suite or open the project in an IDE outside of Docker — `docker compose up` alone doesn't need them, since Maven runs *inside* the build containers.

| Tool | Why | Required for |
|---|---|---|
| **Docker Desktop** (Mac/Windows) or Docker Engine + Compose plugin (Linux) | Runs Kafka, Postgres, and all 4 services | Everything |
| **Git** | Clone/push the repo | Everything |
| **JDK 21** (Temurin/Eclipse Adoptium build recommended) | Compile/run outside Docker, IDE support | Local `mvn` commands, IDE |
| **Maven 3.9+** | Build the multi-module project locally | Local `mvn test` / `mvn compile` |
| **curl** | Hit the REST APIs to drive the demo | Everything (ships with macOS/Linux; Windows 10+ has `curl.exe`) |
| **jq** (optional) | Pretty-print JSON responses | Nice to have, not required |
| **IntelliJ IDEA Community** (optional) | Editing/debugging the code | Only if you want an IDE |

### macOS (Homebrew)

```bash
# Homebrew itself, if you don't have it:
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

brew install --cask docker      # then open Docker.app once and wait for the whale icon to go steady
brew install openjdk@21
brew install maven
brew install jq                 # optional
brew install --cask intellij-idea-ce   # optional

# Make Java 21 the one on your PATH (Homebrew installs it "keg-only"):
echo 'export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

### Windows

- Install **Docker Desktop** from docker.com — during setup, choose the WSL2 backend (it will prompt you to install WSL2 if missing).
- Install Java and Maven via `winget` (PowerShell):
  ```powershell
  winget install EclipseAdoptium.Temurin.21.JDK
  winget install Apache.Maven
  ```
- Run everything below from either PowerShell or a WSL2 terminal — the `curl` commands work in both (Windows 10+ ships `curl.exe`).

### Linux (Ubuntu/Debian)

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-plugin openjdk-21-jdk maven jq
sudo usermod -aG docker $USER   # log out/in afterward so `docker` works without sudo
```

### Verify everything installed correctly

```bash
docker --version && docker compose version
java -version        # should say 21.x
mvn -version
git --version
```

## 2. Get the project

If you haven't already: unzip the `orderflow.zip` you were sent, or if you've since pushed it to your own GitHub repo, clone that instead.

```bash
cd orderflow
```

**Worth doing now if you haven't:** push it to your own GitHub so it's live as a portfolio piece immediately, even before you've built anything further.

```bash
git remote add origin https://github.com/<your-username>/orderflow.git
git branch -M main
git push -u origin main
```

## 3. Start everything

Make sure Docker Desktop is actually running first (the whale icon in your menu bar/system tray should be steady, not animating).

```bash
docker compose up --build
```

First run takes a few minutes — it's pulling the Postgres and Kafka images and, inside each service's build stage, downloading every Maven dependency from scratch. Leave this terminal running; it streams logs from all five containers (Kafka, Postgres, and the four services) interleaved. You're ready when you see all four services log something like `Started OrderServiceApplication in 4.2 seconds`.

Open a **second terminal** for the commands below — leave the first one running so you can watch the logs as events flow through.

## 4. Sanity-check everything is healthy

```bash
curl http://localhost:8081/actuator/health   # order-service
curl http://localhost:8082/actuator/health   # inventory-service
curl http://localhost:8083/actuator/health   # payment-service
curl http://localhost:8084/actuator/health   # shipping-service
```

Each should return `{"status":"UP"}`. If one doesn't, check the compose logs for that service in your first terminal — it's almost always still waiting on Kafka or Postgres to finish starting; give it another 10-20 seconds and retry.

## 5. Demo 1 — the happy path

Seed some stock, then place an order:

```bash
curl -X POST "http://localhost:8082/inventory/sku-widget/seed?quantity=10"

curl -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cust-1","items":[{"productId":"sku-widget","quantity":2,"unitPrice":19.99}]}'
```

That returns `202 Accepted` with a JSON body — copy the `orderId` out of it. Then poll:

```bash
curl http://localhost:8081/orders/<orderId>
```

Run that a few times a second or two apart — you should watch `status` move `PENDING` → `INVENTORY_RESERVED` → `PAYMENT_AUTHORIZED` → `COMPLETED`. Once it's `COMPLETED`, check the shipment that was created:

```bash
curl http://localhost:8084/shipments/by-order/<orderId>
```

## 6. Demo 2 — oversell prevention (the interesting one)

Seed a product with exactly one unit, then fire two orders for it back to back:

```bash
curl -X POST "http://localhost:8082/inventory/sku-limited/seed?quantity=1"

curl -X POST http://localhost:8081/orders -H "Content-Type: application/json" \
  -d '{"customerId":"cust-A","items":[{"productId":"sku-limited","quantity":1,"unitPrice":9.99}]}'

curl -X POST http://localhost:8081/orders -H "Content-Type: application/json" \
  -d '{"customerId":"cust-B","items":[{"productId":"sku-limited","quantity":1,"unitPrice":9.99}]}'
```

Poll both `orderId`s — one will reach `COMPLETED`, the other will land on `CANCELLED` with `cancellationReason` mentioning inventory. Then confirm stock never went negative:

```bash
curl http://localhost:8082/inventory/sku-limited
# availableQuantity: 1, reservedQuantity: 1 → sellable is 0, never -1
```

## 7. Demo 3 — payment decline & compensation

Place an order whose total exceeds the mock decline threshold (1000):

```bash
curl -X POST "http://localhost:8082/inventory/sku-expensive/seed?quantity=5"

curl -X POST http://localhost:8081/orders -H "Content-Type: application/json" \
  -d '{"customerId":"cust-C","items":[{"productId":"sku-expensive","quantity":1,"unitPrice":1500.00}]}'
```

Poll the order — it reaches `INVENTORY_RESERVED`, then never gets to `PAYMENT_AUTHORIZED`; instead it flips straight to `CANCELLED` with a payment-related reason. Check that the reservation was released:

```bash
curl http://localhost:8082/inventory/sku-expensive
# reservedQuantity should be back to 0
```

## 8. Running the automated tests

The integration tests use Testcontainers, so Docker needs to be running (they spin up real Postgres/Kafka containers, not mocks):

```bash
mvn test                        # everything
mvn -pl inventory-service test  # just the oversell-prevention test — the one worth reading first
```

## 9. Shutting down

```bash
docker compose down       # stops and removes the containers
docker compose down -v    # also wipes the Postgres volume, for a totally clean slate next run
```

## Troubleshooting

**"port is already allocated"** — something else on your machine is already using 8081-8084, 5432, or 9092. Either stop that process or change the left-hand side of the port mapping in `docker-compose.yml` (e.g. `"18081:8081"`).

**Build fails downloading dependencies** — if you're on a corporate/school network or VPN, it may block Maven Central; try a different network, or check whether your organization has an internal Maven mirror to point at instead. (This is exactly what happened when this project was scaffolded in a sandboxed build environment — it's a network policy issue, not a bug in the code.)

**A service keeps restarting / never goes healthy** — check its logs specifically: `docker compose logs -f order-service` (swap the service name). Almost always it's still waiting on Kafka or Postgres; `docker compose up` again after a minute usually resolves it on first-ever runs while the Kafka KRaft cluster metadata initializes.

**Apple Silicon (M1/M2/M3/M4) Macs** — every base image used here (Postgres, Eclipse Temurin, Maven, Bitnami Kafka) publishes native ARM64 builds, so this should run natively rather than under emulation. If it feels unusually slow, run `docker info` and check the architecture Docker Desktop is actually using.
