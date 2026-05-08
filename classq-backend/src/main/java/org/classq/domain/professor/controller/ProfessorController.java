package org.classq.domain.professor.controller;

import lombok.RequiredArgsConstructor;
import org.classq.domain.professor.dto.ProfessorResponseDto;
import org.classq.domain.professor.service.ProfessorService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/professors")
public class ProfessorController {

    private final ProfessorService professorService;

    // 내 정보 조회
    @GetMapping("/me")
    public ResponseEntity<ProfessorResponseDto> getMe(@AuthenticationPrincipal Long accountId) {
        return ResponseEntity.ok(professorService.getMe(accountId));
    }



}
