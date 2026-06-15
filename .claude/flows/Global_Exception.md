# 전역 예외처리 (Global Exception Handler) 흐름 정리

## 관련 클래스 구조

| 클래스 | 역할 |
|---|---|
| `ErrorCode` | 에러 코드, 상태코드, 메시지를 enum으로 정의 |
| `BusinessException` | ErrorCode를 담아 운반하는 예외 객체 |
| `ErrorResponse` | 클라이언트에 반환할 JSON 응답 포장지 |
| `GlobalExceptionHandler` | 예외 타입에 맞는 핸들러로 분류 후 처리 |

---

## 전체 흐름

### 1. 서비스에서 예외 던짐

```java
throw new BusinessException(ErrorCode.ENROLLMENT_CLOSED);
```

### 2. BusinessException 생성자 실행

```java
public BusinessException(ErrorCode errorCode) {
    super(errorCode.getMessage()); // RuntimeException에 메시지 전달
    this.errorCode = errorCode;    // ENROLLMENT_CLOSED 저장
}
```

- `super()` 로 부모인 `RuntimeException` 생성자 호출
- `this.errorCode` 에 enum 객체 저장

### 3. 콜스택 타고 전파

```
Service → Controller → DispatcherServlet
```

- 중간에 아무도 `catch` 하지 않으면 Spring이 가로챔

### 4. GlobalExceptionHandler가 예외 타입 분류

```java
@ExceptionHandler(BusinessException.class)  // BusinessException이면 여기
@ExceptionHandler(Exception.class)          // 그 외 예상 못한 예외는 여기
```

- Spring이 `@ExceptionHandler` 목록과 예외 타입을 비교
- **더 구체적인 타입을 우선 선택** (BusinessException > Exception)
- 개발자가 직접 분류할 필요 없이 자동으로 처리

### 5. handleBusinessException() 실행

```java
handleBusinessException(BusinessException e) {
    ErrorCode errorCode = e.getErrorCode(); // ENROLLMENT_CLOSED 꺼냄
    return ResponseEntity
            .status(errorCode.getStatus())       // 409 세팅
            .body(ErrorResponse.of(errorCode));  // ErrorResponse 생성
}
```

### 6. ErrorResponse.of() 실행

```java
public static ErrorResponse of(ErrorCode errorCode) {
    return new ErrorResponse(errorCode.name(), errorCode.getMessage());
}
```

| 메서드 | 반환값 | 타입 |
|---|---|---|
| `errorCode.name()` | `"ENROLLMENT_CLOSED"` | String |
| `errorCode.getMessage()` | `"수강 신청이 마감되었습니다."` | String |
| `errorCode.getStatus()` | `409` | int |

- `name()` 은 enum이 기본 제공하는 메서드
- `getMessage()`, `getStatus()` 는 `@Getter` 가 만들어준 메서드
- 생성자가 `private` 이라 반드시 `of()` 를 통해서만 생성 가능

### 7. 클라이언트에 HTTP 응답 전송

```
HTTP/1.1 409 Conflict
{
    "code": "ENROLLMENT_CLOSED",
    "message": "수강 신청이 마감되었습니다."
}
```

- `status(409)` 는 HTTP 응답 자체에 담김
- JSON 바디에는 `code`, `message` 만 포함 (status 중복 불필요)

### 8. 객체 소멸

- 응답 전송 완료 후 `BusinessException` 객체를 GC가 수거

---

## 핵심 포인트 요약

- 개발자는 `throw new BusinessException(...)` 만 하면 됨, 나머지는 Spring이 자동 처리
- `BusinessException` 은 `ErrorCode` 를 담아 운반하는 그릇
- `GlobalExceptionHandler` 는 꺼내서 응답으로 변환만 하는 역할
- 예외 객체는 메모리에 올라간 채로 전달되다가 응답 후 소멸