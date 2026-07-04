package com.mlsoft.backend.domain.user.entity;

/**
 * 사용자 권한 (docs/01 요구사항 13).
 * 퇴직은 별도 role(RETIRED)이 아니라 is_active=false + retired_at으로 판별한다
 * — 퇴직 전 직급·권한 이력 보존에 유리 (docs/02 검토 메모 2 참고).
 */
public enum Role {
    SYSTEM_ADMIN, // 총관리자 — 전체 권한 + 관리자 화면
    TEAM_LEADER,  // 팀장 — 팀원 연차 관리 + 서브 승인자 지정 대상
    EMPLOYEE      // 사원 — 신청·조회
}
