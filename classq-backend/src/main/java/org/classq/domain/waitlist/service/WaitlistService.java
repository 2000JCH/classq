package org.classq.domain.waitlist.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.course.repository.CourseRepository;
import org.classq.domain.enrollment.repository.EnrollmentRepository;
import org.classq.domain.enrollment.service.EnrollmentService;
import org.classq.domain.notification.entity.Notification;
import org.classq.domain.notification.entity.NotificationType;
import org.classq.domain.notification.repository.NotificationRepository;
import org.classq.domain.notification.service.SseEmitterService;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.classq.domain.student.entity.Student;
import org.classq.domain.student.repository.StudentRepository;
import org.classq.domain.waitlist.dto.WaitlistListResponseDto;
import org.classq.domain.waitlist.dto.WaitlistResponseDto;
import org.classq.domain.waitlist.entity.Waitlist;
import org.classq.domain.waitlist.entity.WaitlistStatus;
import org.classq.domain.waitlist.repository.WaitlistRepository;
import org.classq.global.exception.BusinessException;
import org.classq.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class WaitlistService {

    private static final String ZSET_PREFIX = "waitlist:zset:course:";

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final WaitlistRepository waitlistRepository;
    private final NotificationRepository notificationRepository;
    private final SseEmitterService sseEmitterService;
    private final EnrollmentService enrollmentService;
    private final RedisTemplate<String, String> redisTemplate;

    // 내 대기 목록 조회
    @Transactional(readOnly = true)
    public WaitlistListResponseDto getMyWaitlists(Long accountId) {
        // 1. 학생 조회
        Student student = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        Long studentId = student.getId();

        // 2. 대기 목록 조회 (WAITING, NOTIFIED만 조회)
        List<WaitlistResponseDto> waitlists = waitlistRepository
                .findByStudent_IdAndWaitlistStatusInAndDeletedAtIsNull(
                        studentId, List.of(WaitlistStatus.WAITING, WaitlistStatus.NOTIFIED))
                .stream()
                .map(w -> {
                    // rank는 ZSET에서 동적 계산 (취소/만료가 반영된 실제 순번)
                    Long rankLong = redisTemplate.opsForZSet()
                            .rank(ZSET_PREFIX + w.getCourse().getId(), String.valueOf(w.getId()));
                    int rank = (rankLong != null) ? rankLong.intValue() + 1 : w.getRank();
                    return new WaitlistResponseDto(
                            w.getId(), w.getCourse().getId(), w.getCourse().getName(),
                            rank, w.getWaitlistStatus());
                })
                .toList();

        // 3. 현재 학점 조회 (Redis 캐시 우선, 없으면 RDS)
        String creditsKey = "credits:student:" + studentId;
        String creditsCached = redisTemplate.opsForValue().get(creditsKey);
        if (creditsCached == null) {
            long loaded = enrollmentRepository.sumCreditsByStudentId(studentId);
            creditsCached = String.valueOf(loaded);
            redisTemplate.opsForValue().set(creditsKey, creditsCached);
        }
        int currentCredits = Integer.parseInt(creditsCached);

        return new WaitlistListResponseDto(waitlists, currentCredits, 19);
    }

    // 대기자 등록
    @Transactional
    public WaitlistResponseDto register(Long accountId, Long courseId) {
        // 1. studentId — Redis 캐시 (로그인 시 세팅)
        String studentIdStr = redisTemplate.opsForValue().get("student:account:" + accountId);
        if (studentIdStr == null) throw new BusinessException(ErrorCode.STUDENT_NOT_FOUND);
        Long studentId = Long.valueOf(studentIdStr);

        // 2. 강의 존재 확인 — Redis 캐시 (DataInitializer 시 세팅)
        String courseName = redisTemplate.opsForValue().get("course:" + courseId + ":name");
        if (courseName == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);

        // 3. 대기 슬롯 차감 (음수면 즉시 롤백 후 거절)
        String slotKey = "waitlist:course:" + courseId;
        Long remaining = redisTemplate.opsForValue().decrement(slotKey);
        if (remaining == null || remaining < 0) {
            redisTemplate.opsForValue().increment(slotKey);
            throw new BusinessException(ErrorCode.WAITLIST_CLOSED);
        }

        String zsetKey = ZSET_PREFIX + courseId;
        // zaddDone[0]: ZADD 성공 여부 추적 — catch와 afterRollback의 슬롯 이중 복구 방지
        boolean[] zaddDone = {false};

        try {
            // 4. rank 추정 — DB 저장용 (ZADD 전이라 ZCARD+1 사용, 응답은 ZRANK 기준)
            Long zsetSize = redisTemplate.opsForZSet().zCard(zsetKey);
            int estimatedRank = (zsetSize != null) ? zsetSize.intValue() + 1 : 1;

            // 5. DB INSERT (재활성화 or 신규)
            Waitlist waitlist = waitlistRepository
                    .findByStudent_IdAndCourse_IdAndDeletedAtIsNotNull(studentId, courseId)
                    .map(existing -> {
                        existing.reactivate(estimatedRank);
                        return waitlistRepository.save(existing);
                    })
                    .orElseGet(() -> waitlistRepository.save(
                            Waitlist.builder()
                                    .student(studentRepository.getReferenceById(studentId))
                                    .course(courseRepository.getReferenceById(courseId))
                                    .rank(estimatedRank)
                                    .build()
                    ));

            Long waitlistId = waitlist.getId();

            // 6. ZSET 등록 — score=등록시각(ms), member=waitlistId
            //    score 오름차순 = 먼저 등록한 순서 = rank 순서
            redisTemplate.opsForZSet().add(zsetKey, String.valueOf(waitlistId), System.currentTimeMillis());
            zaddDone[0] = true;

            // 7. 롤백 정리 등록 (ZADD 직후 — 이후 예외 시 ZSET + 슬롯 원복)
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        redisTemplate.opsForZSet().remove(zsetKey, String.valueOf(waitlistId));
                        redisTemplate.opsForValue().increment(slotKey);
                    }
                }
            });

            // 8. 실제 rank — ZRANK (동시 등록이 반영된 정확한 순번)
            Long rankLong = redisTemplate.opsForZSet().rank(zsetKey, String.valueOf(waitlistId));
            int rank = (rankLong != null) ? rankLong.intValue() + 1 : estimatedRank;

            return new WaitlistResponseDto(waitlistId, courseId, courseName, rank, WaitlistStatus.WAITING);

        } catch (DataIntegrityViolationException e) {
            // ZADD 전 실패 → catch에서 슬롯 복구 (afterRollback 미등록 상태)
            if (!zaddDone[0]) redisTemplate.opsForValue().increment(slotKey);
            throw new BusinessException(ErrorCode.DUPLICATE_WAITLIST);
        } catch (Exception e) {
            // ZADD 전 실패 → catch에서 슬롯 복구 / ZADD 후 실패 → afterRollback이 처리
            if (!zaddDone[0]) redisTemplate.opsForValue().increment(slotKey);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // 대기 수락
    @Transactional
    public void accept(Long accountId, Long targetWaitlistId) {
        Student student = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        Waitlist waitlist = waitlistRepository.findById(targetWaitlistId)
                .filter(w -> w.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITLIST_NOT_FOUND));

        // 본인 확인
        if (!waitlist.getStudent().getId().equals(student.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 상태 확인 (NOTIFIED만 수락 가능)
        if (waitlist.getWaitlistStatus() != WaitlistStatus.NOTIFIED) {
            throw new BusinessException(ErrorCode.WAITLIST_INVALID_STATUS);
        }

        // 만료 시간 확인 (10분 제한 시간이 지났으면 만료 처리 후 다음 순번에게  기회를 넘김)
        if (waitlist.getExpiredAt() != null && LocalDateTime.now().isAfter(waitlist.getExpiredAt())) {
            expireAndPromoteNext(waitlist);
            throw new BusinessException(ErrorCode.WAITLIST_EXPIRED);
        }

        Long studentId = student.getId();
        Long courseId = waitlist.getCourse().getId();
        Long waitlistId = waitlist.getId();

        try {
            enrollmentService.enrollFromWaitlist(studentId, courseId);
            waitlist.expire();
            // 수락 완료 — DB 커밋 후 ZSET에서 제거 (DB와 ZSET 동시 정합)
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    redisTemplate.opsForZSet().remove(ZSET_PREFIX + courseId, String.valueOf(waitlistId));
                }
            });
        } catch (BusinessException e) {
            // 수락 실패 → ZSET 정리는 expireAndPromoteNext 내 afterCommit이 처리
            expireAndPromoteNext(waitlist);
            throw e;
        }
    }

    // 대기 거절
    @Transactional
    public void reject(Long accountId, Long targetWaitlistId) {
        Student student = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        Waitlist waitlist = waitlistRepository.findById(targetWaitlistId)
                .filter(w -> w.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITLIST_NOT_FOUND));

        if (!waitlist.getStudent().getId().equals(student.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (waitlist.getWaitlistStatus() != WaitlistStatus.NOTIFIED) {
            throw new BusinessException(ErrorCode.WAITLIST_INVALID_STATUS);
        }

        expireAndPromoteNext(waitlist);
    }

    // 만료 처리 후 다음 순번 알림 (accept 실패, reject, scheduler 공통)
    @Transactional
    public void expireAndPromoteNext(Waitlist waitlist) {
        Waitlist managed = waitlistRepository.findById(waitlist.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITLIST_NOT_FOUND));
        managed.expire();

        Long courseId = managed.getCourse().getId();
        String zsetKey = ZSET_PREFIX + courseId;
        String expiredIdStr = String.valueOf(managed.getId());

        // 다음 대기자: ZSET 상위 3개 중 만료 대상을 제외하고 유효한 WAITING을 순서대로 탐색
        // stale member(soft-delete 등)가 앞에 있어도 건너뛰고 실제 유효 대기자를 찾기 위해 루프 사용
        Set<String> topEntries = redisTemplate.opsForZSet().range(zsetKey, 0, 2);
        Optional<Waitlist> nextOpt = Optional.empty();
        if (topEntries != null) {
            for (String id : topEntries) {
                if (id.equals(expiredIdStr)) continue;
                Optional<Waitlist> candidate = waitlistRepository.findById(Long.valueOf(id))
                        .filter(w -> w.getDeletedAt() == null && w.getWaitlistStatus() == WaitlistStatus.WAITING);
                if (candidate.isPresent()) {
                    nextOpt = candidate;
                    break;
                }
            }
        }

        if (nextOpt.isPresent()) {
            Waitlist next = nextOpt.get();
            next.notified();
            Notification notification = notificationRepository.save(
                    Notification.builder()
                            .student(next.getStudent())
                            .course(next.getCourse())
                            .notificationType(NotificationType.WAITLIST_AVAILABLE)
                            .message("수강 신청 자리가 생겼습니다. 10분 내에 수락해 주세요.")
                            .build()
            );

            Long nextStudentId = next.getStudent().getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 만료된 대기자 ZSET 제거 (DB 커밋 확정 후)
                    redisTemplate.opsForZSet().remove(zsetKey, expiredIdStr);
                    redisTemplate.opsForValue().increment("waitlist:course:" + courseId);
                    // 새 대기자의 10분 수락 창 보장을 위해 lock TTL 재설정
                    redisTemplate.opsForValue().set("lock:course:" + courseId, "1", 15, TimeUnit.MINUTES);
                    sseEmitterService.send(nextStudentId, notification);
                }
            });
        } else {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    redisTemplate.opsForZSet().remove(zsetKey, expiredIdStr);
                    redisTemplate.opsForValue().increment("waitlist:course:" + courseId);
                    redisTemplate.delete("lock:course:" + courseId);
                }
            });
        }
    }

    // 대기자 취소
    @Transactional
    public void cancel(Long accountId, Long targetWaitlistId) {
        // 1. 학생 조회
        Student student = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        // 2. 대기 건 조회 (비관적 락 — 중복 취소 요청 차단)
        Waitlist waitlist = waitlistRepository.findByIdForUpdate(targetWaitlistId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITLIST_NOT_FOUND));

        // 3. 본인 대기 건인지 확인
        if (!waitlist.getStudent().getId().equals(student.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 4. 취소 가능한 상태인지 확인 (WAITING, NOTIFIED만 가능)
        WaitlistStatus status = waitlist.getWaitlistStatus();
        if (status != WaitlistStatus.WAITING && status != WaitlistStatus.NOTIFIED) {
            throw new BusinessException(ErrorCode.WAITLIST_INVALID_STATUS);
        }

        // 5. RDS soft delete
        Long courseId = waitlist.getCourse().getId();
        Long waitlistId = waitlist.getId();
        waitlist.delete();

        // 6. DB 커밋 후 ZSET 제거 + 슬롯 반환 (순서 보장: DB 확정 먼저)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redisTemplate.opsForZSet().remove(ZSET_PREFIX + courseId, String.valueOf(waitlistId));
                redisTemplate.opsForValue().increment("waitlist:course:" + courseId);
            }
        });
    }
}
