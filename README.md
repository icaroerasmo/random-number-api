# Random Number API

Tiny Spring Boot API that stores generated random numbers in daily files.

## Endpoints

- `GET /new?label={label}&min={min}&max={max}`
  - Always generates a new number for `label`, stores it, and returns it.
- `GET /?label={label}`
  - Returns the stored number for `label` without generating a new one.
  - Returns `404` when there is no stored value for that label.

Response format:

```json
{
  "label": "cameraA",
  "number": 6,
  "source": "new"
}
```

Persistence behavior:

- Numbers are stored in per-day files (`YYYY-MM-DD.properties`).
- Entries include value + timestamp and are valid for 24 hours.
- Cleanup runs asynchronously after each `GET /new` request:
  - removes records older than 24h
  - removes daily files that become empty

- Local run (Maven): defaults to `./numbers`.
- Docker run: defaults to `/app/numbers` via `APP_STORAGE_FILE`.

## Run locally

```bash
mvn spring-boot:run
```

## Test

```bash
mvn test
```

## Build and run with Docker

```bash
docker build -t random-number-api -f dockerfile .
mkdir -p ./numbers
docker run --rm -p 8080:8080 --name random-number-api \
  -v "$(pwd)/numbers:/app/numbers" \
  random-number-api
```

You can inspect the generated `./numbers/YYYY-MM-DD.properties` files from the host while the container is running.


