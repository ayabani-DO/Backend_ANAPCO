package tn.esprit.examen.nomPrenomClasseExamen.repositories;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Token;
import tn.esprit.examen.nomPrenomClasseExamen.entities.TokenType;

import java.util.Optional;

@Repository

public interface TokenRepository extends JpaRepository<Token, Long> {
    Optional<Token> findByToken(String token);

    /**
     * Token-hygiene (Part 2B): deletes a user's still-unused tokens of one purpose so that issuing a
     * fresh token invalidates the previous one. Never touches the other purpose, and never touches an
     * already-consumed token ({@code validatedAt} not null). Runs inside the caller's transaction, so
     * it rolls back with the rest if the follow-up email fails.
     */
    @Modifying
    @Query("delete from Token t where t.user.idUser = :idUser and t.type = :type and t.validatedAt is null")
    int deletePreviousUnusedTokens(@Param("idUser") Long idUser, @Param("type") TokenType type);
}
