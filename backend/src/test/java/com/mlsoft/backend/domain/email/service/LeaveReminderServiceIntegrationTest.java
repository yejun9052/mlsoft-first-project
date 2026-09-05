package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.domain.email.entity.EmailHistory;
import com.mlsoft.backend.domain.email.entity.EmailTemplate;
import com.mlsoft.backend.domain.email.entity.LeaveReminderDispatch;
import com.mlsoft.backend.domain.email.entity.ReminderDispatchResult;
import com.mlsoft.backend.domain.email.repository.EmailHistoryRepository;
import com.mlsoft.backend.domain.email.repository.EmailTemplateRepository;
import com.mlsoft.backend.domain.email.repository.LeaveReminderDispatchRepository;
import com.mlsoft.backend.domain.policy.entity.LeavePolicyConfig;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
import com.mlsoft.backend.domain.policy.repository.LeavePolicyConfigRepository;
import com.mlsoft.backend.domain.user.entity.OnboardingStatus;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 연차 소진 안내 대상 판정·중복·양식·H2 저장 경계를 검증한다. */
@SpringBootTest(properties = {
        "spring.mail.username=",
        "spring.mail.password="
})
@ActiveProfiles("test")
class LeaveReminderServiceIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 1, 30);

    @Autowired
    private LeaveReminderService leaveReminderService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LeaveReminderDispatchRepository dispatchRepository;
    @Autowired
    private EmailHistoryRepository emailHistoryRepository;
    @Autowired
    private EmailTemplateRepository emailTemplateRepository;
    @Autowired
    private LeavePolicyConfigRepository configRepository;

    @BeforeEach
    void setUp() {
        dispatchRepository.deleteAll();
        emailHistoryRepository.deleteAll();
        emailTemplateRepository.deleteAll();
        userRepository.deleteAll();
        setConfig(PolicyConfigKey.REMINDER_AUTO_CYCLE, "D30");
        setConfig(PolicyConfigKey.REMINDER_LIST_DAYS, "30");
    }

    @Test
    @DisplayName("기산일 30일 전 경계만 D30 대상이다")
    void findTargetIds_D30경계() {
        User target = saveUser("대상", "target@mlsoft.com", LocalDate.of(2025, 3, 1),
                OnboardingStatus.COMPLETED, "15.0");
        User outside = saveUser("경계밖", "outside@mlsoft.com", LocalDate.of(2025, 3, 2),
                OnboardingStatus.COMPLETED, "15.0");

        assertEquals(List.of(target.getId()), leaveReminderService.findTargetIds(TODAY));
        assertTrue(!leaveReminderService.findTargetIds(TODAY).contains(outside.getId()));
    }

    @Test
    @DisplayName("NONE이면 조회·발송 이력을 만들지 않는다")
    void findTargetIds_NONE_0건() {
        setConfig(PolicyConfigKey.REMINDER_AUTO_CYCLE, "NONE");
        User user = saveUser("없음", "none@mlsoft.com", LocalDate.of(2025, 3, 1),
                OnboardingStatus.COMPLETED, "15.0");

        assertTrue(leaveReminderService.findTargetIds(TODAY).isEmpty());
        assertEquals(0, leaveReminderService.dispatch(user.getId(), TODAY));
        assertEquals(0, dispatchRepository.count());
        assertEquals(0, emailHistoryRepository.count());
    }

    @Test
    @DisplayName("같은 사원·주기·기간은 두 번 실행해도 한 건이다")
    void dispatch_중복_1건() {
        User user = saveUser("중복", "duplicate@mlsoft.com", LocalDate.of(2025, 3, 1),
                OnboardingStatus.COMPLETED, "15.0");

        assertEquals(1, leaveReminderService.dispatch(user.getId(), TODAY));
        assertEquals(0, leaveReminderService.dispatch(user.getId(), TODAY));
        assertEquals(1, dispatchRepository.count());
        assertEquals(1, emailHistoryRepository.count());
    }

    @Test
    @DisplayName("이메일이 공백이면 SKIPPED_NO_EMAIL만 기록한다")
    void dispatch_이메일없음_건너뜀() {
        User user = saveUser("무메일", "", LocalDate.of(2025, 3, 1),
                OnboardingStatus.COMPLETED, "15.0");

        assertEquals(1, leaveReminderService.dispatch(user.getId(), TODAY));
        LeaveReminderDispatch dispatch = dispatchRepository.findAll().getFirst();
        assertEquals(ReminderDispatchResult.SKIPPED_NO_EMAIL, dispatch.getResult());
        assertEquals(0, emailHistoryRepository.count());
    }

    @Test
    @DisplayName("DB 양식이 있으면 변수 치환·HTML escaping 후 아웃박스에 보존한다")
    void dispatch_DB양식_치환과escaping() {
        emailTemplateRepository.saveAndFlush(EmailTemplate.create(
                "LEAVE_BALANCE_REMINDER",
                "안내 {name} {remainingDays}",
                "이름={name}; 잔여={remainingDays}; 기산일={nextResetDate}; 남은날={daysUntilReset}; URL={serviceUrl}"));
        User user = saveUser("A&B<대상>", "template@mlsoft.com", LocalDate.of(2025, 3, 1),
                OnboardingStatus.COMPLETED, "15.0");

        leaveReminderService.dispatch(user.getId(), TODAY);

        EmailHistory history = emailHistoryRepository.findAll().getFirst();
        assertEquals("안내 A&B<대상> 15", history.getTitle());
        assertTrue(history.getContent().contains("A&amp;B"));
        assertTrue(history.getContent().contains("&lt;대상&gt;"));
        assertTrue(history.getContent().contains("2026-03-01"));
        assertTrue(history.getContent().contains("30"));
        assertTrue(history.getContent().contains("&amp;"));
    }

    @Test
    @DisplayName("DB 양식이 없으면 기본 리마인더 문구를 사용한다")
    void dispatch_DB양식없음_기본문구() {
        User user = saveUser("기본", "default@mlsoft.com", LocalDate.of(2025, 3, 1),
                OnboardingStatus.COMPLETED, "15.0");

        leaveReminderService.dispatch(user.getId(), TODAY);

        EmailHistory history = emailHistoryRepository.findAll().getFirst();
        assertTrue(history.getTitle().contains("연차 소진 안내"));
        assertTrue(history.getContent().contains("현재 사용 가능한 연차가 15일 남아 있습니다."));
    }

    @Test
    @DisplayName("잔여 0·온보딩 승인 대기 사원은 대상에서 제외한다")
    void findTargetIds_잔여0과승인대기_제외() {
        saveUser("잔여없음", "zero@mlsoft.com", LocalDate.of(2025, 3, 1),
                OnboardingStatus.COMPLETED, "0.0");
        saveUser("승인대기", "pending@mlsoft.com", LocalDate.of(2025, 3, 1),
                OnboardingStatus.PENDING_APPROVAL, "15.0");

        assertTrue(leaveReminderService.findTargetIds(TODAY).isEmpty());
    }

    @Test
    @DisplayName("QUARTER는 분기 첫날부터 7일 이내에만 catch-up한다")
    void findTargetIds_QUARTER_catchUpWindow() {
        setConfig(PolicyConfigKey.REMINDER_AUTO_CYCLE, "QUARTER");
        User user = saveUser("분기", "quarter@mlsoft.com", LocalDate.of(2025, 5, 1),
                OnboardingStatus.COMPLETED, "15.0");

        assertEquals(List.of(user.getId()),
                leaveReminderService.findTargetIds(LocalDate.of(2026, 4, 1)));
        assertEquals(List.of(user.getId()),
                leaveReminderService.findTargetIds(LocalDate.of(2026, 4, 8)));
        assertTrue(leaveReminderService.findTargetIds(LocalDate.of(2026, 4, 9)).isEmpty());
    }

    private User saveUser(
            String name,
            String email,
            LocalDate lastResetDate,
            OnboardingStatus onboardingStatus,
            String baseDays
    ) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(email)
                .role(Role.EMPLOYEE)
                .hireDate(lastResetDate)
                .lastResetDate(lastResetDate)
                .onboardingStatus(onboardingStatus)
                .baseDays(new BigDecimal(baseDays))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build());
    }

    private void setConfig(PolicyConfigKey key, String value) {
        LeavePolicyConfig config = configRepository.findByName(key.getKey())
                .orElseGet(() -> configRepository.save(LeavePolicyConfig.create(key.getKey(), value)));
        config.updateValue(value);
        configRepository.saveAndFlush(config);
    }
}
