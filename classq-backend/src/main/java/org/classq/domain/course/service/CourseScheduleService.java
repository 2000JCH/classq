package org.classq.domain.course.service;

import lombok.RequiredArgsConstructor;
import org.classq.domain.course.dto.CourseScheduleDto;
import org.classq.domain.course.repository.CourseRepository;
import org.classq.domain.course.repository.CourseScheduleRepository;
import org.classq.global.exception.BusinessException;
import org.classq.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseScheduleService {

    private final CourseRepository courseRepository;
    private final CourseScheduleRepository courseScheduleRepository;

    @Transactional(readOnly = true)
    public List<CourseScheduleDto> getCourseSchedules(Long courseId) {

        // 강의가 존재하는지 확인
        courseRepository.findById(courseId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));


        return courseScheduleRepository.findByCourseId(courseId).stream()
                .map(s -> new CourseScheduleDto(s.getId(), s.getCourseScheduleDay(), s.getStartTime(), s.getEndTime()))
                .toList();
    }
}