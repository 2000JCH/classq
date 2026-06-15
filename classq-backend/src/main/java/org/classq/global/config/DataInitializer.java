package org.classq.global.config;

import lombok.RequiredArgsConstructor;
import org.classq.domain.account.entity.Account;
import org.classq.domain.account.entity.AccountStatus;
import org.classq.domain.account.entity.Role;
import org.classq.domain.account.repository.AccountRepository;
import org.classq.domain.course.entity.Course;
import org.classq.domain.course.entity.CourseSchedule;
import org.classq.domain.course.entity.enums.ClassMode;
import org.classq.domain.course.entity.enums.ClassType;
import org.classq.domain.course.entity.enums.CourseScheduleDay;
import org.classq.domain.course.entity.enums.CourseType;
import org.classq.domain.course.repository.CourseRepository;
import org.classq.domain.course.repository.CourseScheduleRepository;
import org.classq.domain.department.entity.Department;
import org.classq.domain.department.repository.DepartmentRepository;
import org.classq.domain.professor.entity.Professor;
import org.classq.domain.professor.repository.ProfessorRepository;
import org.classq.domain.student.entity.Student;
import org.classq.domain.student.repository.StudentRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final DepartmentRepository departmentRepository;
    private final AccountRepository accountRepository;
    private final StudentRepository studentRepository;
    private final ProfessorRepository professorRepository;
    private final CourseRepository courseRepository;
    private final CourseScheduleRepository courseScheduleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initDepartments();
        initAdmin();
        initTestStudents();
        initTestProfessor();
        initTestCourses();
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

    private void initTestStudents() {
        if (accountRepository.existsByEmail("qqq123@naver.com")) return;

        Department csDept = departmentRepository.findAll().stream()
                .filter(d -> d.getName().equals("컴퓨터공학과"))
                .findFirst().orElseThrow();

        String[][] students = {
                {"김철수", "qqq123@naver.com", "qqq12345!"},
                {"김유리", "zzz123@naver.com", "zzz12345!"},
                {"신형만", "www123@naver.com", "www12345!"},
                {"신짱구", "sss123@naver.com", "sss12345!"},
                {"김훈이", "ddd123@naver.com", "ddd12345!"},
                {"유맹구", "xxx123@naver.com", "xxx12345!"},
        };

        for (String[] s : students) {
            Account account = accountRepository.save(Account.builder()
                    .email(s[1])
                    .password(passwordEncoder.encode(s[2]))
                    .role(Role.STUDENT)
                    .status(AccountStatus.ACTIVE)
                    .build());

            studentRepository.save(Student.builder()
                    .account(account)
                    .department(csDept)
                    .name(s[0])
                    .grade(1)
                    .build());
        }
    }

    private void initTestProfessor() {
        Account account = accountRepository.findByEmailAndDeletedAtIsNull("aaa123@naver.com")
                .orElse(null);

        if (account != null &&
                professorRepository.findByAccountIdAndDeletedAtIsNull(account.getId()).isPresent()) {
            return;
        }

        Department csDept = departmentRepository.findAll().stream()
                .filter(d -> d.getName().equals("컴퓨터공학과"))
                .findFirst().orElseThrow();

        if (account == null) {
            account = accountRepository.save(Account.builder()
                    .email("aaa123@naver.com")
                    .password(passwordEncoder.encode("aaa12345!"))
                    .role(Role.PROFESSOR)
                    .status(AccountStatus.ACTIVE)
                    .build());
        }

        professorRepository.save(Professor.builder()
                .account(account)
                .department(csDept)
                .name("김근태")
                .build());
    }

    private void initTestCourses() {
        if (courseRepository.count() > 0) {
            initCourseRedisKeysIfMissing();
            return;
        }

        Department csDept = departmentRepository.findAll().stream()
                .filter(d -> d.getName().equals("컴퓨터공학과"))
                .findFirst().orElseThrow();

        Account profAccount = accountRepository.findByEmailAndDeletedAtIsNull("aaa123@naver.com")
                .orElseThrow();
        Professor professor = professorRepository.findByAccountIdAndDeletedAtIsNull(profAccount.getId())
                .orElseThrow();

        saveCourse(professor, csDept,  "데이터베이스", CourseType.MAJOR_REQUIRED, ClassType.THEORY,  ClassMode.OFFLINE, 3, 3,  2,  1, 4, CourseScheduleDay.MON, LocalTime.of(13, 54), LocalTime.of(14, 54));
        saveCourse(professor, csDept,  "자료구조",    CourseType.MAJOR_REQUIRED, ClassType.THEORY,  ClassMode.OFFLINE, 3, 2,  2,  1, 4, CourseScheduleDay.MON, LocalTime.of(13, 54), LocalTime.of(15, 54));
        saveCourse(professor, csDept,  "UI/UX",       CourseType.MAJOR_ELECTIVE, ClassType.THEORY,  ClassMode.OFFLINE, 3, 30, 30, 2, 4, CourseScheduleDay.MON, LocalTime.of(17, 55), LocalTime.of(18, 55));
        saveCourse(professor, null,    "리더쉽",      CourseType.LIBERAL_ARTS,   ClassType.THEORY,  ClassMode.OFFLINE, 3, 30, 30, 1, 4, CourseScheduleDay.MON, LocalTime.of(17, 56), LocalTime.of(19, 56));
        saveCourse(professor, null,    "음악의이해",  CourseType.LIBERAL_ARTS,   ClassType.THEORY,  ClassMode.OFFLINE, 3, 30, 30, 1, 4, CourseScheduleDay.TUE, LocalTime.of(16, 57), LocalTime.of(19, 57));
        saveCourse(professor, null,    "교양이란",    CourseType.LIBERAL_ARTS,   ClassType.THEORY,  ClassMode.ONLINE,  3, 30, 30, 1, 4, CourseScheduleDay.TUE, LocalTime.of(17, 59), LocalTime.of(18, 59));
        saveCourse(professor, null,    "경찰과도둑",  CourseType.LIBERAL_ARTS,   ClassType.PRACTICE,ClassMode.OFFLINE, 3, 30, 30, 1, 4, CourseScheduleDay.WED, LocalTime.of(13, 58), LocalTime.of(14, 58));
    }

    private void initCourseRedisKeysIfMissing() {
        courseRepository.findAll().forEach(course -> {
            String enrollmentKey = "enrollment:course:" + course.getId();
            String waitlistKey = "waitlist:course:" + course.getId();
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(enrollmentKey))) {
                redisTemplate.opsForValue().set(enrollmentKey, String.valueOf(course.getCapacity()));
            }
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(waitlistKey))) {
                redisTemplate.opsForValue().set(waitlistKey, String.valueOf(course.getWaitlistLimit()));
            }
        });
    }

    private void saveCourse(Professor professor, Department department, String name,
                            CourseType courseType, ClassType classType, ClassMode classMode,
                            int credits, int capacity, int waitlistLimit,
                            int minGrade, int maxGrade,
                            CourseScheduleDay day, LocalTime startTime, LocalTime endTime) {
        Course course = courseRepository.save(Course.builder()
                .professor(professor)
                .department(department)
                .name(name)
                .courseType(courseType)
                .classType(classType)
                .classMode(classMode)
                .credits(credits)
                .capacity(capacity)
                .waitlistLimit(waitlistLimit)
                .minGrade(minGrade)
                .maxGrade(maxGrade)
                .build());

        courseScheduleRepository.save(CourseSchedule.builder()
                .course(course)
                .courseScheduleDay(day)
                .startTime(startTime)
                .endTime(endTime)
                .build());

        redisTemplate.opsForValue().set("enrollment:course:" + course.getId(), String.valueOf(capacity));
        redisTemplate.opsForValue().set("waitlist:course:" + course.getId(), String.valueOf(waitlistLimit));
    }
}