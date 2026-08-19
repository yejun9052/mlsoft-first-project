package com.mlsoft.backend.domain.user.service;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.department.repository.DepartmentRepository;
import com.mlsoft.backend.domain.user.entity.OnboardingStatus;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 승인자 결정 규칙 — 연차·복리후생이 <b>같은 규칙</b>을 쓴다 (리뷰 I-5).
 * <p>
 * 전에는 {@code LeaveService}·{@code WelfareService}에 똑같은 메서드가 복사돼 있었고, 그 3~4줄에
 * 결함 3개가 밀집해 있었다. 한 곳으로 모아 규칙이 갈라지지 않게 한다.
 *
 * <h3>승인자가 될 수 있는 조건</h3>
 * <ol>
 *   <li><b>재직 중</b>이어야 한다 — 퇴직자는 로그인 자체가 막힌다</li>
 *   <li><b>권한이 TEAM_LEADER 또는 SYSTEM_ADMIN</b>이어야 한다 — {@code @PreAuthorize}가 역할로 막는다.
 *       팀장이 EMPLOYEE로 강등돼도 {@code department.leader_id}는 그대로 남으므로, role을 확인하지 않으면
 *       결재할 수 없는 사람이 primary로 지정돼 <b>신청이 영구 PENDING</b>으로 남는다(선차감 유지). (I-5a)</li>
 *   <li><b>온보딩을 마쳤어야</b> 한다 — {@code OnboardingCheckInterceptor}가 {@code hire_date}가 없는
 *       계정의 {@code /api/**} 접근을 막으므로, 지정돼도 결재를 못 한다 (I-5b, 검증 Y-2)</li>
 *   <li><b>신청자 본인이 아니어야</b> 한다 — 셀프 결재 방지. leader 분기에만 있고 fallback에는 없어서
 *       첫 SYSTEM_ADMIN이 본인이면 자기 신청을 자기가 승인할 수 있었다 (I-5c).
 *       <b>단 신청자가 SYSTEM_ADMIN이면 이 탐색에 들어오지 않는다</b> — 본인이 승인자다
 *       ({@link #resolvePrimary} 첫 줄, 2026-08-19)</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApproverResolver {

    /** 서브 승인자로 지정 가능한 역할 */
    private static final List<Role> APPROVER_ROLES = List.of(Role.TEAM_LEADER, Role.SYSTEM_ADMIN);

    private final UserRepository userRepository;
    // 상위 부서로 올라가기 위해 필요하다 — Department.parentId가 관계가 아니라 raw id다
    private final DepartmentRepository departmentRepository;

    /**
     * 기본 승인자 결정 — <b>가까운 곳부터 위로 올라가며</b> 결재할 수 있는 팀장을 찾는다.
     *
     * <pre>
     * ⓪ 신청자가 SYSTEM_ADMIN  → 본인 (2026-08-19 추가. 위에 결재선이 없다)
     * ① 소속 부서 팀장          자격 있으면 → 확정
     * ② 상위 부서 팀장 (2026-08-16 추가)  자격 있으면 → 확정
     *    └ 그 위에 또 부모가 있으면 계속 (현재 계층은 2단계라 실제로는 한 번만 올라간다)
     * ③ SYSTEM_ADMIN 중 id가 가장 작은 계정 (신청자 본인 제외)
     * ④ 그마저 없으면 INVALID_APPROVER
     * </pre>
     *
     * <p><b>②를 넣은 이유</b>: 예전에는 자기 부서 팀장이 없으면 곧바로 ③으로 갔다. 그래서
     * "개발본부(팀장 있음) → 개발 1팀(공석)" 구조에서 개발 1팀 신청이 <b>바로 위 본부장을 건너뛰고</b>
     * 총관리자에게 갔다. 조직도상 결재선이 있는데 시스템만 모르는 상태였다.
     *
     * <p><b>각 단계에서 무엇을 "자격"으로 보는가</b>는 {@link #isEligibleFor} 하나뿐이다 —
     * 재직 + TEAM_LEADER/SYSTEM_ADMIN + 온보딩 완료 + 신청자 본인이 아닐 것. 단계마다 기준이
     * 다르면 "왜 이 사람이 승인자인지" 설명할 수 없게 된다.
     *
     * <p><b>팀장이 지정돼 있는데 자격이 없으면 WARN을 남기고 위로 올라간다.</b> 조용히 넘어가면
     * 관리자는 그 부서 결재선이 비어 있다는 것을 영원히 모른다.
     */
    public User resolvePrimary(User applicant) {
        // 총관리자 위에는 결재선이 없다 — 본인이 자기 결재자다 (2026-08-19).
        //
        // 2026-08-17에 "본인 신청은 본인이 결재할 수 없다"를 넣으면서 총관리자도 예외로 두지
        // 않았는데, 그러면 총관리자의 연차가 갈 곳이 없다. 아래 ③이 **본인을 제외한** 다른
        // 총관리자를 찾으므로 총관리자가 한 명뿐이면 INVALID_APPROVER로 **신청 자체가 막힌다.**
        // 부서 팀장이 있으면 올라가지도 않고 ①에서 잡히는데, 그건 부하가 상급자의 연차를
        // 심사하는 모양이 된다.
        //
        // 그래서 "마지막 수단"이 아니라 **맨 앞**에 둔다. 부서 팀장이 있느냐 없느냐에 따라
        // 총관리자의 결재선이 달라지면 "왜 이 사람이 승인자인지" 설명할 수 없다.
        if (applicant.getRole() == Role.SYSTEM_ADMIN) {
            return applicant;
        }

        User leader = findLeaderUpwards(applicant);
        if (leader != null) {
            return leader;
        }
        return userRepository
                .findFirstByRoleAndIsActiveTrueAndOnboardingStatusAndIdNotOrderByIdAsc(
                        Role.SYSTEM_ADMIN, OnboardingStatus.COMPLETED, applicant.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_APPROVER));
    }

    /**
     * 소속 부서에서 시작해 상위 부서로 올라가며 결재 가능한 팀장을 찾는다. 없으면 {@code null}.
     *
     * <p>현재 부서 계층은 2단계로 제한돼 있어(생성·수정 시 검증) 실제로는 한 번만 올라간다.
     * 그래도 반복으로 쓴 이유는 계층이 깊어져도 규칙이 그대로 성립하기 때문이다.
     *
     * <p><b>방문한 부서를 기억하는 것은 방어 코드다.</b> 계층 검증이 순환(A→B→A)을 막지만,
     * DB를 직접 고치면 만들 수 있다. 그때 이 루프가 영원히 돌면 연차 신청 한 건이 스레드를 물고
     * 늘어진다 — 잘못된 데이터의 대가를 신청자가 치르게 하지 않는다.
     */
    private User findLeaderUpwards(User applicant) {
        Department department = applicant.getDepartment();
        Set<Long> visited = new HashSet<>();

        while (department != null) {
            if (department.getId() != null && !visited.add(department.getId())) {
                log.warn("[승인자] 부서 계층에 순환이 있어 탐색을 멈춘다 — departmentId={}", department.getId());
                return null;
            }

            User leader = department.getLeader();
            if (leader != null) {
                if (isEligibleFor(leader, applicant)) {
                    // visited가 2개 이상이면 한 번 이상 위로 올라왔다는 뜻이다.
                    // 왜 이 사람이 승인자인지 나중에 설명할 수 있어야 한다
                    if (visited.size() > 1) {
                        log.info("[승인자] 하위 부서 팀장이 없어 상위 부서 팀장으로 결정 — "
                                        + "applicantId={}, departmentId={}, leaderId={}",
                                applicant.getId(), department.getId(), leader.getId());
                    }
                    return leader;
                }
                // 강등·퇴직·온보딩 미완료로 팀장이 결재할 수 없는 상태 — 관리자가 정리해야 할 신호다
                log.warn("[승인자] 부서 팀장이 결재 불가 상태라 상위 부서로 올라간다 — departmentId={}, leaderId={}",
                        department.getId(), leader.getId());
            }

            Long parentId = department.getParentId();
            department = parentId == null ? null : departmentRepository.findById(parentId).orElse(null);
        }
        return null;
    }

    /**
     * 서브 승인자 검증 — 선택 사항이라 {@code null}이면 그대로 {@code null}.
     * 지정했다면 기본 승인자와 같은 조건을 만족해야 한다 (docs/01 2-3).
     *
     * <p><b>기본 승인자와 같은 사람은 거부한다</b> (리뷰 I-7). 서브 승인자의 목적은
     * 두 사람 중 먼저 처리한 쪽이 결재를 확정하는 <b>병렬 선착순</b>인데, 같은 사람이
     * 양쪽에 들어가면 그 구조가 1인 결재로 축퇴한다 — 화면에는 승인자가 둘로 보이는데
     * 실제로는 한 명이 막고 있는 상태가 되고, 그 사람이 부재하면 대안이 없다.
     */
    public User resolveSub(Long subApproverId, User applicant, User primaryApprover) {
        if (subApproverId == null) {
            return null;
        }
        if (primaryApprover != null && subApproverId.equals(primaryApprover.getId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_APPROVER);
        }
        User sub = userRepository.findById(subApproverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_APPROVER));
        if (!isEligibleFor(sub, applicant)) {
            throw new BusinessException(ErrorCode.INVALID_APPROVER);
        }
        return sub;
    }

    /**
     * 결재할 수 있는 사람인가 — 신청자와 무관한 <b>자격</b>만 본다 (재직·역할·온보딩).
     * 부서 팀장을 지정할 때처럼 "누구의 신청인지"가 아직 없는 자리에서 쓴다.
     */
    public boolean canApprove(User candidate) {
        return candidate != null
                && candidate.isActive()
                && APPROVER_ROLES.contains(candidate.getRole())
                && candidate.isOnboardingCompleted();
    }

    /**
     * 이 신청의 승인자가 될 수 있는가 — 자격 + 셀프 결재 배제.
     * 셀프 배제를 자격과 분리한 이유: 팀장 지정처럼 신청자가 정해지지 않은 시점에도
     * 자격만 따로 검사해야 하기 때문이다.
     */
    public boolean isEligibleFor(User candidate, User applicant) {
        return canApprove(candidate) && !candidate.getId().equals(applicant.getId());
    }
}
