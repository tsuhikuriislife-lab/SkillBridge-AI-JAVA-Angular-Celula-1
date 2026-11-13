.PHONY: up down reset logs ps observability test-back test-back-unit test-front test-livekit test-livekit-stream build db-notifications test-notifications logs-notifications test-data

up:
	docker compose up --build -d

down:
	docker compose down

reset:
	docker compose down -v --remove-orphans

	docker compose up --build -d

logs:
	docker compose logs -f --tail=200

ps:
	docker compose ps

observability:
	docker compose --profile observability up --build -d

test-back:
	cd backend && mvn test

test-back-unit:
	cd backend && mvn -Dtest=OfferingCrudServiceTest,OfferingCatalogServiceTest,ServiceEnrollmentServiceTest test

test-front:
	cd frontend && npm ci && npm test -- --watch=false

test-livekit:
	node scripts/validate-livekit-metrics.js

test-livekit-stream:
	@if [ -z "$$LIVEKIT_API_KEY" ] || [ -z "$$LIVEKIT_API_SECRET" ]; then \
		echo "Error: exporta LIVEKIT_API_KEY y LIVEKIT_API_SECRET antes de ejecutar docker compose up. Si LiveKit arranca sin claves, el cliente recibe 'Connection reset by peer'."; \
		exit 1; \
	fi
	@if [ ! -d scripts/livekit-stream-test/node_modules ]; then \
		cd scripts/livekit-stream-test && npm ci; \
	fi
	cd scripts/livekit-stream-test && timeout 60s node stream-test.js

test-data:
	docker compose --profile data run --rm pyspark

build:
	docker compose build

db-notifications:
	docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$$POSTGRES_USER" -d "$$POSTGRES_DB"' < ops/postgres/init/10-notifications-db.sql

test-notifications:
	cd notifications-service && npm ci && npm run lint:check && npm test

logs-notifications:
	docker compose logs -f --tail=200 notifications
