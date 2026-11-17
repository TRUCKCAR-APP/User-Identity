package escuelaing.edu.co.truckcar.User_Identity.repository;

import escuelaing.edu.co.truckcar.User_Identity.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {

    boolean existsByNit(String nit);

    Optional<Company> findByOwnerUserId(UUID ownerUserId);

    List<Company> findAllByOrderByNameAsc();
}
