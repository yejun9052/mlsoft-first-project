package com.mlsoft.backend.domain.welfare.dto;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.welfare.entity.WelfareRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 복리후생 신청 상세/목록 응답 (docs/03).
 * primary/subApprover는 엔티티 설계상 FK 연관 없이 id만 보관하므로(WelfareRequest 참고) 이름 없이 id만 노출한다.
 * LAZY 연관(신청자·부서·정책)을 접근하므로 트랜잭션 내에서 변환한다.
 */
public record WelfareResponse(
        Long id,
        Long userId,
        String userName,
        Long departmentId,
        String departmentName,
        Long policyId,
        String category,
        String target,
        String evidenceGuide,
        BigDecimal addDays,
        String reason,
        String status,
        Long primaryApproverId,
        Long subApproverId,
        LocalDateTime createdAt
) {

    public static WelfareResponse of(WelfareRequest welfare) {
        User applicant = welfare.getUser();
        Department department = applicant.getDepartment();
        return new WelfareResponse(
                welfare.getId(),
                applicant.getId(),
                applicant.getName(),
                department != null ? department.getId() : null,
                department != null ? department.getName() : null,
                welfare.getPolicy().getId(),
                welfare.getCategory(),
                welfare.getTarget().name(),
                welfare.getEvidenceGuide(),
                welfare.getAddDays(),
                welfare.getReason(),
                welfare.getStatus().name(),
                welfare.getPrimaryApproverId(),
                welfare.getSubApproverId(),
                welfare.getCreatedAt()
        );
    }
}
