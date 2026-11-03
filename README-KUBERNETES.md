# Spring Boot Observability --- Kubernetes README

This document covers **only the Kubernetes deployment** of the Spring
Boot Observability project on Windows using Docker Desktop Kubernetes.
Keep this file separate from the Docker Compose README to avoid mixing
the two environments.

## 1. Overview

The Kubernetes deployment runs these components in the `observability`
namespace:

  ---------------------------------------------------------------------------
  Component               Purpose                 Kubernetes service
  ----------------------- ----------------------- ---------------------------
  Spring Boot             Application APIs and    `springboot-service:8080`
                          Actuator metrics        

  Prometheus              Scrapes and stores      `prometheus-service:9090`
                          metrics                 

  Grafana                 Dashboards for metrics  `grafana-service:3000`
                          and logs                

  Loki                    Log storage and         `loki-service:3100`
                          querying                

  Grafana Alloy           Discovers Kubernetes    Deployment `alloy`
                          pods and forwards their 
                          logs to Loki            
  ---------------------------------------------------------------------------

### Architecture

``` text
Spring Boot application
   | /actuator/prometheus
   v
Prometheus  <--------- Grafana (metrics dashboards)
                         ^
                         |
Kubernetes pod logs       | log queries
   |                     |
   v                     |
Grafana Alloy ---> Loki --+
```

Grafana uses Kubernetes service DNS names to reach Prometheus and Loki.
A local `kubectl port-forward` makes the Grafana UI available in the
browser.

## 2. Prerequisites

-   Docker Desktop with Kubernetes enabled
-   `kubectl`
-   The project source code
-   Java and Maven if rebuilding the Spring Boot image

Check the Kubernetes context and node:

``` powershell
kubectl config current-context
kubectl get nodes
```

The context used in this setup is `docker-desktop`. The node should be
`Ready`.

This guide uses Docker Desktop Kubernetes, not Kind or Minikube.

## 3. Kubernetes files

The project manifests and configuration files are stored in `k8s`:

``` text
k8s/
├── namespace.yaml
├── configmap.yaml
├── deployment.yaml
├── service.yaml
├── prometheus-k8s.yaml
├── prometheus-pvc.yaml
├── prometheus-deployment.yaml
├── prometheus-service.yaml
├── grafana-pvc.yaml
├── grafana-deployment.yaml
├── grafana-service.yaml
├── loki-config.yaml
├── loki-pvc.yaml
├── loki-deployment.yaml
├── loki-service.yaml
├── alloy-config.yaml
├── alloy-rbac.yaml
└── alloy-deployment.yaml
```

`prometheus-k8s.yaml` is the Prometheus scrape configuration, and
`alloy-config.yaml` is Alloy's River configuration. `loki-config.yaml`
is a Kubernetes ConfigMap manifest containing Loki's configuration.

## 4. Build the Spring Boot image

The Deployment uses the local image `springboot-observability:1.0`.

From the project root:

``` powershell
docker build -t springboot-observability:1.0 .
docker images springboot-observability
```

The Deployment is configured with `imagePullPolicy: Never`, so the image
must be available to Docker Desktop Kubernetes locally. If the cluster
cannot find it, load the image into the Docker Desktop Kubernetes
container runtime before deploying.

## 5. Create the namespace

``` powershell
kubectl apply -f .\k8s\namespace.yaml
kubectl get namespaces
```

All resources in this guide are created in the `observability`
namespace.

## 6. Deploy Spring Boot

Apply the ConfigMap, Deployment, and Service:

``` powershell
kubectl apply -f .\k8s\configmap.yaml
kubectl apply -f .\k8s\deployment.yaml
kubectl apply -f .\k8s\service.yaml
```

Check the resources:

``` powershell
kubectl get deployments -n observability
kubectl get pods -n observability
kubectl get services -n observability
```

Application resources:

-   Deployment: `springboot-app`
-   Service: `springboot-service`
-   Service port: `8080`
-   Health probe path: `/actuator/health`
-   ConfigMap: `springboot-config`

Wait for the Spring Boot pod to show `Running` and `1/1` ready.

View application logs:

``` powershell
kubectl logs deployment/springboot-app -n observability --tail=100
```

## 7. Deploy Prometheus

Prometheus scrapes `/actuator/prometheus` from the Spring Boot service.
The configured target is:

``` text
springboot-service.observability.svc.cluster.local:8080
```

The scrape interval is configured as 5 seconds in `prometheus-k8s.yaml`.

Create the Prometheus ConfigMap:

``` powershell
kubectl create configmap prometheus-config `
  --from-file=prometheus.yml=.\k8s\prometheus-k8s.yaml `
  -n observability
```

Apply Prometheus storage, Deployment, and Service:

``` powershell
kubectl apply -f .\k8s\prometheus-pvc.yaml
kubectl apply -f .\k8s\prometheus-deployment.yaml
kubectl apply -f .\k8s\prometheus-service.yaml
```

Resources:

-   Deployment: `prometheus`
-   Service: `prometheus-service`, port `9090`
-   PVC: `prometheus-pvc`

The PVC should become `Bound`.

If `prometheus-config` already exists, do not run the create command
unchanged. To recreate it:

``` powershell
kubectl delete configmap prometheus-config -n observability
kubectl create configmap prometheus-config `
  --from-file=prometheus.yml=.\k8s\prometheus-k8s.yaml `
  -n observability
```

## 8. Deploy Loki

Apply Loki's ConfigMap, storage, Deployment, and Service:

``` powershell
kubectl apply -f .\k8s\loki-config.yaml
kubectl apply -f .\k8s\loki-pvc.yaml
kubectl apply -f .\k8s\loki-deployment.yaml
kubectl apply -f .\k8s\loki-service.yaml
```

Resources:

-   Deployment: `loki`
-   Service: `loki-service`, port `3100`
-   PVC: `loki-pvc`

Check that the pod is ready and the PVC is `Bound`:

``` powershell
kubectl get pods -n observability
kubectl get pvc -n observability
```

## 9. Deploy Grafana Alloy

Alloy discovers Kubernetes pods, collects their container logs, adds
Kubernetes labels, and forwards logs to Loki.

Create the Alloy ConfigMap:

``` powershell
kubectl create configmap alloy-config `
  --from-file=config.alloy=.\k8s\alloy-config.yaml `
  -n observability
```

Apply permissions and the Deployment:

``` powershell
kubectl apply -f .\k8s\alloy-rbac.yaml
kubectl apply -f .\k8s\alloy-deployment.yaml
```

Check the pod and its logs:

``` powershell
kubectl get pods -n observability
kubectl logs deployment/alloy -n observability --tail=50
```

Alloy forwards logs to:

``` text
http://loki-service.observability.svc.cluster.local:3100/loki/api/v1/push
```

If `alloy-config` already exists, recreate it using the same
delete-and-create approach shown for the Prometheus ConfigMap.

## 10. Deploy Grafana

Apply Grafana storage, Deployment, and Service:

``` powershell
kubectl apply -f .\k8s\grafana-pvc.yaml
kubectl apply -f .\k8s\grafana-deployment.yaml
kubectl apply -f .\k8s\grafana-service.yaml
```

Resources:

-   Deployment: `grafana`
-   Service: `grafana-service`, port `3000`
-   PVC: `grafana-pvc`

The PVC is mounted at `/var/lib/grafana`.

## 11. Verify Kubernetes resources

``` powershell
kubectl get all -n observability
kubectl get pvc -n observability
```

Expected Deployments include `springboot-app`, `prometheus`, `grafana`,
`loki`, and `alloy`. Pods should be `Running` and ready; PVCs should be
`Bound`.

Useful diagnostics:

``` powershell
kubectl describe pod <pod-name> -n observability
kubectl logs <pod-name> -n observability --tail=100
```

## 12. Open Kubernetes Grafana

Run this in PowerShell and leave the terminal open:

``` powershell
kubectl port-forward svc/grafana-service 3002:3000 -n observability
```

Open:

``` text
http://localhost:3002
```

Use the Grafana credentials configured for this deployment. Do not
commit passwords or tokens to Git.

### Keep Docker Compose separate

  Environment      Grafana URL
  ---------------- -------------------------
  Docker Compose   `http://localhost:3001`
  Kubernetes       `http://localhost:3002`

Kubernetes Grafana uses host port `3002`; Docker Compose Grafana remains
on `3001`. The two environments are separate.

## 13. Configure Grafana data sources

In Kubernetes Grafana, add and Save & test these data sources:

**Prometheus**

``` text
http://prometheus-service:9090
```

**Loki**

``` text
http://loki-service:3100
```

These are in-cluster Service addresses. Use these URLs in Grafana rather
than `localhost`.

## 14. Import the dashboard

The migrated dashboard is named `test dashboard 01`.

1.  Open `http://localhost:3002`.
2.  Go to **Dashboards → New → Import**.
3.  Upload the Kubernetes-updated dashboard JSON.
4.  Complete the import and inspect the panels.

Grafana data source UIDs belong to a particular Grafana instance. If the
JSON is imported into a different instance, select or update the
corresponding data sources.

A panel showing `No data` does not necessarily mean the import failed.
First run the metric query without label filters, inspect the labels,
and then use the matching filter.

### Date Generator: scheduled and on-demand metrics

The Date Generator scheduler records source `SCHEDULED`. The API
endpoint below creates a record with source `ON_DEMAND`:

``` text
GET /api/date-generator
```

Example PromQL queries:

``` promql
date_generator_records_generated_total{source="scheduled"}
```

``` promql
date_generator_records_generated_total{source="on_demand"}
```

The on-demand query only returns a series after an on-demand record has
been generated.

## 15. Test the Spring Boot API in Kubernetes

Open a separate PowerShell terminal and run:

``` powershell
kubectl port-forward svc/springboot-service 8080:8080 -n observability
```

Keep it open. In another PowerShell terminal, trigger an on-demand
record:

``` powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/date-generator"
```

A successful response includes `source: ON_DEMAND` and
`status: SUCCESS`.

Check the generation counts:

``` powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/date-generator/metrics"
```

Check application health:

``` powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/actuator/health"
```

Stop a port-forward with `Ctrl + C` in its terminal. This stops only
local forwarding; it does not stop the Kubernetes Deployment.

## 16. Explore logs in Grafana

In Grafana, open **Explore**, select Loki, and run:

All logs in the namespace:

``` logql
{namespace="observability"}
```

Spring Boot pod logs:

``` logql
{namespace="observability", pod=~"springboot-app-.*"}
```

Weather logs:

``` logql
{namespace="observability", pod=~"springboot-app-.*"} |= "Weather API"
```

Exchange logs:

``` logql
{namespace="observability", pod=~"springboot-app-.*"} |= "Exchange API"
```

## 17. Useful commands

  ----------------------------------------------------------------------------------------------------------
  Task                                PowerShell command
  ----------------------------------- ----------------------------------------------------------------------
  Current context                     `kubectl config current-context`

  Nodes                               `kubectl get nodes`

  All namespace resources             `kubectl get all -n observability`

  Pods                                `kubectl get pods -n observability`

  Services                            `kubectl get services -n observability`

  ConfigMaps                          `kubectl get configmaps -n observability`

  PVCs                                `kubectl get pvc -n observability`

  Watch pods                          `kubectl get pods -n observability -w`

  Spring Boot logs                    `kubectl logs deployment/springboot-app -n observability --tail=100`

  Prometheus logs                     `kubectl logs deployment/prometheus -n observability --tail=100`

  Grafana logs                        `kubectl logs deployment/grafana -n observability --tail=100`

  Loki logs                           `kubectl logs deployment/loki -n observability --tail=100`

  Alloy logs                          `kubectl logs deployment/alloy -n observability --tail=100`
  ----------------------------------------------------------------------------------------------------------

## 18. Troubleshooting

### A pod is not starting

``` powershell
kubectl get pods -n observability
kubectl describe pod <pod-name> -n observability
kubectl logs <pod-name> -n observability --tail=100
```

Inspect the Events section from `describe` and the container logs before
changing the manifests.

### A ConfigMap is missing

``` powershell
kubectl get configmaps -n observability
```

Prometheus and Alloy ConfigMaps are created from their separate
configuration files using the `kubectl create configmap` commands above.
Loki's ConfigMap is applied from `loki-config.yaml`.

### A PVC is Pending

``` powershell
kubectl get pvc -n observability
kubectl describe pvc <pvc-name> -n observability
```

On Docker Desktop Kubernetes, storage provisioning may wait until a pod
uses the claim. Inspect PVC events and pod status before changing
storage settings.

### Grafana cannot connect to a data source

``` powershell
kubectl get services -n observability
```

Confirm the services exist, then verify the in-cluster URLs in the
Grafana data source settings:

``` text
http://prometheus-service:9090
http://loki-service:3100
```

### A dashboard panel shows No data

Run the query without filters in Grafana Explore. If the metric exists,
inspect its labels and use the correct label values. For example, an
`on_demand` metric series requires an on-demand API request to have
occurred.

### A port-forward fails

The local port may already be in use. Stop the old port-forward with
`Ctrl + C`, or select a different unused local port while retaining the
Kubernetes service port.

## 19. Stop and restart workloads

To stop local browser access, press `Ctrl + C` in each port-forward
terminal. The Deployments continue running.

To scale all Deployments in the namespace down to zero replicas:

``` powershell
kubectl scale deployment --all --replicas=0 -n observability
```

To scale them back to one replica:

``` powershell
kubectl scale deployment --all --replicas=1 -n observability
```

Check:

``` powershell
kubectl get pods -n observability
```

### Delete the Kubernetes namespace --- destructive

**Warning:** Deleting the namespace removes its Deployments, Services,
ConfigMaps, and PVC objects. Depending on the storage provisioner and
reclaim policy, persistent data may also be deleted. Do not use this as
a normal stop command.

``` powershell
kubectl delete namespace observability
```

To recreate the environment, recreate the namespace and reapply the
manifests from this guide. Recreate the Prometheus and Alloy ConfigMaps
using their commands above.

## 20. Quick deployment sequence

Run from the project root after Docker Desktop Kubernetes is ready and
the Spring Boot image is available locally.

``` powershell
kubectl apply -f .\k8s\namespace.yaml

kubectl apply -f .\k8s\configmap.yaml
kubectl apply -f .\k8s\deployment.yaml
kubectl apply -f .\k8s\service.yaml

kubectl create configmap prometheus-config `
  --from-file=prometheus.yml=.\k8s\prometheus-k8s.yaml `
  -n observability
kubectl apply -f .\k8s\prometheus-pvc.yaml
kubectl apply -f .\k8s\prometheus-deployment.yaml
kubectl apply -f .\k8s\prometheus-service.yaml

kubectl apply -f .\k8s\loki-config.yaml
kubectl apply -f .\k8s\loki-pvc.yaml
kubectl apply -f .\k8s\loki-deployment.yaml
kubectl apply -f .\k8s\loki-service.yaml

kubectl create configmap alloy-config `
  --from-file=config.alloy=.\k8s\alloy-config.yaml `
  -n observability
kubectl apply -f .\k8s\alloy-rbac.yaml
kubectl apply -f .\k8s\alloy-deployment.yaml

kubectl apply -f .\k8s\grafana-pvc.yaml
kubectl apply -f .\k8s\grafana-deployment.yaml
kubectl apply -f .\k8s\grafana-service.yaml

kubectl get all -n observability
kubectl get pvc -n observability
```

The two `kubectl create configmap` commands are first-time creation
commands. If those ConfigMaps already exist, follow the recreate
procedure in their respective sections instead of running the commands
unchanged.

Open Grafana:

``` powershell
kubectl port-forward svc/grafana-service 3002:3000 -n observability
```

Then browse to `http://localhost:3002`.

------------------------------------------------------------------------

**Scope:** This is a local development setup for Docker Desktop
Kubernetes. It is not a production cluster deployment. The Docker
Compose environment is documented separately and is not managed by this
README.
