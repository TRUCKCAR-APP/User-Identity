package escuelaing.edu.co.truckcar.User_Identity.config;

import escuelaing.edu.co.truckcar.User_Identity.event.DomainEventPublisher;
import escuelaing.edu.co.truckcar.User_Identity.event.UserStatusChangedEvent;
import escuelaing.edu.co.truckcar.User_Identity.model.Role;
import escuelaing.edu.co.truckcar.User_Identity.model.User;
import escuelaing.edu.co.truckcar.User_Identity.model.UserStatus;
import escuelaing.edu.co.truckcar.User_Identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Al arrancar se publica el estado de cada conductor, página por página, sin tumbar el servicio si Kafka falla. */
class AccountStatusSnapshotPublisherTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final DomainEventPublisher publisher = mock(DomainEventPublisher.class);
    private final AccountStatusSnapshotPublisher snapshot = new AccountStatusSnapshotPublisher(repository, publisher);

    private static User driver(UserStatus status) {
        return User.builder().id(UUID.randomUUID()).email(UUID.randomUUID() + "@truckcar.co").passwordHash("x")
                .firstName("C").lastName("D").role(Role.CONDUCTOR).status(status).build();
    }

    @Test
    void publishesEveryDriverAcrossPages() {
        List<User> first = List.of(driver(UserStatus.ACTIVE), driver(UserStatus.PENDING_VERIFICATION));
        List<User> second = List.of(driver(UserStatus.SUSPENDED));
        when(repository.findAllWithFilters(eq(Role.CONDUCTOR), isNull(), any(Pageable.class))).thenAnswer(invocation -> {
            Pageable page = invocation.getArgument(2);
            return page.getPageNumber() == 0
                    ? new PageImpl<>(first, PageRequest.of(0, 2), 3)
                    : new PageImpl<>(second, PageRequest.of(1, 2), 3);
        });

        snapshot.run(null);

        ArgumentCaptor<UserStatusChangedEvent> events = ArgumentCaptor.forClass(UserStatusChangedEvent.class);
        verify(publisher, times(3)).publish(events.capture());
        assertThat(events.getAllValues()).extracting(UserStatusChangedEvent::status)
                .containsExactly("ACTIVE", "PENDING_VERIFICATION", "SUSPENDED");
        assertThat(events.getAllValues()).allMatch(event -> event.previousStatus() == null);
    }

    @Test
    void aKafkaFailureDoesNotStopTheService() {
        when(repository.findAllWithFilters(eq(Role.CONDUCTOR), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(driver(UserStatus.ACTIVE))));
        doThrow(new IllegalStateException("broker caído")).when(publisher).publish(any(UserStatusChangedEvent.class));

        assertThatCode(() -> snapshot.run(null)).doesNotThrowAnyException();
    }
}
