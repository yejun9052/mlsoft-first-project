package com.mlsoft.backend.domain.user.service;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

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
 *       첫 SYSTEM_ADMIN이 본인이면 자기 신청을 자기가 승인할 수 있었다 (I-5c)</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApproverResolver {

    /** 서브 승인자로 지정 가능한 역할 */
    private static final List<Role> APPROVER_ROLES = List.of(Role.TEAM_LEADER, Role.SYSTEM_ADMIN);

    private final UserRepository userRepository;

    /**
     * 기본 승인자 = 소속 부서 팀장. 조건을 못 갖추면 SYSTEM_ADMIN fallback (검증 Y-3).
     * 둘 다 없으면 {@code INVALID_APPROVER}.
     */
    public User resolvePrimary(User applicant) {
        Department department = applicant.getDepartment();
        if (department != null && department.getLeader() != null) {
            User leader = department.getLeader();
            if (isEligibleFor(leader, applicant)) {
                return leader;
            }
            // 강등·퇴직·온보딩 미완료로 팀장이 결재할 수 없는 상태 — 조용히 넘어가지 않고 남긴다.
            // 부서에 팀장이 지정돼 있는데 fallback을 타는 건 관리자가 정리해야 할 신호다.
            log.warn("[승인자] 부서 팀장이 결재 불가 상태라 SYSTEM_ADMIN으로 대체 — departmentId={}, leaderId={}",
                    department.getId(), leader.getId());
        }
        return userRepository
                .findFirstByRoleAndIsActiveTrueAndHireDateIsNotNullAndIdNotOrderByIdAsc(
                        Role.SYSTEM_ADMIN, applicant.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_APPROVER));
    }

    /**
     * 서브 승인자 검증 — 선택 사항이라 {@code null}이면 그대로 {@code null}.
     * 지정했다면 기본 승인자와 같은 조건을 만족해야 한다 (docs/01 2-3).
     */
    public User resolveSub(Long subApproverId, User applicant) {
        if (subApproverId == null) {
            return null;
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
