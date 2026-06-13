package org.classq.domain.admin.controller;

import lombok.RequiredArgsConstructor;
import org.classq.domain.admin.dto.PendingProfessorDto;
import org.classq.domain.admin.service.AdminAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    // 승인 대기 교수 목록 조회
    @GetMapping("/accounts/pending")
    public ResponseEntity<List<PendingProfessorDto>> getPendingProfessors() {
        return ResponseEntity.ok(adminAccountService.getPendingProfessors());
    }

    // 교수 승인
    @PatchMapping("/accounts/{accountId}/approve")
    public ResponseEntity<Void> approveProfessor(@PathVariable Long accountId) {
        adminAccountService.approveProfessor(accountId);
        return ResponseEntity.noContent().build();
    }
}
