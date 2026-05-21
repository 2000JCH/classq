package org.classq.domain.waitlist.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.course.entity.Course;
import org.classq.domain.course.repository.CourseRepository;
import org.classq.domain.enrollment.entity.EnrollmentStatus;
import org.classq.domain.enrollment.repository.EnrollmentRepository;
import org.springframework.transaction.annotation.Transactional;
import org.classq.domain.student.entity.Student;
import org.classq.domain.student.repository.StudentRepository;
import org.classq.domain.waitlist.dto.WaitlistResponseDto;
import org.classq.domain.waitlist.entity.Waitlist;
import org.classq.domain.waitlist.entity.WaitlistStatus;
import org.classq.domain.waitlist.repository.WaitlistRepository;
import org.classq.global.exception.BusinessException;
import org.classq.global.exception.ErrorCode;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final WaitlistRepository waitlistRepository;
    private final RedisTemplate<String, String> redisTemplate;

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

        // 4. rank 계산 (현재 활성 대기자 수 + 1)
        int rank = waitlistRepository.countByCourse_IdAndWaitlistStatusInAndDeletedAtIsNull(
                courseId, List.of(WaitlistStatus.WAITING, WaitlistStatus.NOTIFIED)) + 1;

        // 5. RDS INSERT (실패 시 Redis 슬롯 복구)
        try {
            Waitlist waitlist = waitlistRepository.save(
                    Waitlist.builder()
                            .student(student)
                            .course(course)
                            .rank(rank)
                            .build()
            );
            return new WaitlistResponseDto(waitlist.getId(), courseId, course.getName(), rank, WaitlistStatus.WAITING);
        } catch (Exception e) {
            redisTemplate.opsForValue().increment("waitlist:course:" + courseId);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // 대기자 취소
    @Transactional
    public void cancel(Long accountId, Long waitlistId) {
        // 1. 학생 조회
        Student student = studentRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        // 2. 대기 건 조회
        Waitlist waitlist = waitlistRepository.findById(waitlistId)
                .filter(w -> w.getDeletedAt() == null)
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

        // 5. Redis 대기 슬롯 반환
        redisTemplate.opsForValue().increment("waitlist:course:" + waitlist.getCourse().getId());

        waitlist.delete();
    }
}
