#!/usr/bin/env bash
# Builds all four service images from the repo root and loads them straight
# into the local kind cluster's node — no registry involved. Run this after
# `kind create cluster` and before `helm install`, and again any time you
# change service code.
set -euo pipefail

CLUSTER_NAME="${1:-orderflow}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SERVICES=(order-service inventory-service payment-service shipping-service)

echo "Building images from ${REPO_ROOT} ..."
for svc in "${SERVICES[@]}"; do
  echo "--- building orderflow/${svc}:local ---"
  docker build -t "orderflow/${svc}:local" -f "${REPO_ROOT}/${svc}/Dockerfile" "${REPO_ROOT}"
done

echo "Loading images into kind cluster '${CLUSTER_NAME}' ..."
for svc in "${SERVICES[@]}"; do
  kind load docker-image "orderflow/${svc}:local" --name "${CLUSTER_NAME}"
done

echo "Done. Images available inside the kind cluster:"
for svc in "${SERVICES[@]}"; do
  echo "  orderflow/${svc}:local"
done
