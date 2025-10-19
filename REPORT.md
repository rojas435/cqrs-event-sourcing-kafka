# Informe técnico: Garantía de orden de consumo de eventos

Resumen breve

Se centralizó la publicación de eventos en un único topic (`account-events`) y se garantizó el orden por entidad publicando siempre con `key = aggregateId`. Además se unificó el consumidor en el servicio de consulta para demultiplexar por un header `eventType` y se ajustó la lógica de republish/restore para conservar key y orden. Con esto la proyección (read model) puede reconstruirse determinísticamente desde el event store.

Objetivos

- Garantizar que todos los eventos de una misma entidad (aggregate) se consuman y apliquen en el mismo orden.
- Permitir reproducir (replay) todos los eventos desde el event store para reconstruir la read model.

Cambios realizados (resumen técnico)

- Topic único
  - Nuevo topic: `account-events`. Unifica la publicación de eventos de cuenta.

- Producer: enviar key y header
  - `AccountEventStore` ahora publica con `ProducerRecord(topic, key=aggregateId, value=event)`.
  - Se añade header `eventType` con el nombre del evento (por ejemplo `FundsDepositedEvent`).
  - Beneficio: la key asegura que Kafka enrute todos los eventos de la misma entidad a la misma partición (orden por aggregate).

- Consumer unificado y robusto
  - `AccountEventConsumer` ahora escucha `${kafka.topic}` con un único `@KafkaListener`.
  - Extrae el `value()` cuando el payload es un `ConsumerRecord` para evitar errores de deserialización.
  - Usa `eventType` (header) y `ObjectMapper` para convertir y despachar al handler correspondiente.

- Republish / restore
  - `AccountEventSourcingHandler.republishEvents()` y el flujo de restore publican eventos con la misma key y header, permitiendo reconstruir la read model en el mismo orden.

- Correcciones menores
  - Bugfix: `AccountEventHandler.on(FundsWithdrawnEvent)` actualizado para aplicar correctamente el cambio de balance.
  - `application.yml` limpiados para evitar errores de configuración y definir `kafka.topic`, `spring.kafka.*`, `server.port`, `spring.datasource` y `spring.jpa`.

Archivos modificados (lista)

- account.cmd/src/main/java/.../infrastructure/AccountEventStore.java
- account.cmd/src/main/java/.../infrastructure/AccountEventSourcingHandler.java
- account.query/src/main/java/.../infrastructure/consumers/AccountEventConsumer.java
- account.query/src/main/java/.../infrastructure/consumers/EventConsumer.java
- account.query/src/main/java/.../infrastructure/handlers/AccountEventHandler.java
- account.cmd/src/main/resources/application.yml
- account.query/src/main/resources/application.yml
- docker-compose.yml (ajustes: Kafdrop, puertos)

¿Por qué garantiza orden esto? (explicación técnica concisa)

- Kafka garantiza orden por partición.
- La partición se elige en función de la key. Publicando con `key = aggregateId` todas las events de una entidad llegan a la misma partición.
- Si al hacer replay republicas con la misma key y en el mismo orden guardado en el event store, la proyección se reconstruye de forma determinista.

Verificación recomendada (pasos cortos)

1. Arrancar infra (Kafka, ZK, Mongo, MySQL, Kafdrop).
2. Arrancar servicios:
   - Command app (puerto 5000)
   - Query app (puerto 5001)
3. Crear cuenta (POST /api/v1/openBankAccount) — guardar `id`.
4. Hacer operaciones: PUT /api/v1/depositFunds/{id}, PUT /api/v1/withdrawFunds/{id}.
5. En Kafdrop, abrir `account-events` y comprobar:
   - Cada registro tiene `key == <aggregateId>`.
   - Header `eventType` presente.
6. Consultar read DB: GET http://localhost:5001/api/v1/bankAccountLookup/byId/{id} y verificar balances.
7. Test de replay:
   - Detener `account.query`, opcionalmente limpiar read DB, arrancar `account.query`.
   - POST http://localhost:5000/api/v1/restoreReadDb (republica eventos desde Mongo).
   - Verificar que los eventos republicados aparecen en Kafdrop y la read DB se reconstruye correctamente.

Buenas prácticas aplicadas y recomendadas

- Producers
  - En producción: `acks=all`, `enable.idempotence=true` para evitar duplicados.
  - Key: usar `StringSerializer` para key y `JsonSerializer` para value.

- Consumers
  - `auto-offset-reset=earliest` para nuevos consumers que necesiten leer desde el inicio.
  - Acknowledge manual y confirmar offset sólo tras aplicar la proyección con éxito.
  - `spring.kafka.consumer.properties.spring.json.trusted.packages='*'` si se usa `JsonDeserializer`.

- Event Sourcing
  - Mantener version/sequence por aggregate y aplicar verificación optimista para concurrencia.
  - Implementar idempotencia en handlers: ignorar eventos con `version <= lastAppliedVersion`.

- Republish
  - Republíca con la misma `key` y en el mismo orden que fueron persistidos.

Puntos de control (troubleshooting)

- Si faltan `key` en mensajes → no hay garantía de orden por aggregate.
- Si falta `eventType` → consumidor no podrá demultiplexar.
- Errores de Jackson sobre `ConsumerRecord` → puede indicar que el listener está recibiendo el wrapper; se resolvió extrayendo `.value()`.
- Si la read DB no queda consistente tras replay: revisar versiones aplicadas y lógica idempotente en el handler.

Resultado esperado

- Eventos de una misma cuenta se consumen y aplican en el mismo orden.
- `restoreReadDb` reconstruye la proyección de forma determinista desde el event store.
