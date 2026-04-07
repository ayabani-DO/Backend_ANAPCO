package tn.esprit.examen.nomPrenomClasseExamen;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Role;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.RoleRepository;

@SpringBootApplication
@EnableJpaAuditing
@EnableAsync

public class nomPrenomClasseExamenApplication {

    public static void main(String[] args) {
        SpringApplication.run(nomPrenomClasseExamenApplication.class, args);
    }
    @Bean
    public CommandLineRunner runner(RoleRepository rolerepo){
        return args -> {
            if (rolerepo.findByName("USER").isEmpty()){
                rolerepo.save(Role.builder().name("USER").build());
            } if (rolerepo.findByName("AGENTF").isEmpty()) {
                rolerepo.save(Role.builder().name("AGENTF").build());

            }
            if (rolerepo.findByName("ADMIN").isEmpty()){
                rolerepo.save(Role.builder().name("ADMIN").build());
            }

        };
    }
}
