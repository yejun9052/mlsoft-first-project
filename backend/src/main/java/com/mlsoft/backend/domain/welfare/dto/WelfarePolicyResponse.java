package com.mlsoft.backend.domain.welfare.dto;

import com.mlsoft.backend.domain.welfare.entity.WelfarePolicy;
import com.mlsoft.backend.domain.welfare.entity.WelfareTarget;

import java.math.BigDecimal;

/**
 * 복리후생 정책 응답 (docs/03).
 */
public record WelfarePolicyResponse(
        Long id,
        String category,
        WelfareTarget target,
        BigDecimal defaultDays,
        String defaultEvidence,
        String description,
        boolean active
) {

    public static WelfarePolicyResponse of(WelfarePolicy policy) {
        return new WelfarePolicyResponse(
                policy.getId(),
                policy.getCategory(),
                policy.getTarget(),
                policy.getDefaultDays(),
                policy.getDefaultEvidence(),
                policy.getDescription(),
                policy.isActive()
        );
    }
}
