package io.github.repairticket.repository;

import io.github.repairticket.domain.AppUser;
import io.github.repairticket.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<AppUser> findAllByRoleOrderByFullNameAsc(UserRole role);
}
