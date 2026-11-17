package escuelaing.edu.co.truckcar.User_Identity.service.impl;

import escuelaing.edu.co.truckcar.User_Identity.model.AffiliationStatus;
import escuelaing.edu.co.truckcar.User_Identity.model.DriverType;
import escuelaing.edu.co.truckcar.User_Identity.model.User;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas puras de la afiliación: quién puede recibir cargas y cómo se normaliza el NIT. */
class CompanyRulesTest {

    private static User driver(DriverType type, UUID company, AffiliationStatus status) {
        return User.builder().driverType(type).companyId(company).affiliationStatus(status).build();
    }

    @Test
    void onlyAnActiveCompanyDriverOrALegacyAccountCanReceiveLoads() {
        UUID company = UUID.randomUUID();
        assertThat(driver(null, null, null).affiliationAllowsLoads()).as("cuenta anterior").isTrue();
        assertThat(driver(DriverType.INDEPENDIENTE, null, null).affiliationAllowsLoads()).isFalse();
        assertThat(driver(DriverType.EMPRESA, company, AffiliationStatus.PENDIENTE).affiliationAllowsLoads()).isFalse();
        assertThat(driver(DriverType.EMPRESA, company, AffiliationStatus.RECHAZADA).affiliationAllowsLoads()).isFalse();
        assertThat(driver(DriverType.EMPRESA, company, AffiliationStatus.ACTIVA).affiliationAllowsLoads()).isTrue();
        assertThat(driver(DriverType.EMPRESA, null, AffiliationStatus.ACTIVA).affiliationAllowsLoads()).isFalse();
    }

    @Test
    void theNitKeepsOnlyItsDigitsWithoutTheCheckDigit() {
        assertThat(CompanyServiceImpl.normalizeNit("900.123.456-7")).isEqualTo("900123456");
        assertThat(CompanyServiceImpl.normalizeNit(" 800 765 432 ")).isEqualTo("800765432");
        assertThatThrownBy(() -> CompanyServiceImpl.normalizeNit("12-3")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompanyServiceImpl.normalizeNit(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
