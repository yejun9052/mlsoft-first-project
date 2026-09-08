package com.mlsoft.backend.global.validation;

import com.mlsoft.backend.domain.leave.dto.ApprovalRequest;
import com.mlsoft.backend.domain.leave.dto.CancelRequest;
import com.mlsoft.backend.domain.leave.dto.LeaveCreateRequest;
import com.mlsoft.backend.domain.leave.entity.LeaveType;
import com.mlsoft.backend.domain.auth.dto.OnboardingRequest;
import com.mlsoft.backend.domain.user.dto.UserProfileUpdateRequest;
import com.mlsoft.backend.domain.welfare.dto.WelfareApprovalRequest;
import com.mlsoft.backend.domain.welfare.dto.WelfareCreateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 자유 입력 필드 길이 상한 (리뷰 I-7).
 *
 * <p>사유·코멘트는 {@code varchar(255)} 컬럼에 그대로 들어간다. {@code @Size}가 없으면
 * 256자 요청이 검증을 통과해 <b>insert 단계에서 DataIntegrityViolation → 500</b>이 됐다.
 * 400으로 돌려줘야 사용자가 무엇을 고쳐야 하는지 알 수 있다.
 *
 * <p>경계값 255/256을 <b>DB 컬럼 길이와 짝지어</b> 고정한다 — 컬럼을 늘리거나 줄이면서
 * 애노테이션을 안 고치면 이 테스트가 깨지는 것이 목적이다.
 */
class FreeTextLengthValidationTest {

    /** varchar(255) — db/schema.sql의 reason·comment 컬럼 길이와 같아야 한다 */
    private static final int COLUMN_LENGTH = 255;

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("연차 신청 사유 — 255자는 통과, 256자는 위반 1건")
    void leaveCreateReason_boundary() {
        assertTrue(validator.validate(leaveRequest(text(COLUMN_LENGTH))).isEmpty());
        assertEquals(1, validator.validate(leaveRequest(text(COLUMN_LENGTH + 1))).size());
    }

    @Test
    @DisplayName("연차 취소 사유 — 256자는 위반")
    void cancelReason_boundary() {
        assertTrue(validator.validate(new CancelRequest(text(COLUMN_LENGTH))).isEmpty());
        assertEquals(1, validator.validate(new CancelRequest(text(COLUMN_LENGTH + 1))).size());
    }

    @Test
    @DisplayName("연차 처리 코멘트 — 선택 항목이지만 길이 상한은 있다")
    void approvalComment_boundary() {
        // comment는 @NotBlank가 아니므로 null·빈 문자열도 통과해야 한다
        assertTrue(validator.validate(new ApprovalRequest(true, null)).isEmpty());
        assertTrue(validator.validate(new ApprovalRequest(true, text(COLUMN_LENGTH))).isEmpty());
        assertEquals(1, validator.validate(new ApprovalRequest(true, text(COLUMN_LENGTH + 1))).size());
    }

    @Test
    @DisplayName("복리후생 신청 사유 — 256자는 위반")
    void welfareCreateReason_boundary() {
        assertTrue(validator.validate(new WelfareCreateRequest(1L, text(COLUMN_LENGTH), null)).isEmpty());
        assertEquals(1,
                validator.validate(new WelfareCreateRequest(1L, text(COLUMN_LENGTH + 1), null)).size());
    }

    @Test
    @DisplayName("복리후생 처리 코멘트 — 256자는 위반")
    void welfareApprovalComment_boundary() {
        assertTrue(validator.validate(new WelfareApprovalRequest(false, text(COLUMN_LENGTH))).isEmpty());
        assertEquals(1,
                validator.validate(new WelfareApprovalRequest(false, text(COLUMN_LENGTH + 1))).size());
    }

    @Test
    @DisplayName("온보딩 직급 — 50자는 통과, 51자는 위반")
    void onboardingJobGrade_boundary() {
        int length = 50;
        OnboardingRequest valid = new OnboardingRequest(
                LocalDate.of(1995, 4, 1), LocalDate.now().minusDays(1), text(length));
        OnboardingRequest invalid = new OnboardingRequest(
                LocalDate.of(1995, 4, 1), LocalDate.now().minusDays(1), text(length + 1));

        assertTrue(validator.validate(valid).isEmpty());
        assertEquals(1, validator.validate(invalid).size());
    }

    @Test
    @DisplayName("프로필 직급 — 50자는 통과, 51자는 위반")
    void profileJobGrade_boundary() {
        int length = 50;
        UserProfileUpdateRequest valid = new UserProfileUpdateRequest(
                "사원", LocalDate.of(1995, 4, 1), null, text(length));
        UserProfileUpdateRequest invalid = new UserProfileUpdateRequest(
                "사원", LocalDate.of(1995, 4, 1), null, text(length + 1));

        assertTrue(validator.validate(valid).isEmpty());
        assertEquals(1, validator.validate(invalid).size());
    }

    private LeaveCreateRequest leaveRequest(String reason) {
        return new LeaveCreateRequest(
                LeaveType.ANNUAL, List.of(LocalDate.now().plusDays(1)), reason, null);
    }

    private String text(int length) {
        return "가".repeat(length);
    }
}
