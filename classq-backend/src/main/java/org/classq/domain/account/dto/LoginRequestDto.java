package org.classq.domain.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor  //@RequestBody로 받는 요청은 DTO에 @NoArgsConstructor가 필요하다.
@AllArgsConstructor
public class LoginRequestDto {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;
}
