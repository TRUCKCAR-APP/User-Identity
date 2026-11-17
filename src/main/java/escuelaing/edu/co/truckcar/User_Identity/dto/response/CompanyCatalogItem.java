package escuelaing.edu.co.truckcar.User_Identity.dto.response;

import java.util.UUID;

/** Transportadora en el catálogo público del registro: solo lo necesario para elegirla. */
public record CompanyCatalogItem(UUID id, String name) {
}
