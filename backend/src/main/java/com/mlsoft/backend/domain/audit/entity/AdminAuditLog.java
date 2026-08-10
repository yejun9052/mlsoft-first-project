package com.mlsoft.backend.domain.audit.entity;

import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관리자 조작 감사 로그 (리뷰 S-3).
 *
 * <p>권한 변경·연차 직접 설정·퇴직·온보딩 승인 같은 조작은 지금까지 {@code log.info}에만 남았다.
 * 애플리케이션 로그는 회전되고, 조회 수단이 서버 접속뿐이며, "누가 언제 무엇을 몇 일에서 몇 일로
 * 바꿨는지"를 사후에 대조할 수 없다. 승인 한 번으로 연차가 부여되는 절차(S-1)가 생기면서
 * 대상이 더 늘었다.
 *
 * <p><b>append-only</b>다 — 수정·삭제 메서드가 없다. 도메인 메서드도 정적 팩토리 하나뿐이고
 * 이 엔티티를 바꾸는 경로는 존재하지 않는다.
 *
 * <p><b>조작과 같은 트랜잭션에서 기록한다</b>. 조작이 롤백되면 감사 기록도 함께 사라지는데,
 * 이게 의도한 동작이다 — 일어나지 않은 일을 기록으로 남기면 대조가 더 어려워진다.
 * 실패한 시도는 {@code @PreAuthorize}가 이미 역할을 걸러낸 뒤이므로 정당한 관리자의
 * 업무 규칙 위반뿐이라 감사 가치가 낮고, 그건 {@code log.warn}으로 충분하다.
 */
@Entity
// 조회 인덱스 — leave_action_history와 같은 근거(append-only, 목록이 createdAt 내림차순).
// action은 값이 7개뿐이라 선택도가 낮아 단독 인덱스를 두지 않는다.
@Table(name = "admin_audit_log", indexes = {
        @Index(name = "idx_audit_created", columnList = "created_at"),
        @Index(name = "idx_audit_actor_created", columnList = "actor_id, created_at"),
        @Index(name = "idx_audit_target_created", columnList = "target_user_id, created_at")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AdminAuditLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 조작한 관리자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    /** 조작 종류 — MySQL에서는 네이티브 enum 컬럼으로 생성된다 (다른 이력 테이블과 같다) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminAction action;

    /**
     * 대상 사원 — 시스템 설정 변경처럼 사원이 대상이 아닌 조작은 null.
     * 사원별 이력 조회(누가 이 사람 연차를 건드렸나)의 필터 키다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    private User targetUser;

    /**
     * 조작 시점의 대상 표시명 스냅샷 — 사원명 또는 설정 키.
     * FK만 두면 사원이 개명했을 때 과거 기록이 현재 이름으로 보인다. 감사 기록은
     * <b>그때 무엇이 보였는지</b>가 남아야 하므로 이름을 복사해 둔다.
     */
    @Column(name = "target_label", nullable = false, length = 100)
    private String targetLabel;

    /** 변경 전 값 — 사람이 읽는 문자열. 대조가 목적이라 타입별 컬럼으로 쪼개지 않는다 */
    @Column(name = "before_value", length = 255)
    private String beforeValue;

    /** 변경 후 값 */
    @Column(name = "after_value", length = 255)
    private String afterValue;

    /** 감사 기록 생성 — 유일한 쓰기 경로 */
    public static AdminAuditLog create(User actor, AdminAction action, User targetUser,
                                       String targetLabel, String beforeValue, String afterValue) {
        return AdminAuditLog.builder()
                .actor(actor)
                .action(action)
                .targetUser(targetUser)
                .targetLabel(targetLabel)
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .build();
    }
}
