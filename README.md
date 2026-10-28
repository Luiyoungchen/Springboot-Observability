# Spring Boot Observability Stack

A Docker-based application observability project using **Java 21, Spring Boot, Spring Boot Actuator, Micrometer, Prometheus, Grafana, Loki, and Grafana Alloy**.

The goal is to run the application and monitoring services together with Docker Compose, collect application metrics, collect container logs, and view both metrics and logs in Grafana.

## Project status

- [x] Created the Spring Boot application and Actuator configuration.
- [x] Added the home endpoint and a simulated slow endpoint.
- [x] Built the Spring Boot Docker image successfully.
- [x] Created Prometheus, Loki, Alloy, and Grafana configuration files.
- [x] Validated the Compose configuration using `docker compose config`.
- [x] Confirmed the Spring Boot application runs on port `8080`.
- [x] Confirmed the Loki query returns application logs in Grafana Explore.
- [x] Confirmed the Grafana HTTP request-rate panel displays data.
- [x] Added the response-latency and JVM heap panels.
- [x] Confirmed the Grafana application-logs panel displays log entries.
- [ ] Recheck all services and dashboard panels after a fresh restart.

---

### Components

| Component | Responsibility | Local address |
|---|---|---|
| Spring Boot | Runs the Java application and exposes endpoints | http://localhost:8080 |
| Spring Boot Actuator | Exposes health and monitoring endpoints | `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus` |
| Prometheus | Scrapes and stores metrics | http://localhost:9090 |
| Grafana Alloy | Collects selected Docker container logs and forwards them to Loki | http://localhost:12345 |
| Loki | Stores and queries logs | http://localhost:3100 |
| Grafana | Dashboards and queries for metrics and logs | http://localhost:3001 |

Grafana uses host port `3001` because a separate Grafana installation on the Windows host was already using port `3000`.

## 2. Technology stack

- Java 21
- Spring Boot
- Maven / Maven Wrapper
- Spring Boot Actuator
- Micrometer Prometheus registry
- Docker and Docker Compose
- Prometheus
- Grafana
- Loki
- Grafana Alloy

The Dockerfile uses a multi-stage build: Maven with Eclipse Temurin 21 builds the JAR, and a smaller Java 21 runtime image runs it.

## 3. Project structure

```text
Springboot-observability/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/angamba/observability/
│       │       ├── ObservabilityApplication.java
│       │       ├── ObservabilityController.java
│       │       └── Controller/
│       │           ├── WeatherController.java
│       │           └── ExchangeController.java
│       └── resources/
│           └── application.yml
├── prometheus/
│   └── prometheus.yml
├── grafana/
│   └── provisioning/
│       └── datasources/
│           └── datasources.yml
├── loki/
│   └── loki-config.yml
├── alloy/
│   └── config.alloy
├── Dockerfile
├── docker-compose.yml
├── .dockerignore
├── .gitignore
├── pom.xml
└── README.md
```

Your exact source filenames may differ slightly. Keep the paths referenced by `docker-compose.yml` consistent with the files on disk.

## 4. Application endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/` | Returns application information |
| GET | `/slow` | Simulates a slow response for latency monitoring |
| GET | `/api/weather?city=Guwahati` | Retrieves current weather information |
| GET | `/api/exchange?base=USD&target=INR` | Retrieves a currency exchange rate |
| GET | `/actuator/health` | Reports application health |
| GET | `/actuator/metrics` | Lists available application metrics |
| GET | `/actuator/prometheus` | Exposes Prometheus metrics |

Try these URLs after starting the application:

- [Application home](http://localhost:8080/)
- [Slow endpoint](http://localhost:8080/slow)
- [Health endpoint](http://localhost:8080/actuator/health)
- [Metrics endpoint](http://localhost:8080/actuator/metrics)
- [Prometheus metrics](http://localhost:8080/actuator/prometheus)
- [Weather Api](http://localhost:8080/api/weather?city=Guwahati)
- [Exchange Api](http://localhost:8080/api/exchange?base=USD&target=INR)

The `application.yml` configuration used in this project is:

```yaml
server:
  port: 8080

spring:
  application:
    name: springboot-observability

management:
  endpoints:
    web:
      exposure:
        include: health, prometheus, metrics
  endpoint:
    health:
      show-details: never
  prometheus:
    metrics:
      export:
        enabled: true
```

Only expose the Actuator endpoints needed for your environment. Avoid exposing sensitive operational details publicly.

## 5. Prometheus configuration

File: `prometheus/prometheus.yml`

Prometheus scrapes the application every five seconds. Inside Docker Compose, the target uses the Compose service name rather than `localhost`.

```yaml
global:
  scrape_interval: 5s
  evaluation_interval: 5s

scrape_configs:
  - job_name: "springboot-app"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets: ["springboot-app:8080"]
```

**Why `springboot-app:8080`?** Containers in the same Compose network can reach one another by service name. Within the Prometheus container, `localhost:8080` would refer to Prometheus itself, not Spring Boot.

## 6. Loki and Grafana Alloy logging

The log pipeline is:

```text
Spring Boot container stdout/stderr
        ↓
Grafana Alloy discovers and collects Docker logs
        ↓
Loki stores log streams
        ↓
Grafana Explore and dashboard panels query Loki
```

The current working LogQL query confirmed in Grafana Explore is:

```logql
{service_name="springboot-observability"}
```

To generate fresh log entries, call the home and slow endpoints, then run the query again.

If the query returns no results, inspect the labels available in Grafana Explore. Depending on the actual Alloy configuration, other possible selectors might be:

```logql
{job="springboot"}
```

```logql
{container="springboot-app"}
```

These alternatives work only if those labels exist in the ingested streams.

The Loki configuration uses a single-node setup with filesystem storage and a TSDB index. This is intended for local development and learning, not a hardened multi-node production deployment.

Alloy discovers Docker containers, filters for the container named `/springboot-app`, adds labels, and sends collected logs to Loki at:

```text
http://loki:3100/loki/api/v1/push
```

The Compose configuration mounts the Docker socket into Alloy so it can discover and read container logs. Docker socket access is highly privileged; use this approach only in a trusted local development environment.

## 7. Grafana data sources

File: `grafana/provisioning/datasources/datasources.yml`

Grafana provisioning is intended to add these data sources automatically:

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true

  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
    editable: true
```

The provisioning directory should be mounted into `/etc/grafana/provisioning` inside the Grafana container. The URLs use Compose service names because Grafana communicates with the other services over the Docker network.

## 8. Dockerfile

The Dockerfile builds the JAR in a Maven/Java 21 build stage, then copies the generated JAR into a Java 21 runtime stage.

```dockerfile
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

The build currently skips tests using `-DskipTests`. Run tests separately when they are ready to be included in the build workflow.

The `.dockerignore` excludes generated files and local-only folders such as `target/`, `.git/`, `.vscode/`, `.idea/`, `node_modules/`, and `.env`.

## 9. Start the project with Docker Compose

Open PowerShell in the project root:

```powershell
cd C:\Users\hp\Desktop\Springboot-observability
```

### Validate the Compose file

```powershell
docker compose config
```

This validates and renders the Compose configuration without starting services.

### Build and start the stack

```powershell
docker compose up -d --build
```

This builds the Spring Boot image and starts the five services in the background.

### Check service status

```powershell
docker compose ps
```

All five services should be listed. Check logs if any service exits or fails to start:

```powershell
docker compose logs --tail=100 springboot-app
docker compose logs --tail=100 prometheus
docker compose logs --tail=100 loki
docker compose logs --tail=100 alloy
docker compose logs --tail=100 grafana
```

Follow one service's logs live:

```powershell
docker compose logs -f springboot-app
```

Press `Ctrl+C` to stop following the log output; this does not stop the container.

### If a container name is already in use

Older standalone containers may already use names such as `loki`, `prometheus`, or `alloy`. Inspect containers first:

```powershell
docker ps -a
```

If an old container has a conflicting name and you want to preserve it, rename and stop it. For example:

```powershell
docker rename loki loki-old
docker stop loki-old
```

Repeat only for the specific container that still has the conflicting original name. Do not run rename commands blindly. Renaming alone does not free a host port while the container is running.

## 10. Verify the observability stack

### A. Check Spring Boot

Open [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health). The response should indicate that the application is up.

### B. Check Prometheus

Open [Prometheus Targets](http://localhost:9090/targets). Find the `springboot-app` target and confirm its state is **UP**.

You can also run this query in Prometheus:

```promql
up
```

The Spring Boot target should have a value of `1` when it is being scraped successfully.

### C. Check Loki

Open [http://localhost:3100/ready](http://localhost:3100/ready). A ready response indicates that Loki is ready.

### D. Check Grafana

Open [http://localhost:3001](http://localhost:3001).

Sign in with the credentials configured for your Grafana instance. A fresh Grafana setup commonly starts with `admin` / `admin` and then asks you to change the password. If the persistent volume already has an account, use that existing account.

Open **Connections → Data sources** and verify that Prometheus and Loki are present and their connection tests succeed.

### E. Generate and query logs

Call the home and slow endpoints several times:

- [Home endpoint](http://localhost:8080/)
- [Slow endpoint](http://localhost:8080/slow)

Then open Grafana → **Explore**, choose Loki, and run:

```logql
{service_name="springboot-observability"}
```

If no logs appear, inspect Alloy and Loki logs:

```powershell
docker compose logs --tail=100 alloy
docker compose logs --tail=100 loki
```

Also confirm that the Alloy filter matches the actual Compose container name and that Alloy can access the Docker socket.

## 11. Grafana dashboard panels

The dashboard has panels for request rate, response latency, JVM heap memory, and application logs.

Choose a time range such as **Last 15 minutes** while testing. Generate requests to `/` and `/slow` to create activity.

### Panel 1 — HTTP Request Rate

**Data source:** Prometheus

```promql
sum(rate(http_server_requests_seconds_count[5m]))
```

This estimates HTTP requests per second over a five-minute rate window.

### Panel 2 — Average HTTP Response Latency

**Data source:** Prometheus

```promql
1000 *
sum(rate(http_server_requests_seconds_sum[5m]))
/
sum(rate(http_server_requests_seconds_count[5m]))
```

Set the panel unit to **milliseconds (ms)**. This calculates a request-count-weighted average across matching Spring HTTP server request metrics. It may show no data until requests have occurred and enough samples are available.

### Panel 3 — JVM Heap Memory Used

**Data source:** Prometheus

```promql
sum(jvm_memory_used_bytes{area="heap"})
```

Set the panel unit to **bytes (IEC)**. This displays used heap memory across matching JVM memory-pool series.

### Panel 4 — Spring Boot Application Logs

**Data source:** Loki

```logql
{service_name="springboot-observability"}
```

Display the returned log entries in a logs panel. If the panel is empty, test the same query in Explore first and inspect the labels on the returned streams.

### Dashboard troubleshooting

- Save the dashboard after adding or changing panels.
- If a metric query is empty, check exact metric names at `/actuator/prometheus`.
- Confirm the Prometheus target is **UP**.
- If logs are missing, confirm Alloy is running, Loki is ready, and Explore returns results for the same LogQL selector.
- If the dashboard has no recent data, widen the time range and generate fresh requests.

## 12. Stop and restart

Stop the stack while retaining its containers and volumes:

```powershell
docker compose stop
```

Start the existing containers again:

```powershell
docker compose start
```

Stop and remove Compose-managed containers and the network while retaining named volumes:

```powershell
docker compose down
```

Remove containers, network, and named volumes:

```powershell
docker compose down -v
```

**Warning:** `docker compose down -v` deletes persisted Prometheus, Loki, Grafana, and Alloy data stored in Compose named volumes. Use it only when you intentionally want to erase that data.

Rebuild after changing the application or Docker build inputs:

```powershell
docker compose up -d --build
```
## 14. Official documentation

- [Spring Boot documentation](https://docs.spring.io/spring-boot/index.html)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/index.html)
- [Micrometer Prometheus registry](https://docs.micrometer.io/micrometer/reference/implementations/prometheus.html)
- [Docker Compose documentation](https://docs.docker.com/compose/)
- [Dockerfile reference](https://docs.docker.com/reference/dockerfile/)
- [Prometheus documentation](https://prometheus.io/docs/introduction/overview/)
- [Grafana documentation](https://grafana.com/docs/grafana/latest/)
- [Grafana data source provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/)
- [Grafana Loki documentation](https://grafana.com/docs/loki/latest/)
- [Grafana Alloy documentation](https://grafana.com/docs/alloy/latest/)
- [Grafana Alloy Docker logging](https://grafana.com/docs/alloy/latest/collect/logs-infrastructure/docker/)

---
### Now We will try to add two new services by making apis
## (a) weather Api
  1. Create a new Java file
Inside src/main/java/com/angamba/observability/, create a package named controller, then create:

WeatherController.java

CHECKING:
To run the spring: .\mvnw.cmd spring-boot:run

Go the new tab, and type localhost:8080/api/weather?city=Guwahati, localhost:8080/api/weather?city=Mumbai

To check in promatheus: Stop the spring and start the docker; docker compose up -d --build
Then go the new tab, and type localhost:9090/

Open the Query:
http_server_requests_seconds_count{uri="/api/weather"} //number of requests made to the Weather API.

1000 *
sum(rate(http_server_requests_seconds_sum{uri="/api/weather"}[5m]))   
/
sum(rate(http_server_requests_seconds_count{uri="/api/weather"}[5m]))
//returns average response time in milliseconds over the last five minutes.

Now to Display in Grafana:
Open Grafana, then edit your existing dashboard and add a panel.

Use these settings:
Setting	Value
Data source	Prometheus
Panel title	Weather API — Request Rate
Query	sum(rate(http_server_requests_seconds_count{uri="/api/weather"}[5m]))
Visualization	Time series

Add a second panel titled Weather API — Average Response Time using the latency query above. Set the unit to milliseconds (ms).

You can also view Weather API logs in Grafana Explore with this LogQL query:
{service_name="springboot-observability"} |= "Weather API"

## (b)Currency Exchange
  1. Create a new Java file
Inside src/main/java/com/angamba/observability/, create a package named controller, then create:

ExchangeController.java
CHECKING:
To run the spring: .\mvnw.cmd spring-boot:run

Go the new tab, and type localhost:8080/api/exchange?base=EUR&target=INR, localhost:8080/api/exchange?base=GBP&target=INR

To check in promatheus: Stop the spring and start the docker; docker compose up -d --build
Then go the new tab, and type localhost:9090/

Open the Query:
sum(rate(http_server_requests_seconds_count{uri="/api/exchange"}[5m])) //Request Rate.

1000 *
sum(rate(http_server_requests_seconds_sum{uri="/api/exchange"}[5m]))
/
sum(rate(http_server_requests_seconds_count{uri="/api/exchange"}[5m]))
//Average Latency

{service_name="springboot-observability"} |= "Exchange API" //log


## Add a combined request-rate panel
In Grafana at http://localhost:3001:
1. Open your existing dashboard.
2. Select Add → Visualization.
3. Choose the Prometheus data source.
4. Paste this query:
sum by (uri) (
  rate(http_server_requests_seconds_count{
    uri=~"/|/slow|/api/weather|/api/exchange"
  }[5m])
)


5. Set the visualization to Time series.
6. Set the title to API Request Rate by Endpoint.
7. Click Apply and save the dashboard.
