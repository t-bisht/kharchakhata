# infra — local docker-compose stack

Services on a shared bridge network `kk-net`:

| service      | image / build         | host port |
|--------------|-----------------------|-----------|
| postgres     | postgres:16-alpine    | 5432      |
| ingestion    | py/ingestion          | 8080      |
| stager       | py/stager             | 8081      |
| login_engine  | java/login_engine      | 8082      |
| web          | web (nginx)           | 3000      |

## Quickstart

```bash
cp infra/.env.example infra/.env      # edit secrets
(cd java && ./gradlew :login_engine:bootJar)   # prereq for login_engine image
docker compose -f infra/docker-compose.yml up --build
```

## Postgres init

Any `.sql` file under `infra/postgres/init/` runs once, in filename order,
the first time the `kk-pg-data` volume is created. Wipe the volume
(`docker volume rm kharchakhata_kk-pg-data`) to re-run.
