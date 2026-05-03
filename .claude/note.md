@NoArgsConstructor
빈 생성자를 만들어준다.
사용 이유: JPA는 데이터를 조회할때 빈 생성자를 먼저 만들고 필드값을 조회하기 때문에 필요하다.

@AllArgsConstructor
각각의 필드에 대한 생성자를 만들어준다.
