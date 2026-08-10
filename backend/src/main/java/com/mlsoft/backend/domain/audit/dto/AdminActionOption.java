package com.mlsoft.backend.domain.audit.dto;

import com.mlsoft.backend.domain.audit.entity.AdminAction;

/** 감사 로그 필터 옵션 (GET /api/admin/audit-logs/actions) — 프론트의 액션 이름 하드코딩 방지 */
public record AdminActionOption(String name, String label) {

    public static AdminActionOption of(AdminAction action) {
        return new AdminActionOption(action.name(), action.getLabel());
    }
}
