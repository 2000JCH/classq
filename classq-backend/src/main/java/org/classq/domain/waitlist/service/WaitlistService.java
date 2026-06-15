package org.classq.domain.waitlist.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.course.entity.Course;
import org.classq.domain.course.repository.CourseRepository;
import org.classq.domain.enrollment.entity.EnrollmentStatus;
import org.classq.domain.enrollment.repository.EnrollmentRepository;
import org.classq.domain.enrollment.service.EnrollmentService;
import org.classq.domain.notification.entity.Notification;
import org.classq.domain.notification.entity.NotificationType;
import org.classq.domain.notification.repository.NotificationRepository;
import org.classq.domain.notification.service.SseEmitterService;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final WaitlistRepository waitlistRepository;
    private final NotificationRepository notificationRepository;
    private final SseEmitterService sseEmitterService;
    private final EnrollmentService enrollmentService;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedissonClient redissonClient;

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
                .map(w -> new WaitlistResponseDto(
                        w.getId(), w.getCourse().getId(), w.getCourse().getName(),
                        w.getRank(), w.getWaitlistStatus()))
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
    public WaitlistResponseDto register(Long accountId, Long courseId) {
        Student student = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        Course course = courseRepository.findById(courseId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        Long studentId = student.getId();

        // 1. 이미 수강신청 완료 여부 확인
        if (enrollmentRepository.existsByStudent_IdAndCourse_IdAndEnrollmentStatusAndDeletedAtIsNull(
                studentId, courseId, EnrollmentStatus.COMPLETED)) {
            throw new BusinessException(ErrorCode.DUPLICATE_ENROLLMENT);
        }

        // 2. 이미 대기 신청 여부 확인
        if (waitlistRepository.existsByCourse_IdAndStudent_IdAndWaitlistStatusInAndDeletedAtIsNull(
                courseId, studentId, List.of(WaitlistStatus.WAITING, WaitlistStatus.NOTIFIED))) {
            throw new BusinessException(ErrorCode.DUPLICATE_WAITLIST);
        }

        // 3. 대기 자리 차감 (음수면 롤백 후 거절)
        Long remaining = redisTemplate.opsForValue().decrement("waitlist:course:" + courseId);  // -1 하고 벨류값 반환
        if (remaining == null || remaining < 0) {
            redisTemplate.opsForValue().increment("waitlist:course:" + courseId);   // 대기자리에 +1 해줌 다시 0으로 만들기 위해
            throw new BusinessException(ErrorCode.WAITLIST_CLOSED);
        }

        // 4. 분산 락으로 rank 계산 + INSERT 직렬화 (rank 중복 방지)
        RLock lock = redissonClient.getLock("waitlist-rank:course:" + courseId);
        try {
            if (!lock.tryLock(5, 3, TimeUnit.SECONDS)) {
                redisTemplate.opsForValue().increment("waitlist:course:" + courseId);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            try {
                int rank = waitlistRepository.countByCourse_IdAndWaitlistStatusInAndDeletedAtIsNull(
                        courseId, List.of(WaitlistStatus.WAITING, WaitlistStatus.NOTIFIED)) + 1;

                // 취소 후 재등록 시 soft-delete 이력 재활성화 (unique constraint 충돌 방지)
                Waitlist waitlist = waitlistRepository
                        .findByStudent_IdAndCourse_IdAndDeletedAtIsNotNull(studentId, courseId)
                        .map(existing -> { existing.reactivate(rank); return existing; })
                        .orElseGet(() -> waitlistRepository.save(
                                Waitlist.builder()
                                        .student(student)
                                        .course(course)
                                        .rank(rank)
                                        .build()
                        ));
                return new WaitlistResponseDto(waitlist.getId(), courseId, course.getName(), rank, WaitlistStatus.WAITING);
            } catch (BusinessException e) {
                redisTemplate.opsForValue().increment("waitlist:course:" + courseId);
                throw e;
            } catch (Exception e) {
                redisTemplate.opsForValue().increment("waitlist:course:" + courseId);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            redisTemplate.opsForValue().increment("waitlist:course:" + courseId);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // 대기 수락
    @Transactional
    public void accept(Long accountId, Long waitlistId) {
        Student student = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        Waitlist waitlist = waitlistRepository.findById(waitlistId)
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

        try {
            enrollmentService.enrollFromWaitlist(studentId, courseId);
            waitlist.expire();
            waitlistRepository.decrementRanksAfter(courseId, waitlist.getRank());
        } catch (BusinessException e) {
            expireAndPromoteNext(waitlist);
            throw e;
        }
    }

    // 대기 거절
    @Transactional
    public void reject(Long accountId, Long waitlistId) {
        Student student = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        Waitlist waitlist = waitlistRepository.findById(waitlistId)
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
        waitlistRepository.decrementRanksAfter(courseId, managed.getRank());
        redisTemplate.opsForValue().increment("waitlist:course:" + courseId);
        Optional<Waitlist> nextOpt = waitlistRepository
                .findFirstByCourse_IdAndWaitlistStatusAndDeletedAtIsNullOrderByRankAsc(courseId, WaitlistStatus.WAITING);

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

            Long studentId = next.getStudent().getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sseEmitterService.send(studentId, notification);
                }
            });
        } else {
            redisTemplate.delete("lock:course:" + courseId);
        }
    }

    // 대기자 취소
    @Transactional
    public void cancel(Long accountId, Long waitlistId) {
        // 1. 학생 조회
        Student student = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        // 2. 대기 건 조회 (비관적 락 — 중복 취소 요청 차단)
        Waitlist waitlist = waitlistRepository.findByIdForUpdate(waitlistId)
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

        // 5. RDS soft delete + rank 갱신
        int cancelledRank = waitlist.getRank();
        Long courseId = waitlist.getCourse().getId();
        waitlist.delete();
        waitlistRepository.decrementRanksAfter(courseId, cancelledRank);

        // 6. Redis 대기 슬롯 반환 (DB 변경 후 수행 — 실패 시 트랜잭션 롤백으로 정합성 유지)
        redisTemplate.opsForValue().increment("waitlist:course:" + courseId);
    }
}
