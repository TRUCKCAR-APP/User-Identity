package escuelaing.edu.co.truckcar.User_Identity.config;

import escuelaing.edu.co.truckcar.User_Identity.event.DomainEventPublisher;
import escuelaing.edu.co.truckcar.User_Identity.event.UserStatusChangedEvent;
import escuelaing.edu.co.truckcar.User_Identity.model.Role;
import escuelaing.edu.co.truckcar.User_Identity.model.User;
import escuelaing.edu.co.truckcar.User_Identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Al arrancar publica el estado de la cuenta de cada conductor como user.status.changed (previousStatus nulo).
 * Así fleets-drivers conoce también a los conductores que existían antes de que hubiera eventos de estado, y se
 * reconcilia si perdió alguno. Es idempotente: el consumidor guarda el último estado por conductor.
 * Si Kafka no responde, se registra y el servicio arranca igual (el siguiente arranque vuelve a intentarlo).
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "truckcar.user-events.status-snapshot-on-startup", havingValue = "true", matchIfMissing = true)
public class AccountStatusSnapshotPublisher implements ApplicationRunner {

    private static final int PAGE_SIZE = 200;

    private final UserRepository userRepository;
    private final DomainEventPublisher eventPublisher;

    @Override
    public void run(ApplicationArguments args) {
        int published = 0;
        try {
            Page<User> page;
            int number = 0;
            do {
                page = userRepository.findAllWithFilters(Role.CONDUCTOR, null,
                        PageRequest.of(number++, PAGE_SIZE, Sort.by("createdAt")));
                for (User user : page) {
                    eventPublisher.publish(new UserStatusChangedEvent(user.getId(), user.getRole().name(), null,
                            user.getStatus().name(), Instant.now().toEpochMilli()));
                    published++;
                }
            } while (page.hasNext());
            log.info("Estado de cuenta publicado para {} conductores (reconciliación de arranque)", published);
        } catch (RuntimeException e) {
            log.warn("No se pudo publicar el estado de las cuentas al arrancar ({} publicados): {}", published, e.getMessage());
        }
    }
}
