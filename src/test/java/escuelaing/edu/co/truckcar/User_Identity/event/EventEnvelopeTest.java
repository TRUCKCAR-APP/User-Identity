package escuelaing.edu.co.truckcar.User_Identity.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import escuelaing.edu.co.truckcar.User_Identity.dto.event.EventEnvelope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventEnvelopeTest {

    @Test
    void serializesUserCreatedEnvelope() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        EventEnvelope<UserRegisteredEvent> envelope = EventEnvelope.<UserRegisteredEvent>builder()
                .eventId(eventId)
                .eventType("user.created")
                .occurredAt(Instant.parse("2026-10-10T23:00:00Z"))
                .correlationId(userId)
                .version(1)
                .producer("user-identity")
                .payload(new UserRegisteredEvent(userId, "driver@example.com", "DRIVER", 1791678000000L))
                .build();

        EventEnvelope<UserRegisteredEvent> restored = mapper.readValue(
                mapper.writeValueAsString(envelope),
                mapper.getTypeFactory().constructParametricType(
                        EventEnvelope.class,
                        UserRegisteredEvent.class
                )
        );

        assertThat(restored.getEventId()).isEqualTo(eventId);
        assertThat(restored.getEventType()).isEqualTo("user.created");
        assertThat(restored.getOccurredAt()).isEqualTo(Instant.parse("2026-10-10T23:00:00Z"));
        assertThat(restored.getCorrelationId()).isEqualTo(userId);
        assertThat(restored.getVersion()).isEqualTo(1);
        assertThat(restored.getProducer()).isEqualTo("user-identity");
        assertThat(restored.getPayload().email()).isEqualTo("driver@example.com");
    }
}
