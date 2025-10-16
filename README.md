# Java-Microservices-CQRS-Event-Sourcing-with-Kafka
Spring Boot Microservices that comply to the CQRS &amp; Event Sourcing patterns, powered by Java and Apache Kafka

## Opción A: Ejecutar con Docker Compose (Windows PowerShell)

Prerequisitos:
- Java 16 (requerido por los POMs)
- Docker Desktop

Infraestructura (Kafka, Zookeeper, MongoDB, MySQL):

1) En la raíz del repo, levanta los contenedores:

```powershell
docker compose up -d
```

Esto expone:
- Kafka broker externo: localhost:29092 (interno: kafka:9092)
- Zookeeper: localhost:2181
- MongoDB: localhost:27017
- MySQL: localhost:3306 (root / techbankRootPsw)

2) Compilar e instalar módulos en orden (usa wrappers mvnw.cmd):

```powershell
# Core
cd .\cqrs-es\cqrs.core
.\mvnw.cmd clean install -DskipTests

# Common
cd ..\..\bank-account\account.common
.\mvnw.cmd clean install -DskipTests

# Command API
cd ..\account.cmd
.\mvnw.cmd clean package -DskipTests

# Query API
cd ..\account.query
.\mvnw.cmd clean package -DskipTests
```

3) Ejecutar microservicios (en dos terminales):

Terminal A (Command API - puerto 5000):
```powershell
cd .\bank-account\account.cmd
.\mvnw.cmd spring-boot:run
```

Terminal B (Query API - puerto 5001):
```powershell
cd .\bank-account\account.query
.\mvnw.cmd spring-boot:run
```

Notas:
- `application.yml` ya apunta a MongoDB (27017), MySQL (3306) y Kafka (29092).
- La base de datos `bankAccount` se crea automáticamente (MySQL: `createDatabaseIfNotExist=true`).
- Si un puerto ya está en uso, libera el proceso o ajusta puertos y configs.

Para apagar la infraestructura:
```powershell
docker compose down
```

