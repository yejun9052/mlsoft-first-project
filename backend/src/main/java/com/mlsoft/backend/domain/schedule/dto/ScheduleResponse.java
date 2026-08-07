package com.mlsoft.backend.domain.schedule.dto;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.schedule.entity.ScheduleEntry;
import com.mlsoft.backend.domain.user.entity.User;

import java.time.LocalDate;
import java.util.List;

/**
 * 개인 일정 응답 (캘린더·내 일정 공용).
 * <p>
 * 메모 마스킹: 연차 사유와 같은 기준으로 <b>본인에게만</b> 채워 보낸다 (docs/01 2-5(b), 검증 Y-4).
 * 캘린더는 전 직원 일정을 보여주므로, 메모에 적은 방문처·개인 사정이 그대로 노출되면 안 된다.
 * 연차와 달리 승인자가 없으므로 열람 권한자는 본인과 SYSTEM_ADMIN뿐이다.
 * <p>
 * {@code typeLabel}을 함께 내리는 이유는 프론트가 종류 이름을 하드코딩하지 않게 하기 위해서다
 * (설정 카탈로그가 메타데이터를 함께 내리는 것과 같은 의도 — 리뷰 I-3).
 */
public record ScheduleResponse(
        Long id,
        Long userId,
        String userName,
        String departmentName,
        String scheduleType,
        String typeLabel,
        List<LocalDate> dates,
        String memo,
        boolean editable
) {

    public static ScheduleResponse of(ScheduleEntry entry, Long viewerId, boolean viewerIsAdmin) {
        User owner = entry.getUser();
        Department department = owner.getDepartment();
        boolean isOwner = entry.isOwnedBy(viewerId);
        return new ScheduleResponse(
                entry.getId(),
                owner.getId(),
                owner.getName(),
                department != null ? department.getName() : null,
                entry.getScheduleType().name(),
                entry.getScheduleType().getLabel(),
                List.copyOf(entry.getDates()),
                (isOwner || viewerIsAdmin) ? entry.getMemo() : null,
                isOwner
        );
    }
}
