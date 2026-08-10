package com.mlsoft.backend.domain.welfare.dto;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.welfare.entity.WelfareRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 복리후생 신청 상세/목록 응답 (docs/03).
 *
 * <p>승인자는 <b>id만</b> 노출한다. 예전에는 FK가 없어서 이름을 담을 수 없었지만(리뷰 D-5로 해소),
 * 지금도 id만 두는 이유는 다르다 — 이름을 담으면 목록 조회가 승인자까지 적재해야 하고
 * ({@code @EntityGraph} 확장) 화면이 그 이름을 아직 쓰지 않는다. 필요해지면 그때 함께 넣는다.
 *
 * <p>LAZY 연관(신청자·부서·정책)을 접근하므로 트랜잭션 내에서 변환한다.
 * 승인자 연관은 <b>{@code getId()}만 읽으므로 쿼리가 나가지 않는다</b> — FK 값이 프록시에 이미 있다.
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
                welfare.getPrimaryApprover().getId(),
                welfare.getSubApprover() != null ? welfare.getSubApprover().getId() : null,
                welfare.getCreatedAt()
        );
    }
}
