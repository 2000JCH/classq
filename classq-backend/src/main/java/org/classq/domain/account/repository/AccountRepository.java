package org.classq.domain.account.repository;

import org.classq.domain.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByEmailAndDeletedAtIsNull(String email);
    Optional<Account> findByIdAndDeletedAtIsNull(Long id);
    boolean existsByEmail(String email);
}
