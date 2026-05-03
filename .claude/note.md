@NoArgsConstructor
빈 생성자를 만들어준다.
사용 이유: JPA는 데이터를 조회할때 빈 생성자를 먼저 만들고 필드값을 조회하기 때문에 필요하다.

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