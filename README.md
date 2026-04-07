# modbus-proxy

A lightweight Modbus TCP proxy that serializes requests from multiple clients (e.g. Home Assistant, evcc) onto a single upstream connection. Designed for SolarEdge inverters, which reject concurrent Modbus connections.

## How it works

Clients connect to the proxy just like they would connect to the inverter directly. The proxy accepts all connections and places incoming requests in a bounded queue. A single processing thread forwards requests to the inverter one at a time and routes responses back to the originating client.

```
Home Assistant ──┐
                 ├──► modbus-proxy ──► SolarEdge inverter
evcc ────────────┘
```

## Requirements

- Java 25 (for local builds)
- Docker (for containerised deployment)

## Quick start with Docker Compose

```bash
# Set the inverter address and start
MODBUS_UPSTREAM_HOST=192.168.1.100 docker compose up -d
```

The proxy is now reachable on port `502` (Modbus TCP) and port `8080` (Spring Boot Actuator).

## Building locally

```bash
mvn clean install          # Build and run all tests
mvn spring-boot:run        # Start locally
```

## Configuration

All parameters are set via `application.yml` or environment variables (Spring Boot property-to-env mapping: `.` → `_`, `-` → `_`, uppercased).

### Proxy (inbound listener)

| Property | Env variable | Default | Description |
|---|---|---|---|
| `modbus.proxy.port` | `MODBUS_PROXY_PORT` | `502` | Port the proxy listens on for client connections |
| `modbus.proxy.max-clients` | `MODBUS_PROXY_MAX_CLIENTS` | `10` | Maximum simultaneous client connections |

### Upstream (SolarEdge inverter)

| Property | Env variable | Default | Description |
|---|---|---|---|
| `modbus.upstream.host` | `MODBUS_UPSTREAM_HOST` | `192.168.1.100` | IP address or hostname of the inverter |
| `modbus.upstream.port` | `MODBUS_UPSTREAM_PORT` | `1502` | Modbus TCP port on the inverter (SolarEdge default: 1502) |
| `modbus.upstream.request-timeout-ms` | `MODBUS_UPSTREAM_REQUEST_TIMEOUT_MS` | `5000` | Timeout per request in milliseconds |
| `modbus.upstream.reconnect-initial-delay-ms` | `MODBUS_UPSTREAM_RECONNECT_INITIAL_DELAY_MS` | `1000` | Initial reconnect delay in milliseconds (doubles on each failure) |
| `modbus.upstream.reconnect-max-delay-ms` | `MODBUS_UPSTREAM_RECONNECT_MAX_DELAY_MS` | `30000` | Maximum reconnect delay in milliseconds |

### Request queue

| Property | Env variable | Default | Description |
|---|---|---|---|
| `modbus.queue.capacity` | `MODBUS_QUEUE_CAPACITY` | `50` | Maximum number of requests waiting in the dispatch queue |
| `modbus.queue.enqueue-timeout-ms` | `MODBUS_QUEUE_ENQUEUE_TIMEOUT_MS` | `2000` | Time to wait for a free queue slot before rejecting the request (milliseconds) |

### Server & Actuator

| Property | Env variable | Default | Description |
|---|---|---|---|
| `server.port` | `SERVER_PORT` | `8080` | HTTP port for Spring Boot Actuator endpoints |

Exposed Actuator endpoints: `health`, `info`, `metrics`, `prometheus`.

## Health check

```bash
curl http://localhost:8080/actuator/health
```

The custom `ModbusProxyHealthIndicator` reports `UP` when the upstream connection is established and the dispatcher is running.

## Example: Home Assistant configuration

```yaml
modbus:
  - name: SolarEdge
    type: tcp
    host: <proxy-host>
    port: 502
```

## Example: evcc configuration

```yaml
meters:
  - name: pv
    type: modbus
    uri: <proxy-host>:502
    id: 1
```
