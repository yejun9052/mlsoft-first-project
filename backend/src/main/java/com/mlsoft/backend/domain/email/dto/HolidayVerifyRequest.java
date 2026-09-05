package com.mlsoft.backend.domain.email.dto;

/** 공휴일 API 키 검증 요청. 키를 생략하면 저장된 키를 사용한다. */
public record HolidayVerifyRequest(String apiKey) {
}
