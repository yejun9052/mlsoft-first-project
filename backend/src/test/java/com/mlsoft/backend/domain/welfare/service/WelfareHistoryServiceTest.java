package com.mlsoft.backend.domain.welfare.service;

import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.domain.welfare.dto.WelfareHistoryLogResponse;
import com.mlsoft.backend.domain.welfare.entity.WelfareActionHistory;
import com.mlsoft.backend.domain.welfare.entity.WelfarePolicy;
import com.mlsoft.backend.domain.welfare.entity.WelfareRequest;
import com.mlsoft.backend.domain.welfare.entity.WelfareTarget;
import com.mlsoft.backend.domain.welfare.repository.WelfareActionHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 복리후생 처리 로그 서비스 단위 테스트 (GET /api/welfare-histories — docs/03 처리 이력).
 * 카테고리·부여일수가 신청 시점 스냅샷(메모 7)에서 오는지, 팀 스코프가 요청자 부서로 결정되는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class WelfareHistoryServiceTest {

    @Mock
    private WelfareActionHistoryRepository welfareActionHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WelfareHistoryService welfareHistoryService;

    private static final Pageable PAGEABLE = PageRequest.of(0, 20);

    @Test
    @DisplayName("전체 로그 — 신청 시점 스냅샷(카테고리·부여일수)과 처리자·신청자를 함께 내려준다")
    void getHistories_mapsSnapshotAndAssociations() {
        Department department = department(10L, "개발팀");
        User applicant = user(1L, "박민수", department);
        User approver = user(2L, "김팀장", department);
        WelfareActionHistory history =
                history(applicant, approver, RequestAction.APPROVED, "증빙 확인했습니다");
        given(welfareActionHistoryRepository.findAll(PAGEABLE))
                .willReturn(new PageImpl<>(List.of(history), PAGEABLE, 1));

        Page<WelfareHistoryLogResponse> responses = welfareHistoryService.getHistories(null, PAGEABLE);

        assertEquals(1, responses.getTotalElements());
        WelfareHistoryLogResponse response = responses.getContent().get(0);
        assertEquals("APPROVED", response.action());
        assertEquals("김팀장", response.actorName());
        assertEquals("박민수", response.userName());
        assertEquals("개발팀", response.departmentName());
        assertEquals("결혼", response.category());
        assertEquals(0, new BigDecimal("7.0").compareTo(response.addDays()));
        assertEquals("증빙 확인했습니다", response.comment());
    }

    @Test
    @DisplayName("전체 로그 — action을 주면 해당 액션만 조회한다")
    void getHistories_withActionFilter_delegatesToFilteredQuery() {
        given(welfareActionHistoryRepository.findByAction(RequestAction.CANCELLED, PAGEABLE))
                .willReturn(new PageImpl<>(List.of(), PAGEABLE, 0));

        welfareHistoryService.getHistories(RequestAction.CANCELLED, PAGEABLE);

        verify(welfareActionHistoryRepository).findByAction(RequestAction.CANCELLED, PAGEABLE);
        verify(welfareActionHistoryRepository, never()).findAll(PAGEABLE);
    }

    @Test
    @DisplayName("결재자 로그 — 요청자 id를 승인자 조건으로 넘긴다 (연차와 같은 기준, 리뷰 S-6)")
    void getMyApprovalHistories_scopesToApproverId() {
        given(welfareActionHistoryRepository.findByApprover(2L, null, PAGEABLE))
                .willReturn(new PageImpl<>(List.of(), PAGEABLE, 0));

        welfareHistoryService.getMyApprovalHistories(2L, null, PAGEABLE);

        verify(welfareActionHistoryRepository).findByApprover(2L, null, PAGEABLE);
    }

    @Test
    @DisplayName("결재자 로그 — action 필터는 저장소로 그대로 넘어간다 (서비스 분기 없음)")
    void getMyApprovalHistories_passesActionThrough() {
        given(welfareActionHistoryRepository.findByApprover(2L, RequestAction.APPROVED, PAGEABLE))
                .willReturn(new PageImpl<>(List.of(), PAGEABLE, 0));

        welfareHistoryService.getMyApprovalHistories(2L, RequestAction.APPROVED, PAGEABLE);

        verify(welfareActionHistoryRepository).findByApprover(2L, RequestAction.APPROVED, PAGEABLE);
    }

    // ---------------------------------------------------------------------
    // 픽스처
    // ---------------------------------------------------------------------

    private static Department department(Long id, String name) {
        return Department.builder().id(id).name(name).description(name + " 설명").active(true).build();
    }

    private static User user(Long id, String name, Department department) {
        return User.builder()
                .id(id)
                .name(name)
                .email(name + "@mlsoft.com")
                .role(Role.EMPLOYEE)
                .department(department)
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build();
    }

    /** 결혼 7일 복리후생을 actor가 처리한 이력 */
    private static WelfareActionHistory history(User applicant, User actor, RequestAction action, String comment) {
        WelfarePolicy policy =
                WelfarePolicy.create("결혼", WelfareTarget.SELF, new BigDecimal("7.0"), "청첩장", "본인 결혼");
        WelfareRequest request = WelfareRequest.create(policy, applicant, "결혼합니다", actor, null);
        return WelfareActionHistory.create(request, actor, action, comment);
    }
}
