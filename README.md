# Random Number API

Tiny Spring Boot API that stores the latest generated random number in a local file at project root.

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

The persisted file is `latest-number.txt` using `label=value` entries.

- Local run (Maven): defaults to `./latest-number.txt`.
- Docker run: defaults to `/app/numbers/latest-number.txt` via `APP_STORAGE_FILE`.

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

You can edit `./numbers/latest-number.txt` from the host while the container is running.



