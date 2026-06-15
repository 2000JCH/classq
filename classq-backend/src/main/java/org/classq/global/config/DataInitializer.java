package org.classq.global.config;

import lombok.RequiredArgsConstructor;
import org.classq.domain.account.entity.Account;
import org.classq.domain.account.entity.AccountStatus;
import org.classq.domain.account.entity.Role;
import org.classq.domain.account.repository.AccountRepository;
import org.classq.domain.department.entity.Department;
import org.classq.domain.department.repository.DepartmentRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final DepartmentRepository departmentRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initDepartments();
        initAdmin();
    }

    private void initDepartments() {
        if (departmentRepository.count() > 0) return;

        List<String> names = List.of(
                "컴퓨터공학과", "전자공학과", "기계공학과", "경영학과", "수학과"
        );
        for (String name : names) {
            departmentRepository.save(Department.builder().name(name).build());
        }
    }

    private void initAdmin() {
        if (accountRepository.existsByEmail("admin@classq.com")) return;

        accountRepository.save(Account.builder()
                .email("admin@classq.com")
                .password(passwordEncoder.encode("admin1234!"))
                .role(Role.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build());
    }
}
