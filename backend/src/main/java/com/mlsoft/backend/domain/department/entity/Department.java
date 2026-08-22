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

    /** 활성화 여부 — 소프트 삭제 (docs/02 갭: DELETE=비활성화 API 계약 충족을 위해 엔티티 확장, WelfarePolicy와 동일 패턴) */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * <b>시스템 기본 부서인가</b> — 첫 기동에 만들어지는 "미배정" 한 행만 참이다 (2026-08-20).
     *
     * <p>이 표시가 필요한 이유는 <b>같은 단어가 두 가지를 가리켰기 때문</b>이다.
     * 화면의 "미배정"은 원래 {@code department == null}용 문구인데,
     * {@code DataInitializer}가 <b>그 이름의 실제 부서 행</b>을 만들고 신규 가입자를 거기 배속한다.
     * 그래서 실계정 중 부서가 {@code null}인 사람이 사실상 없고, "부서 없음"을 조건으로 삼은
     * 화면 분기가 전부 죽어 있었다.
     *
     * <p><b>이름으로 판별하지 않는 이유</b>: 관리자가 부서 이름을 바꾸는 순간 조용히 깨진다.
     * 그래서 이름이 아니라 <b>구조적 속성</b>으로 못박는다 — 이름·설명은 바꿔도 이 표시는 남는다.
     */
    @Column(name = "system_default", nullable = false)
    @Builder.Default
    private boolean systemDefault = false;

    /** 부서 생성 */
    public static Department create(String name, String description, Long parentId) {
        return Department.builder()
                .name(name)
                .description(description)
                .parentId(parentId)
                .build();
    }

    /** 시스템 기본 부서 생성 — {@code DataInitializer} 전용 (첫 기동에 한 번) */
    public static Department createSystemDefault(String name, String description) {
        return Department.builder()
                .name(name)
                .description(description)
                .systemDefault(true)
                .build();
    }

    /**
     * 이미 만들어져 있던 기본 부서에 표시를 붙인다 — {@code DataInitializer} 전용.
     * 이 컬럼이 생기기 전에 만들어진 DB를 위한 멱등 보정이다.
     */
    public void markAsSystemDefault() {
        this.systemDefault = true;
    }

    /** 팀장 지정 */
    public void assignLeader(User leader) {
        this.leader = leader;
    }

    /** 팀장 해제 — 팀장 퇴직 시 결재 이관 규칙 (갭분석 B-4) */
    public void clearLeader() {
        this.leader = null;
    }

    /** 부서 정보 수정 (PUT /api/departments/{id}) — 이름·설명·상위부서 전체 갱신 */
    public void update(String name, String description, Long parentId) {
        this.name = name;
        this.description = description;
        this.parentId = parentId;
    }

    /** 부서 비활성화 (소프트 삭제, DELETE /api/departments/{id}) */
    public void deactivate() {
        this.active = false;
    }
}
