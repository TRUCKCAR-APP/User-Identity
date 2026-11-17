package escuelaing.edu.co.truckcar.User_Identity.dto.response;

import java.util.List;

/** Códigos de respaldo de un solo uso. Se muestran una única vez: en la base solo queda su SHA-256. */
public record TwoFactorBackupCodesResponse(List<String> backupCodes, String message) {
}
