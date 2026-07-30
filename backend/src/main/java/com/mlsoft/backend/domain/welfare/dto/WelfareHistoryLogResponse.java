package com.mlsoft.backend.domain.welfare.dto;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.welfare.entity.WelfareActionHistory;
import com.mlsoft.backend.domain.welfare.entity.WelfareRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 복리후생 처리 로그 응답 (GET /api/welfare-histories — docs/03 처리 이력).
 *
 * <p>카테고리·부여 일수는 신청 시점 스냅샷(메모 7)이라 정책이 나중에 바뀌어도 로그는 당시 값을 유지한다.
 * LAZY 연관을 접근하므로 트랜잭션 내에서 변환한다.
 */
public record WelfareHistoryLogResponse(
        Long id,
        Long requestId,
        String action,
        String actorName,
        String userName,
        String departmentName,
        String category,
        BigDecimal addDays,
        String comment,
        LocalDateTime createdAt
) {

    public static WelfareHistoryLogResponse of(WelfareActionHistory history) {
        User user = history.getUser();
        Department department = user.getDepartment();
        WelfareRequest request = history.getWelfareRequest();
        return new WelfareHistoryLogResponse(
                history.getId(),
                request.getId(),
                history.getAction().name(),
                history.getActor().getName(),
                user.getName(),
                department != null ? department.getName() : null,
                request.getCategory(),
                request.getAddDays(),
                history.getComment(),
                history.getCreatedAt()
        );
    }
}
