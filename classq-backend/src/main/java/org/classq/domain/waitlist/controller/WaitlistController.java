package org.classq.domain.waitlist.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.classq.domain.waitlist.dto.WaitlistListResponseDto;
import org.classq.domain.waitlist.dto.WaitlistRequestDto;
import org.classq.domain.waitlist.dto.WaitlistResponseDto;
import org.classq.domain.waitlist.service.WaitlistService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/waitlists")
public class WaitlistController {

    private final WaitlistService waitlistService;

    // 내 대기 목록 조회
    @GetMapping("/me")
    public ResponseEntity<WaitlistListResponseDto> getMyWaitlists(
            @AuthenticationPrincipal Long accountId) {
        return ResponseEntity.ok(waitlistService.getMyWaitlists(accountId));
    }

    // 대기자 등록
    @PostMapping
    public ResponseEntity<WaitlistResponseDto> register(
            @AuthenticationPrincipal Long accountId,
            @Valid @RequestBody WaitlistRequestDto request) {
        return ResponseEntity.status(201).body(waitlistService.register(accountId, request.getCourseId()));
    }

    // 대기자 취소
    @DeleteMapping("/{waitlistId}")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal Long accountId,
            @PathVariable Long waitlistId) {
        waitlistService.cancel(accountId, waitlistId);
        return ResponseEntity.noContent().build();
    }

    // 대기 수락
    @PostMapping("/{waitlistId}/accept")
    public ResponseEntity<Void> accept(
            @AuthenticationPrincipal Long accountId,
            @PathVariable Long waitlistId) {
        waitlistService.accept(accountId, waitlistId);
        return ResponseEntity.ok().build();
    }
}
