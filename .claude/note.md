@NoArgsConstructor
빈 생성자를 만들어준다.
사용 이유: JPA는 데이터를 조회할때 빈 생성자를 먼저 만들고 필드값을 조회하기 때문에 필요하다.
@RequestBody로 받는 요청은 DTO에 @NoArgsConstructor가 필요하다.

@AllArgsConstructor
각각의 필드에 대한 public 생성자를 만들어준다.
!: private 에서는 사용할 수 없다. public에서만 사용가능

@Configuration
이 클래스가 Spring 설정 클래승미을 알려주는 어노테이션
Spring이 앱을 시작할 때 @Configuration이 붙은 클래스를 찾아서 설정으로 등록
없으면 Spring이 이 클래스를 그냥 일반 클래스로 취급해서 안에 있는 설정들이 동작하지 않음.

@RestControllerAdvice
애플리케이션의 모든 컨트롤러에서 발생하는 예외를 한 곳에서 처리해준다. (GlobalExceptionHandler에 붙인다.)

@RequiredArgsConstructor
final 필드 또는 @NonNull 필드에 대해서 public 생성자를 자동으로 만들어준다.
global/exception/ErrorCode 에서 사용
!: private 에서는 사용할 수 없다. public에서만 사용가능

@EnableWebSecurity
SecurityConfig.java 사용 중
이제 부터 내가 만든 보안 설정을 사용하겠다 라고 스프링에 선언하는 스위치

@Slf4j
Lombok 어노테이션
로그를 남기기위해 사용하는 어노테이션 -> 정확히는 로거 객체(log)를 자동 생성해주는 어노테이션

│ log.debug() │ 개발 중 상세 디버깅 정보
├─────────────┼──────────────────────────────
│ log.info()  │ 정상 흐름 기록 (로그인 성공 등) 
├─────────────┼──────────────────────────────
│ log.warn()  │ 잠재적 문제 (입력값 오류 등) 
├─────────────┼──────────────────────────────
│ log.error() │ 예상 못 한 심각한 오류    

@Transactional
해당 메서드를 하나의 트랜잭션으로 묶어준다.
트랜잭션은 "DB 작업을 하나의 단위로 처리"하는 개념
- 메서드 시작 → 트랜잭션 시작 (DB 연결 유지)
- 메서드 정상 종료 → commit
- 예외 발생 → rollback

사용 이유
1. 수정/삭제 -> dirty checking — 엔티티 필드 변경 시 트랜잭션 종료 시점에 JPA가 자동으로 UPDATE 쿼리 실행
2. Lazy 로딩 — 해당 엔티티에서 조인으로 다른 테이블의 값을 참조하고 있다면 @Transactional 써줘야함
   Lazy는 실제 호출 시점에 DB 조회 → 트랜잭션 없으면 LazyInitializationException 발생
   Eager + 조회 -> @Transactional 없어도 동작함

@Transactional(readOnly = true)
readOnly = true
- 읽기 전용 선언 → 쓰기 작업 없는 조회 메서드에 사용
- Hibernate가 dirty checking을 건너뛰어 성능 최적화
- 엔티티 Lazy를 사용했다면 조회도 트랜잭션 필요

구분
- 조회만 → @Transactional(readOnly = true)
- 수정/삭제 → @Transactional

Specification - JPA가 제공하는 동적 쿼리 방식
필터 조건이 있을 수도 없을 수도 있을때 사용.
JPQL로 하면 조건마다 if문으로 쿼리 문자열을 이어붙여야 해서 지버분해짐 (가독성이 떨어짐)

.stream()
리스트를 스트림으로 변환 각 요소를 하나씩 처리할 수 있게 해준다.
->  List<CourseSchedule> 반환. 예시로 3개라고 가정

.toList() 
변환된 결과를 다시 List<CourseScheduleDto>로 모아서 반환
.stream()이 변환한걸 다시 리스트로 만들어준다.
