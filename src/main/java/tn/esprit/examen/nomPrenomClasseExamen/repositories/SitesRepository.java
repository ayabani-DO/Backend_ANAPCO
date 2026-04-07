package tn.esprit.examen.nomPrenomClasseExamen.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;

@Repository
public interface SitesRepository extends JpaRepository<Sites,Long> {
    
    @Query("SELECT COUNT(s) FROM Sites s WHERE s.countryCode = ?1")
    long countByCountryCode(String countryCode);
}
