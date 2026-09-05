package com.mlsoft.backend.domain.holiday.credential;

import com.mlsoft.backend.domain.credential.SecretCipher;

/**
 * 공휴일 자격 증명 암호화기의 이전 이름과 호환하기 위한 얇은 어댑터.
 * 실제 Spring 빈과 암호화 구현은 {@link SecretCipher} 하나만 사용한다.
 */
@Deprecated
public class HolidaySecretCipher extends SecretCipher {

    public HolidaySecretCipher(String encodedKey) {
        super(encodedKey);
    }
}
