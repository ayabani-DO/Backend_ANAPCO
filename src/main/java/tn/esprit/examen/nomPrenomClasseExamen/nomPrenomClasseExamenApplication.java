package tn.esprit.examen.nomPrenomClasseExamen;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Role;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.RoleRepository;
import tn.esprit.examen.nomPrenomClasseExamen.security.SecurityRoles;

@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
public class nomPrenomClasseExamenApplication {

    public static void main(String[] args) {
        SpringApplication.run(nomPrenomClasseExamenApplication.class, args);
    }

    @Bean
    public CommandLineRunner runner(RoleRepository rolerepo) {
        return args -> {
            if (rolerepo.findByName(SecurityRoles.ADMIN).isEmpty()) {
                rolerepo.save(Role.builder().name(SecurityRoles.ADMIN).build());
            }
            if (rolerepo.findByName(SecurityRoles.OPS_MANAGER).isEmpty()) {
                rolerepo.save(Role.builder().name(SecurityRoles.OPS_MANAGER).build());
            }
            if (rolerepo.findByName(SecurityRoles.FINANCE_CONTROLLER).isEmpty()) {
                rolerepo.save(Role.builder().name(SecurityRoles.FINANCE_CONTROLLER).build());
            }
            if (rolerepo.findByName(SecurityRoles.VIEWER).isEmpty()) {
                rolerepo.save(Role.builder().name(SecurityRoles.VIEWER).build());
            }
        };
    }
}
