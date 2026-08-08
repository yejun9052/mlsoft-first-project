package com.mlsoft.backend.domain.policy.service;

import com.mlsoft.backend.domain.leave.entity.LeaveResetHistory;
import com.mlsoft.backend.domain.leave.repository.LeaveResetHistoryRepository;
import com.mlsoft.backend.domain.policy.dto.LeaveResetHistoryResponse;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

/**
 * 기산일 리셋 이력 서비스 단위 테스트 (GET /api/admin/reset-histories, docs/02 3-11(b)).
 * LAZY user 연관을 트랜잭션 내에서 응답 DTO(userName 포함)로 정확히 변환하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class LeaveResetHistoryServiceTest {

    @Mock
    private LeaveResetHistoryRepository leaveResetHistoryRepository;

    @InjectMocks
    private LeaveResetHistoryService leaveResetHistoryService;

    @Test
    @DisplayName("리셋 이력 조회 — user 정보를 포함해 응답 DTO로 변환한다")
    void getResetHistories_mapsUserNameFromLazyAssociation() {
        User user = User.builder()
                .id(1L)
                .name("박민수")
                .email("minsu@mlsoft.com")
                .role(Role.EMPLOYEE)
                .baseDays(new BigDecimal("15.0"))
                .useDays(new BigDecimal("5.0"))
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build();
        LeaveResetHistory history = LeaveResetHistory.create(user, LocalDate.of(2026, 4, 10),
                new BigDecimal("16.0"), BigDecimal.ZERO, BigDecimal.ZERO);
        Pageable pageable = PageRequest.of(0, 10);
        given(leaveResetHistoryRepository.findAll(pageable))
                .willReturn(new PageImpl<>(List.of(history), pageable, 1));

        var responses = leaveResetHistoryService.getResetHistories(pageable);

        assertEquals(1, responses.getTotalElements());
        LeaveResetHistoryResponse response = responses.getContent().get(0);
        assertEquals(1L, response.userId());
        assertEquals("박민수", response.userName());
        assertEquals(LocalDate.of(2026, 4, 10), response.resetDate());
        assertEquals(0, new BigDecimal("15.0").compareTo(response.prevBaseDays()));
        assertEquals(0, new BigDecimal("5.0").compareTo(response.prevUseDays()));
        assertEquals(0, new BigDecimal("16.0").compareTo(response.newBaseDays()));
    }
}
