# CrackHash

Distributed hash cracking system with Spring Boot microservices.

## Architecture

- **Manager** - Координирующий сервис на порту 8080
- **Worker** - Рабочий сервис на порту 8081

## Requirements

- Java 17+
- Gradle 6+
- Docker & Docker Compose

## Building

Сборка всех модулей:
```bash
./gradlew clean build
```

Сборка отдельного модуля:
```bash
./gradlew :manager:build
./gradlew :worker:build
```

## Running with Docker Compose

1. Сборка JAR файлов:
```bash
./gradlew clean build
```

2. Запуск сервисов:
```bash
docker-compose up -d
```

3. Просмотр логов:
```bash
docker-compose logs -f
```

4. Остановка сервисов:
```bash
docker-compose down
```

## API Endpoints

### Manager (http://localhost:8080)
- Health check: `GET /actuator/health`
- TODO: добавьте свои endpoints

### Worker (http://localhost:8081)
- Health check: `GET /actuator/health`
- TODO: добавьте свои endpoints

## Network Configuration

Сервисы общаются внутри Docker сети `crackhash-network`:
- Manager доступен по имени `manager` на порту 8080
- Worker доступен по имени `worker` на порту 8081

Worker может обращаться к Manager по адресу: `http://manager:8080`

## Development

Для локальной разработки без Docker:

1. Запуск Manager:
```bash
./gradlew :manager:bootRun
```

2. Запуск Worker (в другом терминале):
```bash
./gradlew :worker:bootRun
```

## Project Structure

```
CrackHash/
├── manager/
│   ├── src/main/java/com/crackhash/manager/
│   │   └── ManagerApplication.java
│   ├── src/main/resources/
│   │   └── application.yml
│   ├── build.gradle
│   └── Dockerfile
├── worker/
│   ├── src/main/java/com/crackhash/worker/
│   │   └── WorkerApplication.java
│   ├── src/main/resources/
│   │   └── application.yml
│   ├── build.gradle
│   └── Dockerfile
├── build.gradle
├── settings.gradle
└── docker-compose.yml
```
