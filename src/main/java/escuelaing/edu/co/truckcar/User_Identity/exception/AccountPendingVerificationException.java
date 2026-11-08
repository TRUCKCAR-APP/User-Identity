package escuelaing.edu.co.truckcar.User_Identity.exception;

public class AccountPendingVerificationException extends RuntimeException {
    public AccountPendingVerificationException(String message) {
        super(message);
    }
}