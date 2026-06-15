package org.classq.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TokenResponseDto {
    private String accessToken;
    @JsonIgnore
    private String refreshToken;
}