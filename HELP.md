# Development and Runtime Guide

This project uses Spring Boot 3.5, Java 21, MySQL, Redis, and RocketMQ. Install JDK 21, Docker Compose, and `make` before starting. Maven is provided through the Maven Wrapper included in the project.

## Common Commands

| Makefile command | Underlying command | Purpose |
|---|---|---|
| `make` | `make help` | Display the available commands |
| `make up` | `docker compose up -d` | Start MySQL, Redis, RocketMQ, and the RocketMQ Console |
| `make run` | `./mvnw spring-boot:run` | Start Spring Boot; press Ctrl+C to stop it |
| `make test` | `./mvnw test` | Run the tests |
| `make build` | `./mvnw clean package` | Compile, test, and package the executable JAR |
| `make down` | `docker compose down` | Stop and remove containers and networks while retaining volumes |

## Development Startup

```bash
make up
docker compose ps
docker compose logs rocketmq-init
make run
```

`make up` starts the required services in the background. `rocketmq-init` is a one-time initialization container; its successful log output includes `create topic ... success`.

| Service | Address |
|---|---|
| Spring Boot API | `http://localhost:8080` |
| MySQL | `localhost:3306/taskdb` |
| Redis | `localhost:6379` |
| RocketMQ NameServer | `localhost:9876` |
| RocketMQ Broker | `localhost:10911` |
| RocketMQ Console | `http://localhost:8088` |

The MySQL username is `taskuser` and the password is `taskpass`. Connection settings can be overridden with the environment variables listed in [application.yaml](src/main/resources/application.yaml).

`init.sql` runs only when the MySQL volume is initialized for the first time. If the volume already exists, apply the latest schema manually:

```bash
docker compose exec -T mysql mysql -utaskuser -ptaskpass taskdb < init.sql
```

## Package and Run the JAR

```bash
make build
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

`make build` runs the tests and produces the JAR only after they pass. When development is complete, press Ctrl+C to stop the application and run `make down` to stop the supporting services.

## API Examples

The rate limiter uses a fixed window that starts with the first `/check` request for an API key. The Redis counter expires `windowSeconds` after that request; a new window starts on the next `/check` after expiry. `/usage` only reads the current counter and remaining TTL, so it does not consume quota or start a new window.

| Operation | Success status | Other relevant status |
|---|---|---|
| Create or update a rule | `201 Created` for create; `200 OK` for update | `400 Bad Request` for invalid input |
| Check access | `200 OK` when allowed | `429 Too Many Requests` when blocked; `404 Not Found` when the rule is missing |
| Read usage | `200 OK` | `404 Not Found` when the rule is missing |
| Delete a rule | `204 No Content` | `404 Not Found` when the rule is missing |

Create a rate-limit rule. The first request returns `201 Created`. Submitting the same `apiKey` again updates the rule, clears its current Redis window, and returns `200 OK`:

```bash
curl -i -X POST http://localhost:8080/limits \
  -H 'Content-Type: application/json' \
  -d '{"apiKey":"abc-123","limit":2,"windowSeconds":60}'
```

Check access. The first two requests return `200 OK`; subsequent requests return `429 Too Many Requests`. Every attempt is counted, including blocked requests:

```bash
curl -i 'http://localhost:8080/check?apiKey=abc-123'
```

Query the current usage, remaining quota, and window TTL:

```bash
curl -i 'http://localhost:8080/usage?apiKey=abc-123'
```

List rules with pagination:

```bash
curl -i 'http://localhost:8080/limits?page=0&size=20'
```

Delete a rule and its Redis counter:

```bash
curl -i -X DELETE http://localhost:8080/limits/abc-123
```

`/check`, `/usage`, and the delete operation return `404 Not Found` when no matching rule exists. `limit` and `windowSeconds` must be positive integers. `apiKey` must not be blank and may contain at most 128 characters.

## Rule Cache

Rate-limit rules are cached in Redis through the Spring Cache abstraction. The first lookup for an API key reads MySQL and stores the resulting rule under a key such as `rateLimitRules::abc-123`. Later `/check` and `/usage` requests use the cached rule instead of querying MySQL each time.

Cached rules expire after 10 minutes. Creating or updating a rule refreshes its cached value, while deleting a rule evicts it. The live usage counter remains separate and continues to use the atomic Redis Lua implementation.

## RocketMQ Behavior

A `RateLimitExceededEvent` is published only for the first exceeded request in each window, when `usage == limit + 1`. The consumer writes the event to `rate_limit_exceeded_events`. The unique index on `event_id` makes repeated delivery idempotent. Later blocked requests are still counted, but do not publish another event in the same window.

The producer sends messages asynchronously, so a RocketMQ publishing failure does not change the rate-limit decision already returned by `/check`. If audit events must never be lost in a production environment, the design can be extended with a transactional outbox.
