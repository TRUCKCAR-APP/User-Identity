package escuelaing.edu.co.truckcar.User_Identity.event;

import escuelaing.edu.co.truckcar.User_Identity.dto.event.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaDomainEventPublisher implements DomainEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${truckcar.kafka.topic.user-events:truckcar.events.user-events}")
    private String userEventsTopic;

    @Override
    public void publish(UserRegisteredEvent event) {
        publishEnvelope("user.created", event.userId(), event);
    }

    @Override
    public void publish(UserRoleChangedEvent event) {
        publishEnvelope("user.role.changed", event.userId(), event);
    }

    private void publishEnvelope(String eventType, UUID correlationId, Object payload) {
        EventEnvelope<Object> envelope = EventEnvelope.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .occurredAt(Instant.now())
                .correlationId(correlationId)
                .version(1)
                .producer("user-identity")
                .payload(payload)
                .build();

        try {
            kafkaTemplate.send(
                    userEventsTopic,
                    correlationId.toString(),
                    envelope
            );
            log.info("Evento {} publicado en {} para usuario {}",
                    eventType, userEventsTopic, correlationId);
        } catch (RuntimeException exception) {
            log.error("No se pudo publicar evento {} en {}", eventType, userEventsTopic, exception);
            throw exception;
        }
    }
}
