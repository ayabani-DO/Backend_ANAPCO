package tn.esprit.examen.nomPrenomClasseExamen.services;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Role;
import tn.esprit.examen.nomPrenomClasseExamen.entities.User;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.RoleRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.UserRepository;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public UserService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    public void assignRoleToUser(Long idUser, String roleName) {
        User user = userRepository.findById(idUser)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        if (!user.getRoles().contains(role)) {
            user.getRoles().add(role);
            userRepository.save(user);
        } else {
            throw new IllegalArgumentException("User already has this role!");
        }
    }

    public void assignAndReplaceRoleToUser(Long idUser, String roleName) {
        User user = userRepository.findById(idUser)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        user.getRoles().clear();

        user.getRoles().add(role);
        userRepository.save(user);
    }


    public void updateFullName(Long idUser, String fullName) {
        User user = userRepository.findById(idUser)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (fullName != null && fullName.trim().contains(" ")) {
            String[] parts = fullName.trim().split(" ", 2);
            user.setFirstName(parts[0]);
            user.setLastName(parts[1]);
            userRepository.save(user);
        } else {
            throw new IllegalArgumentException("Full name must include first and last name separated by space.");
        }
    }

    public void updateDateOfBirth(Long idUser, LocalDate newDateOfBirth) {
        User user = userRepository.findById(idUser)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setDateOfBirth(newDateOfBirth);
        userRepository.save(user);
    }

    public User getProfile(Long idUser){
        return userRepository.findById(idUser).orElse(null);
    }

    public List<User> getAllUsersExcept(Long currentUserId) {
        return userRepository.findAllExcept(currentUserId);
    }

    public void DeleteUser(Long idUser){
        userRepository.deleteById(idUser);
    }



    public void banUser(Long idUser, boolean lockStatus) {
        User user = userRepository.findById(idUser)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setAccountLocked(lockStatus);
        userRepository.save(user);
    }

}