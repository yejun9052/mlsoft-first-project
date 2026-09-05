package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.config.AppProperties;
import com.mlsoft.backend.domain.email.event.EmailTemplateData;
import com.mlsoft.backend.domain.email.event.EmailTemplateKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 이메일 본문 조립 검증.
 *
 * <p>본문은 HTML 표다(2026-08-16). 태그 문자열을 단정하는 것은 보통 부서지기 쉬운 테스트지만,
 * 여기서는 <b>수신자가 실제로 보는 것이 이 문자열뿐</b>이고 렌더 결과를 확인할 다른 수단이 없다.
 * 그래서 레이아웃 세부(색·여백)는 건드리지 않고 <b>깨지면 메일이 망가지는 것</b>만 고정한다 —
 * 항목이 표 행으로 들어가는지, 값 없는 항목이 빈 행을 남기지 않는지, 사용자 입력이 태그로 새지 않는지.
 */
class EmailTemplateFactoryTest {

    private static final String FRONTEND_URL = "https://leave.example.com";

    /** 바로가기 버튼의 행선지는 수신자가 신청 당사자인지에 따라 갈린다 (2026-08-17) */
    private static final boolean AS_APPLICANT = true;
    private static final boolean AS_APPROVER = false;

    private final EmailTemplateFactory factory = factoryWith(FRONTEND_URL);

    private static EmailTemplateFactory factoryWith(String frontendUrl) {
        return new EmailTemplateFactory(
                new AppProperties("example.com", List.of(), frontendUrl, false));
    }

    @Test
    @DisplayName("본문은 HTML 표이고 항목이 라벨/값 한 행으로 들어간다")
    void create_rendersRowsAsTable() {
        EmailMessage message = factory.create(leaveData("연차", "김도현", "휴식"), true, AS_APPROVER);

        assertTrue(message.content().contains("<table"), "표가 없다");
        assertTrue(message.content().contains(">구분<"), "라벨이 표 셀로 들어가지 않았다");
        assertTrue(message.content().contains(">연차<"), "값이 표 셀로 들어가지 않았다");
        assertTrue(message.content().contains(">신청자<") && message.content().contains(">김도현<"));
    }

    @Test
    @DisplayName("값이 없는 항목은 행 자체를 만들지 않는다 — 빈 칸이 남으면 누락처럼 보인다")
    void create_omitsEmptyRows() {
        EmailMessage message = factory.create(leaveData("연차", "김도현", ""), true, AS_APPROVER);

        assertFalse(message.content().contains(">사유<"), "사유가 비었는데 행이 남았다");
    }

    @Test
    @DisplayName("사유 열람 권한이 없으면 값이 마스킹된다 (검증 Y-4)")
    void create_masksReasonWithoutPermission() {
        EmailMessage message = factory.create(leaveData("연차", "김도현", "개인 사정"), false, AS_APPROVER);

        assertTrue(message.content().contains(">사유<"), "사유 행은 있어야 한다");
        assertFalse(message.content().contains("개인 사정"), "권한이 없는데 사유가 그대로 나갔다");
    }

    // 이름·사유는 사용자가 적은 값이 그대로 들어간다. 이스케이프를 빼면 표가 깨지고
    // 메일 본문에 링크를 심을 수 있다.
    //
    // "<a href"가 본문에 없는지로 보면 안 된다 — 우리가 넣은 바로가기 버튼이 그 형태다.
    // 심으려는 **주소**가 살아 있는지를 본다.
    @Test
    @DisplayName("사용자 입력의 꺾쇠는 태그가 되지 않는다")
    void create_escapesUserInput() {
        EmailMessage message = factory.create(
                leaveData("연차", "김도현", "<a href=\"http://evil\">클릭</a>"), true, AS_APPROVER);

        assertFalse(message.content().contains("href=\"http://evil\""),
                "사용자가 심은 링크가 살아 있다");
        assertTrue(message.content().contains("&lt;a href"), "이스케이프된 형태가 없다");
    }

    @Test
    @DisplayName("결과 메일에는 처리자 행이 붙고, 신청 접수 메일에는 없다")
    void create_actorRowOnlyOnResult() {
        EmailTemplateData data = new EmailTemplateData(
                EmailTemplateKind.LEAVE_APPROVED, 1L, "김도현", "연차",
                "2026-08-20", "1.0", "휴식", "이서연");
        assertTrue(factory.create(data, true, AS_APPROVER).content().contains(">처리자<"));

        EmailTemplateData applied = new EmailTemplateData(
                EmailTemplateKind.LEAVE_APPLIED, 1L, "김도현", "연차",
                "2026-08-20", "1.0", "휴식", "");
        assertFalse(factory.create(applied, true, AS_APPROVER).content().contains(">처리자<"));
    }

    @Test
    @DisplayName("바로가기 버튼은 frontend-url을 오리진으로 쓴다 — 주소를 코드에 박지 않는다")
    void create_buttonUsesConfiguredOrigin() {
        EmailMessage message = factory.create(leaveData("연차", "김도현", "휴식"), true, AS_APPROVER);

        assertTrue(message.content().contains("href=\"" + FRONTEND_URL + "/approvals\""),
                "설정된 오리진으로 가는 링크가 없다");
        assertTrue(message.content().contains("결재하러 가기"));
    }

    // 한 통이 신청자·승인자·관리자에게 함께 나가지만 본문은 수신자별로 만든다.
    // 그래서 버튼은 그 수신자가 바로 하려는 일이 있는 화면으로 보낸다.
    @Test
    @DisplayName("결과 알림은 신청자를 자기 내역 화면으로 보낸다")
    void create_buttonTargetDependsOnKind() {
        EmailTemplateData approved = new EmailTemplateData(
                EmailTemplateKind.LEAVE_APPROVED, 1L, "김도현", "연차",
                "2026-08-20", "1.0", "휴식", "이서연");

        assertTrue(factory.create(approved, true, AS_APPLICANT).content()
                .contains(FRONTEND_URL + "/history"));
    }

    // 2026-08-17: 종류(kind)만 보고 행선지를 정해 **신청자에게도 "결재하러 가기"가 갔다.**
    // 자기 신청을 자기가 결재할 수는 없으므로, 눌러도 할 일이 없는 화면으로 데려가는 버튼이었다.
    @Test
    @DisplayName("접수 알림에서 신청자는 결재 화면으로 가지 않는다")
    void create_applicantNeverGetsApprovalButton() {
        String toApplicant = factory.create(leaveData("연차", "김도현", "휴식"), true, AS_APPLICANT)
                .content();

        assertFalse(toApplicant.contains("결재하러 가기"), "신청자에게 결재 버튼이 갔다");
        assertFalse(toApplicant.contains(FRONTEND_URL + "/approvals"), "신청자를 결재 화면으로 보냈다");
        assertTrue(toApplicant.contains(FRONTEND_URL + "/history"), "신청자를 자기 내역으로 보내지 않았다");
    }

    @Test
    @DisplayName("같은 접수 알림이라도 결재자에게는 결재 버튼이 간다")
    void create_approverStillGetsApprovalButton() {
        String toApprover = factory.create(leaveData("연차", "김도현", "휴식"), true, AS_APPROVER)
                .content();

        assertTrue(toApprover.contains("결재하러 가기"));
        assertTrue(toApprover.contains(FRONTEND_URL + "/approvals"));
    }

    // 복리후생 신청자를 연차 내역으로 보내면 자기 신청이 없는 화면이 열린다
    @Test
    @DisplayName("복리후생 신청자는 복리후생 화면으로 간다")
    void create_welfareApplicantGoesToWelfare() {
        EmailTemplateData welfareApplied = new EmailTemplateData(
                EmailTemplateKind.WELFARE_APPLIED, 1L, "김도현", "결혼",
                "", "5.0", "결혼", "");
        String content = factory.create(welfareApplied, true, AS_APPLICANT).content();

        assertTrue(content.contains(FRONTEND_URL + "/welfare"));
        assertFalse(content.contains(FRONTEND_URL + "/history"), "복리후생인데 연차 내역으로 보냈다");
    }

    // 라벨과 목적지를 따로 두면 "복리후생 내역"을 눌렀는데 연차 화면이 열린다
    @Test
    @DisplayName("버튼 라벨과 목적지가 함께 움직인다")
    void create_buttonLabelMatchesTarget() {
        EmailTemplateData welfareApproved = new EmailTemplateData(
                EmailTemplateKind.WELFARE_APPROVED, 1L, "김도현", "결혼",
                "", "5.0", "결혼", "이서연");
        String content = factory.create(welfareApproved, true, AS_APPLICANT).content();

        assertTrue(content.contains(FRONTEND_URL + "/welfare"));
        assertTrue(content.contains("복리후생 내역 확인하기"));
    }

    // 깨진 링크를 보내느니 버튼이 없는 편이 낫다
    @Test
    @DisplayName("frontend-url이 비면 버튼을 그리지 않는다")
    void create_omitsButtonWithoutOrigin() {
        EmailMessage message = factoryWith("")
                .create(leaveData("연차", "김도현", "휴식"), true, AS_APPROVER);

        assertFalse(message.content().contains("결재하러 가기"));
        assertTrue(message.content().contains("<table"), "버튼이 없어도 본문 표는 남아야 한다");
    }

    // 2026-08-16: 헤더 띠가 종류와 무관하게 "MLsoft 연차관리"라, 복리후생 메일을 열어도
    // 맨 위 큰 글자가 "연차"였다. 제목·헤더·버튼 어디에도 연차가 새면 안 된다.
    @Test
    @DisplayName("복리후생 메일에는 '연차'가 제목·헤더·버튼 어디에도 없다")
    void create_welfareMailNeverMentionsLeave() {
        EmailTemplateData welfare = new EmailTemplateData(
                EmailTemplateKind.WELFARE_APPLIED, 1L, "김도현", "결혼",
                "", "5.0", "결혼", "");
        EmailMessage message = factory.create(welfare, true, AS_APPROVER);

        assertTrue(message.title().startsWith("[복리후생 신청]"), "제목이 복리후생이 아니다");
        assertTrue(message.content().contains(">복리후생 신청<"), "헤더 띠가 복리후생이 아니다");

        // 푸터의 발신자 브랜드("MLsoft 연차관리")는 서비스 이름이라 예외다 — 그 한 곳을 걷어내고
        // 나머지 어디에도 연차가 없어야 한다. 헤더 띠가 브랜드로 되돌아가면 위 단정이 먼저 깨진다.
        String exceptSenderBrand = message.content().replace("MLsoft 연차관리", "");
        assertFalse(exceptSenderBrand.contains("연차"), "발신자 표기 말고 다른 곳에 연차가 남아 있다");
    }

    @Test
    @DisplayName("제목의 대괄호와 헤더 띠는 같은 값을 쓴다")
    void create_subjectAndHeadingShareOneSource() {
        EmailMessage message = factory.create(leaveData("연차", "김도현", "휴식"), true, AS_APPROVER);

        assertTrue(message.title().startsWith("[연차 신청] "));
        assertTrue(message.content().contains(">연차 신청<"));
    }

    @Test
    @DisplayName("반려 온보딩 메일은 초기화 전 입사일을 본문에 남긴다")
    void create_onboardingRejected_keepsHireDate() {
        EmailTemplateData rejected = new EmailTemplateData(
                EmailTemplateKind.ONBOARDING_REJECTED,
                null,
                "김도현",
                "반려",
                "1990-01-01",
                "",
                "",
                "");

        EmailMessage message = factory.create(rejected, true, AS_APPLICANT);

        assertTrue(message.title().startsWith("[온보딩 반려]"));
        assertTrue(message.content().contains("1990-01-01"));
    }

    @Test
    @DisplayName("수정 온보딩은 자동 승인 범위 안·밖 문구를 구분한다")
    void create_onboardingRevised_branchesBySummary() {
        EmailTemplateData completed = new EmailTemplateData(
                EmailTemplateKind.ONBOARDING_REVISED,
                null,
                "김도현",
                "수정 후 확정",
                "2026-08-20",
                "",
                "",
                "수정한 온보딩이 자동 승인 범위 안에서 확정되었습니다.");
        EmailTemplateData pending = new EmailTemplateData(
                EmailTemplateKind.ONBOARDING_REVISED,
                null,
                "김도현",
                "수정 후 승인 대기",
                "1990-01-01",
                "",
                "",
                "수정한 온보딩이 승인 대기 상태로 다시 접수되었습니다.");

        assertTrue(factory.create(completed, true, AS_APPROVER).content().contains("자동 승인 범위 안에서 확정"));
        assertTrue(factory.create(pending, true, AS_APPROVER).content().contains("승인 대기 상태로 다시"));
    }

    // ==== 헬퍼 ====

    private EmailTemplateData leaveData(String itemName, String applicant, String reason) {
        return new EmailTemplateData(
                EmailTemplateKind.LEAVE_APPLIED, 1L, applicant, itemName,
                "2026-08-20", "1.0", reason, "");
    }
}
