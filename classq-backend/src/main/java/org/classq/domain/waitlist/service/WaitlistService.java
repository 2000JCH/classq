package org.classq.domain.waitlist.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.course.repository.CourseRepository;
import org.classq.domain.enrollment.repository.EnrollmentRepository;
import org.classq.domain.enrollment.service.EnrollmentService;
import org.classq.domain.waitlist.producer.dto.WaitlistPromoteEvent;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final WaitlistRepository waitlistRepository;
    private final EnrollmentService enrollmentService;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // 내 대기 목록 조회
    @Transactional(readOnly = true)
    public WaitlistListResponseDto getMyWaitlists(Long accountId) {
        Student student = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        Long studentId = student.getId();

        List<WaitlistResponseDto> waitlists = waitlistRepository
                .findByStudent_IdAndWaitlistStatusInAndDeletedAtIsNull(
                        studentId, List.of(WaitlistStatus.WAITING, WaitlistStatus.NOTIFIED))
                .stream()
                .map(w -> new WaitlistResponseDto(
                        w.getId(), w.getCourse().getId(), w.getCourse().getName(),
                        w.getRank(), w.getWaitlistStatus()))
                .toList();

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
        String studentIdStr = redisTemplate.opsForValue().get("student:account:" + accountId);
        if (studentIdStr == null) throw new BusinessException(ErrorCode.STUDENT_NOT_FOUND);
        Long studentId = Long.valueOf(studentIdStr);

        String courseName = redisTemplate.opsForValue().get("course:" + courseId + ":name");
        if (courseName == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);

        String slotKey = "waitlist:course:" + courseId;
        Long remaining = redisTemplate.opsForValue().decrement(slotKey);
        if (remaining == null || remaining < 0) {
            redisTemplate.opsForValue().increment(slotKey);
            throw new BusinessException(ErrorCode.WAITLIST_CLOSED);
        }

        // DB 롤백 시 슬롯 원복
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    redisTemplate.opsForValue().increment(slotKey);
                }
            }
        });

        try {
            int rank = waitlistRepository.countByCourse_IdAndWaitlistStatusInAndDeletedAtIsNull(
                    courseId, List.of(WaitlistStatus.WAITING, WaitlistStatus.NOTIFIED)) + 1;

            Waitlist waitlist = waitlistRepository
                    .findByStudent_IdAndCourse_IdAndDeletedAtIsNotNull(studentId, courseId)
                    .map(existing -> {
                        existing.reactivate(rank);
                        return waitlistRepository.save(existing);
                    })
                    .orElseGet(() -> waitlistRepository.save(
                            Waitlist.builder()
                                    .student(studentRepository.getReferenceById(studentId))
                                    .course(courseRepository.getReferenceById(courseId))
                                    .rank(rank)
                                    .build()
                    ));

            return new WaitlistResponseDto(waitlist.getId(), courseId, courseName, rank, WaitlistStatus.WAITING);

        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_WAITLIST);
        } catch (Exception e) {
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

        if (!waitlist.getStudent().getId().equals(student.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (waitlist.getWaitlistStatus() != WaitlistStatus.NOTIFIED) {
            throw new BusinessException(ErrorCode.WAITLIST_INVALID_STATUS);
        }

        if (waitlist.getExpiredAt() != null && LocalDateTime.now().isAfter(waitlist.getExpiredAt())) {
            expireAndPromoteNext(waitlist);
            throw new BusinessException(ErrorCode.WAITLIST_EXPIRED);
        }

        Long studentId = student.getId();
        Long courseId = waitlist.getCourse().getId();

        try {
            enrollmentService.enrollFromWaitlist(studentId, courseId);
            waitlist.expire();
        } catch (BusinessException e) {
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

    // 만료 처리 후 Kafka로 다음 순번 위임 (reject, accept 실패, scheduler 공통)
    @Transactional
    public void expireAndPromoteNext(Waitlist waitlist) {
        Waitlist managed = waitlistRepository.findById(waitlist.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITLIST_NOT_FOUND));
        managed.expire();

        Long courseId = managed.getCourse().getId();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redisTemplate.opsForValue().increment("waitlist:course:" + courseId);
                kafkaTemplate.send("waitlist-promote-events", String.valueOf(courseId),
                        new WaitlistPromoteEvent(courseId));
            }
        });
    }

    // 대기자 취소
    @Transactional
    public void cancel(Long accountId, Long targetWaitlistId) {
        Student student = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        Waitlist waitlist = waitlistRepository.findByIdForUpdate(targetWaitlistId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITLIST_NOT_FOUND));

        if (!waitlist.getStudent().getId().equals(student.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        WaitlistStatus status = waitlist.getWaitlistStatus();
        if (status != WaitlistStatus.WAITING && status != WaitlistStatus.NOTIFIED) {
            throw new BusinessException(ErrorCode.WAITLIST_INVALID_STATUS);
        }

        Long courseId = waitlist.getCourse().getId();
        waitlist.delete();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redisTemplate.opsForValue().increment("waitlist:course:" + courseId);
            }
        });
    }
}
