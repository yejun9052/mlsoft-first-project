package com.mlsoft.backend.domain.department.entity;

import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 부서 (docs/02 3-2 department).
 * - 팀장은 leader_id로 명시 (이전 프로젝트의 "첫 MANAGER 검색" 방식 폐지)
 * - 팀장 공석(null)이면 승인자는 SYSTEM_ADMIN fallback (검증 Y-3)
 */
@Entity
@Table(name = "department")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Department extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 부서명 */
    @Column(nullable = false)
    private String name;

    /** 부서 설명 */
    @Column(nullable = false)
    private String description;

    /** 팀장 (명시적 지정, 공석 허용) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_id")
    private User leader;

    /** 상위 부서 id (부서 계층, 최대 2단계) */
    @Column(name = "parent_id")
    private Long parentId;

    /** 부서 생성 */
    public static Department create(String name, String description, Long parentId) {
        return Department.builder()
                .name(name)
                .description(description)
                .parentId(parentId)
                .build();
    }

    /** 팀장 지정 */
    public void assignLeader(User leader) {
        this.leader = leader;
    }

    /** 팀장 해제 — 팀장 퇴직 시 결재 이관 규칙 (갭분석 B-4) */
    public void clearLeader() {
        this.leader = null;
    }
}
