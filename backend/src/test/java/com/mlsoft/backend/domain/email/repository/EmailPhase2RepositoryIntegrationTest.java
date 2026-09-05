package com.mlsoft.backend.domain.email.repository;

import com.mlsoft.backend.domain.email.entity.EmailTemplate;
import com.mlsoft.backend.domain.email.entity.LeaveReminderDispatch;
import com.mlsoft.backend.domain.email.entity.MailCredential;
import com.mlsoft.backend.domain.email.entity.ReminderCycle;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 이메일 2단계 W1 저장소의 유일 제약과 기본 조회를 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmailPhase2RepositoryIntegrationTest {

    @Autowired
    private MailCredentialRepository mailCredentialRepository;

    @Autowired
    private EmailTemplateRepository emailTemplateRepository;

    @Autowired
    private LeaveReminderDispatchRepository leaveReminderDispatchRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("mail_credentials provider는 중복될 수 없다")
    void 메일자격증명_provider_유일() {
        mailCredentialRepository.saveAndFlush(
                MailCredential.create("SMTP", "sender@mlsoft.com", "암호문-1"));

        assertThrows(DataIntegrityViolationException.class, () ->
                mailCredentialRepository.saveAndFlush(
                        MailCredential.create("SMTP", "other@mlsoft.com", "암호문-2")));
    }

    @Test
    @DisplayName("leave_reminder_dispatch는 같은 사원·주기·기간을 한 번만 선점한다")
    void 리마인더_사원주기기간_유일() {
        User user = userRepository.saveAndFlush(User.create(
                "리마인더 대상", "reminder-" + System.nanoTime() + "@mlsoft.com",
                com.mlsoft.backend.domain.user.entity.Role.EMPLOYEE));
        String periodKey = "D30:2027-08-28";

        leaveReminderDispatchRepository.saveAndFlush(
                LeaveReminderDispatch.create(
                        user,
                        ReminderCycle.D30,
                        periodKey,
                        LocalDate.of(2026, 8, 28),
                        LocalDate.of(2026, 9, 27),
                        new BigDecimal("3.5")));

        assertTrue(leaveReminderDispatchRepository
                .existsByUserAndCycleAndPeriodKey(user, ReminderCycle.D30, periodKey));
        assertThrows(DataIntegrityViolationException.class, () ->
                leaveReminderDispatchRepository.saveAndFlush(
                        LeaveReminderDispatch.create(
                                user,
                                ReminderCycle.D30,
                                periodKey,
                                LocalDate.of(2026, 8, 28),
                                LocalDate.of(2026, 9, 27),
                                new BigDecimal("3.5"))));
    }

    @Test
    @DisplayName("email_templates는 template_key로 조회한다")
    void 이메일양식_templateKey_조회() {
        EmailTemplate saved = emailTemplateRepository.saveAndFlush(
                EmailTemplate.create(
                        "LEAVE_BALANCE_REMINDER",
                        "[연차 소진 안내] {name}님",
                        "잔여 연차: {remainingDays}"));

        EmailTemplate found = emailTemplateRepository
                .findByTemplateKey("LEAVE_BALANCE_REMINDER")
                .orElseThrow();

        assertEquals(saved.getId(), found.getId());
        assertEquals("잔여 연차: {remainingDays}", found.getBody());
    }
}
