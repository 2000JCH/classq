package org.classq.domain.professor.controller;

import lombok.RequiredArgsConstructor;
import org.classq.domain.professor.dto.ProfessorRequestDto;
import org.classq.domain.professor.dto.ProfessorResponseDto;
import org.classq.domain.professor.entity.Professor;
import org.classq.domain.professor.service.ProfessorService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    // 내 정보 수정
    @PutMapping("/me")
    public ResponseEntity<ProfessorResponseDto> updateMe(@AuthenticationPrincipal Long accountId, @RequestBody ProfessorRequestDto request) {
        return ResponseEntity.ok(professorService.updateMe(accountId, request));

    }
}
