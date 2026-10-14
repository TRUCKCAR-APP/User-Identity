package escuelaing.edu.co.truckcar.User_Identity.event;

/**
 * Interfaz para publicar eventos de dominio al bus de eventos compartido.
 * <p>
 * Actualmente existe una implementación simple que usa logs;
 * cuando se integre Kafka/Amazon MSK se creará una implementación real.
 * </p>
 */
public interface DomainEventPublisher {

    /**
     * Publica un evento de registro de usuario.
     */
    void publish(UserRegisteredEvent event);

    /**
     * Publica un evento de cambio de rol.
     */
    void publish(UserRoleChangedEvent event);
}
