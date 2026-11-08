package escuelaing.edu.co.truckcar.User_Identity.model;

public enum UserStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED;

    /**
     * Valida si la transición de estado solicitada está permitida por las reglas de negocio.
     */
    public boolean canTransitionTo(UserStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case PENDING_VERIFICATION -> target == ACTIVE || target == SUSPENDED;
            case ACTIVE -> target == SUSPENDED;
            case SUSPENDED -> target == ACTIVE;
        };
    }
}