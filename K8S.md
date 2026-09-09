# Running OrderFlow on Kubernetes (local kind cluster)

This is the Kubernetes counterpart to `RUNNING_LOCALLY.md`: same platform, same four services, same three demo scenarios — just running as Deployments/StatefulSets in a real (if local) cluster via a Helm chart, instead of as docker-compose containers. Read `RUNNING_LOCALLY.md` first if you haven't already; this assumes Docker Desktop is already installed and working.

## Why kind

[kind](https://kind.sigs.k8s.io/) ("Kubernetes IN Docker") runs a real, spec-compliant Kubernetes cluster as Docker containers on your own machine — no cloud account, no cost, and close enough to a managed cluster (EKS/GKE) that everything here — the Helm chart, the probes, the resource limits — carries over directly when this project's Terraform/EKS phase happens later. It's the standard way to develop and test Kubernetes manifests locally.

## 1. Install the extra tools

You already have Docker Desktop and `kubectl` may already have come with it (Docker Desktop optionally bundles a `kubectl` client). Install `kind` and `helm` on top:

```bash
brew install kind
brew install helm
brew install kubectl   # skip if `kubectl version --client` already works
```

Verify:

```bash
kind version
helm version
kubectl version --client
```

## 2. Create the cluster

From the repo root:

```bash
kind create cluster --name orderflow --config deploy/kind-cluster-config.yaml
```

`deploy/kind-cluster-config.yaml` maps the cluster's NodePorts straight through to `localhost:8081-8084` on your Mac — the same ports docker-compose used — so every `curl` command from `RUNNING_LOCALLY.md` works completely unchanged once things are up.

Point `kubectl` at it (kind does this automatically, but to confirm):

```bash
kubectl cluster-info --context kind-orderflow
kubectl get nodes
```

## 3. Build and load the images

Kind's cluster has its own internal container runtime — it can't see the images sitting in Docker Desktop's normal image cache, so they need to be explicitly loaded in:

```bash
chmod +x deploy/helm/build-and-load-images.sh   # first time only
./deploy/helm/build-and-load-images.sh orderflow
```

This builds all four images (`orderflow/order-service:local`, etc. — same Dockerfiles as docker-compose) and loads each one into the kind node. Takes a few minutes the first time; re-run it any time you change service code and want to redeploy.

## 4. Install the Helm chart

```bash
helm install orderflow deploy/helm/orderflow
```

Watch it come up:

```bash
kubectl get pods -w
```

Give it 1-2 minutes on first install — Postgres and Kafka need to pass their own readiness checks before the four services' readiness probes will pass (they're wired to wait, via Kubernetes' normal `initialDelaySeconds`/retry behavior on connection failures, not an explicit `depends_on` — Kubernetes doesn't have compose's `depends_on: condition: service_healthy`, so this is the actual K8s-native way to express the same startup ordering). You're ready when everything shows `1/1 Running`:

```bash
kubectl get pods
# NAME                                 READY   STATUS    RESTARTS
# kafka-xxxxxxxxxx-xxxxx              1/1     Running   0
# postgres-0                           1/1     Running   0
# order-service-xxxxxxxxxx-xxxxx      1/1     Running   0
# inventory-service-xxxxxxxxxx-xxxxx  1/1     Running   0
# payment-service-xxxxxxxxxx-xxxxx    1/1     Running   0
# shipping-service-xxxxxxxxxx-xxxxx   1/1     Running   0
```

## 5. Run the same three demos

Because of the port mapping from step 2, these are byte-for-byte the same commands as `RUNNING_LOCALLY.md` sections 4-7 — copy them straight over:

```bash
curl http://localhost:8081/actuator/health   # order-service
curl http://localhost:8082/actuator/health   # inventory-service
curl http://localhost:8083/actuator/health   # payment-service
curl http://localhost:8084/actuator/health   # shipping-service
```

Then the happy path:

```bash
curl -X POST "http://localhost:8082/inventory/sku-widget/seed?quantity=10"

curl -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cust-1","items":[{"productId":"sku-widget","quantity":2,"unitPrice":19.99}]}'

curl http://localhost:8081/orders/<orderId>   # poll until COMPLETED
```

Oversell prevention and payment-decline/compensation work exactly as described in `RUNNING_LOCALLY.md` sections 6-7 — same requests, same expected results, just served by pods instead of compose containers.

## 6. Look around the cluster (worth doing once)

A few commands that are good to have run at least once, since they're the kind of thing that comes up in interviews about this project:

```bash
kubectl get deployments,statefulsets,services
kubectl describe pod <order-service-pod-name>     # see the readiness/liveness probe config, resource requests/limits
kubectl logs -f deployment/order-service           # tail logs like docker compose logs -f
kubectl top pods                                   # if metrics-server is installed; not required for anything above
```

## 7. Tearing down

```bash
helm uninstall orderflow          # removes all the Deployments/Services/StatefulSet
kind delete cluster --name orderflow   # deletes the whole cluster, including the Postgres PVC data
```

## Troubleshooting

**Pods stuck in `ImagePullBackOff`** — the image wasn't loaded into the kind node, or `image.tag`/`image.repository` in `values.yaml` doesn't match what was built. Re-run `deploy/helm/build-and-load-images.sh orderflow` and confirm with `docker exec -it orderflow-control-plane crictl images | grep orderflow`.

**A service's pod is `Running` but never `Ready`** — check `kubectl describe pod <name>` for probe failure events, then `kubectl logs <name>`. Almost always it's still retrying its Kafka or Postgres connection while those start up; the probes are configured with enough `initialDelaySeconds`/`failureThreshold` to ride this out, but a first-ever install on a slow machine can still need an extra minute.

**`localhost:8081` connection refused even though pods are Ready** — confirm the cluster was actually created with `--config deploy/kind-cluster-config.yaml` (the port mapping is set at cluster-creation time and can't be added after the fact — delete and recreate the cluster if you created it without the config file).

**Postgres pod recreated and lost data** — expected if you ran `kind delete cluster`; the PVC lives inside the kind node's storage, not on your Mac. This mirrors `docker compose down -v` for compose.
