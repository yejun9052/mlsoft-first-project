package com.mlsoft.backend.security;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.leave.entity.LeaveType;
import com.mlsoft.backend.domain.leave.repository.LeaveRequestRepository;
import com.mlsoft.backend.domain.schedule.entity.ScheduleEntry;
import com.mlsoft.backend.domain.schedule.entity.ScheduleType;
import com.mlsoft.backend.domain.schedule.repository.ScheduleEntryRepository;
import com.mlsoft.backend.domain.user.entity.OnboardingStatus;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.domain.welfare.entity.WelfarePolicy;
import com.mlsoft.backend.domain.welfare.entity.WelfareRequest;
import com.mlsoft.backend.domain.welfare.entity.WelfareTarget;
import com.mlsoft.backend.domain.welfare.repository.WelfarePolicyRepository;
import com.mlsoft.backend.domain.welfare.repository.WelfareRequestRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
// Spring Boot 4에서 위치가 바뀌었다 — 예전 org.springframework.boot.test.autoconfigure.web.servlet 이 아니다
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 권한 게이트 통합 검증 (리뷰 T-1) — 이 시스템에서 <b>한 번도 검증된 적이 없던</b> 영역이다.
 *
 * <p>{@code @WebMvcTest}가 아니라 {@code @SpringBootTest + MockMvc}인 이유: 검증 대상이
 * <b>네 겹의 실행 순서</b>다 — SecurityFilterChain → {@link JwtFilter} → 인터셉터 →
 * {@code @PreAuthorize} → 서비스 소유권 검사. 인터셉터가 매 요청 DB를 조회하므로 슬라이스 테스트로는
 * 그 순서를 재현할 수 없다.
 *
 * <p>인증은 <b>실제 JWT 쿠키</b>로 넣는다. {@code @WithMockUser}를 쓰면 {@link JwtFilter}를 건너뛰어
 * 정작 검증하려는 토큰 파싱·DB 재조회가 빠진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityAccessMatrixTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LeaveRequestRepository leaveRequestRepository;
    @Autowired
    private WelfarePolicyRepository welfarePolicyRepository;
    @Autowired
    private WelfareRequestRepository welfareRequestRepository;
    @Autowired
    private ScheduleEntryRepository scheduleEntryRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    // ==================== 인증 ====================

    @Test
    @DisplayName("미인증 요청은 401 + 공통 실패 응답 형식")
    void 미인증_401() throws Exception {
        // 깨지면: 로그인하지 않은 외부인이 사내 부서·연차 정보를 그대로 조회한다
        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    @DisplayName("만료된 JWT는 401")
    void 만료토큰_401() throws Exception {
        // 깨지면: 만료된 토큰이 계속 유효한 세션처럼 통한다
        User employee = saveUser("expired", Role.EMPLOYEE, true, true);
        String expired = new JwtProvider(jwtSecret, -1L)
                .createToken(employee.getId(), employee.getEmail(), employee.getRole());

        mockMvc.perform(get("/api/departments").cookie(tokenCookie(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("서명이 다른 JWT는 401")
    void 위조토큰_401() throws Exception {
        // 깨지면: 서명 검증이 무력화돼 누구나 토큰을 만들어 낼 수 있다
        User employee = saveUser("forged", Role.EMPLOYEE, true, true);
        String forged = new JwtProvider("forged-secret-key-that-is-long-enough-0123456789", 86400000L)
                .createToken(employee.getId(), employee.getEmail(), Role.SYSTEM_ADMIN);

        mockMvc.perform(get("/api/users").cookie(tokenCookie(forged)))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 역할 게이트 + DB 신선도 ====================

    @Test
    @DisplayName("EMPLOYEE가 SYSTEM_ADMIN 전용 API를 호출하면 403")
    void 관리자API_일반사원_403() throws Exception {
        // 깨지면: 일반 사원이 전 직원 명단·연차를 조회한다
        User employee = saveUser("employee", Role.EMPLOYEE, true, true);

        mockMvc.perform(get("/api/users").cookie(tokenCookie(employee)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("토큰은 관리자 · DB는 강등 → 같은 요청부터 403")
    void 강등_즉시반영_403() throws Exception {
        // 이 시스템의 특징적인 동작이다. 깨지면: 강등된 사람이 토큰 만료(24h)까지 관리자로 남는다.
        // OnboardingCheckInterceptor가 매 요청 DB role로 SecurityContext를 재구성하는 것이 근거다
        User user = saveUser("demoted", Role.SYSTEM_ADMIN, true, true);
        String adminToken = jwtProvider.createToken(user.getId(), user.getEmail(), Role.SYSTEM_ADMIN);

        user.changeRole(Role.EMPLOYEE);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/users").cookie(tokenCookie(adminToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("토큰은 사원 · DB는 승격 → 같은 요청부터 허용")
    void 승격_즉시반영_허용() throws Exception {
        // 깨지면: 승격된 사람이 재로그인 전까지 관리자 업무를 못 한다 (강등의 반대 방향)
        User user = saveUser("promoted", Role.EMPLOYEE, true, true);
        String employeeToken = jwtProvider.createToken(user.getId(), user.getEmail(), Role.EMPLOYEE);

        user.changeRole(Role.SYSTEM_ADMIN);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/users").cookie(tokenCookie(employeeToken)))
                .andExpect(status().isOk());
    }

    // ==================== 계정 상태 ====================

    @Test
    @DisplayName("퇴직자는 로그아웃만 허용 — 만료 쿠키를 돌려준다")
    void 퇴직자_로그아웃만_허용() throws Exception {
        // 깨지면: 퇴직자가 브라우저에 남은 HttpOnly 쿠키를 스스로 지울 방법이 없다
        User retired = saveUser("retired-logout", Role.EMPLOYEE, true, true);
        String token = jwtProvider.createToken(retired.getId(), retired.getEmail(), Role.EMPLOYEE);
        retired.retire(LocalDate.now());
        userRepository.saveAndFlush(retired);

        mockMvc.perform(post("/api/auth/logout").cookie(tokenCookie(token)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString(JwtProvider.TOKEN_COOKIE_NAME + "=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    @DisplayName("퇴직자는 로그아웃 외 전 경로 차단 — RETIRED_USER는 401이다")
    void 퇴직자_그외_401() throws Exception {
        // 깨지면: 퇴직자가 토큰 만료(24h)까지 사내 데이터를 계속 본다.
        // 상태코드가 403이 아니라 401인 것은 ErrorCode.RETIRED_USER 정의 그대로다 —
        // 프론트 인터셉터가 401에서 로그인으로 보내므로 퇴직자는 자연히 로그아웃된다
        User retired = saveUser("retired-blocked", Role.EMPLOYEE, true, true);
        String token = jwtProvider.createToken(retired.getId(), retired.getEmail(), Role.EMPLOYEE);
        retired.retire(LocalDate.now());
        userRepository.saveAndFlush(retired);

        mockMvc.perform(get("/api/auth/me").cookie(tokenCookie(token)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/departments").cookie(tokenCookie(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("온보딩 미완료자는 /api/auth/* 만 허용")
    void 온보딩미완료_인증경로만_허용() throws Exception {
        // 깨지면: 입사일이 없는 계정이 연차를 신청한다 (검증 Y-2)
        User user = saveUser("not-onboarded", Role.EMPLOYEE, true, false);

        mockMvc.perform(get("/api/auth/me").cookie(tokenCookie(user)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/leaves/me").cookie(tokenCookie(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("온보딩 승인 대기자도 차단된다 — 미시작과 다른 메시지를 준다 (S-1)")
    void 온보딩_승인대기_차단() throws Exception {
        // 깨지면: 자가 신고한 과거 입사일로 연차를 받은 계정이 그대로 시스템을 쓴다.
        // 메시지를 구분하지 않으면 대기자가 온보딩을 다시 내려다 ALREADY_ONBOARDED만 맞는다
        User user = saveUser("pending-approval", Role.EMPLOYEE, true, false);
        user.requestOnboardingApproval(LocalDate.of(1990, 1, 1), LocalDate.of(1990, 1, 1));
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/leaves/me").cookie(tokenCookie(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("관리자")));
    }

    // ==================== 조회 상한 (리뷰 S-4) ====================

    @Test
    @DisplayName("페이지 크기 상한 초과는 400 — 조용히 깎지 않는다")
    void 페이지크기초과_400() throws Exception {
        // 깨지면: size=100000 요청 하나로 전 사원을 한 번에 끌어올 수 있다.
        // 조용히 100으로 깎으면 클라이언트가 왜 100건만 오는지 알 수 없다 —
        // PolicyConfigKey가 "20.9"를 20으로 깎는 대신 거부하는 것과 같은 기준이다
        User admin = saveUser("size-admin", Role.SYSTEM_ADMIN, true, true);

        mockMvc.perform(get("/api/users").param("size", "100000").cookie(tokenCookie(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // 상한 이내는 그대로 통과한다
        mockMvc.perform(get("/api/users").param("size", "50").cookie(tokenCookie(admin)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("팀 현황은 조회 기간이 1년을 넘으면 400 — 페이징이 없어 기간이 곧 상한이다")
    void 팀현황_기간초과_400() throws Exception {
        // 깨지면: from=1900-01-01으로 그 부서의 전체 이력을 한 번에 끌어올 수 있다.
        // 이 엔드포인트는 List를 통째로 돌려주므로 size 상한이 걸리지 않는다
        User member = saveUser("team-range", Role.EMPLOYEE, true, true);

        mockMvc.perform(get("/api/leaves/team")
                        .param("from", "1900-01-01")
                        .param("to", LocalDate.now().toString())
                        .cookie(tokenCookie(member)))
                .andExpect(status().isBadRequest());
    }

    // ==================== 객체 단위 권한 (서비스 계층) ====================

    @Test
    @DisplayName("승인자가 아닌 팀장은 남의 연차·복리후생을 승인할 수 없다")
    void 승인자아닌팀장_403() throws Exception {
        // 깨지면: 다른 팀 팀장이 신청 ID를 추측해 임의로 승인한다.
        // @PreAuthorize는 역할만 보므로(TL 통과) 이 방어는 전적으로 서비스 계층 몫이다
        User outsider = saveUser("outsider-leader", Role.TEAM_LEADER, true, true);
        User approver = saveUser("real-approver", Role.TEAM_LEADER, true, true);
        User applicant = saveUser("applicant", Role.EMPLOYEE, true, true);

        LeaveRequest leave = leaveRequestRepository.save(LeaveRequest.create(
                applicant, LeaveType.ANNUAL, List.of(futureDate(10)), "휴식", approver, null));
        WelfareRequest welfare = welfareRequestRepository.save(WelfareRequest.create(
                savedPolicy(), applicant, "경조사", approver.getId(), null));

        String body = """
                { "approved": true, "comment": "권한 검증" }
                """;
        mockMvc.perform(post("/api/leaves/{id}/approval", leave.getId())
                        .cookie(tokenCookie(outsider))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/welfare-requests/{id}/approval", welfare.getId())
                        .cookie(tokenCookie(outsider))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("타인의 연차·복리후생·일정에는 손댈 수 없다")
    void 타인리소스_403() throws Exception {
        // 깨지면: 인증만 되면 ID를 바꿔가며 남의 연차를 취소하고 일정을 지운다.
        // 이 엔드포인트들에는 @PreAuthorize가 없다 — CLAUDE.md의 "소유권은 서비스 책임" 약속이
        // 실제로 지켜지는지 확인하는 것이 이 테스트다
        User owner = saveUser("owner", Role.EMPLOYEE, true, true);
        User attacker = saveUser("attacker", Role.EMPLOYEE, true, true);
        User approver = saveUser("owner-approver", Role.TEAM_LEADER, true, true);

        LeaveRequest leave = leaveRequestRepository.save(LeaveRequest.create(
                owner, LeaveType.ANNUAL, List.of(futureDate(20)), "휴식", approver, null));
        WelfareRequest welfare = welfareRequestRepository.save(WelfareRequest.create(
                savedPolicy(), owner, "경조사", approver.getId(), null));
        ScheduleEntry schedule = scheduleEntryRepository.save(ScheduleEntry.create(
                owner, ScheduleType.FIELD_WORK, List.of(futureDate(30)), "외근"));

        mockMvc.perform(post("/api/leaves/{id}/cancel", leave.getId())
                        .cookie(tokenCookie(attacker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "타인 연차 취소 시도" }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/welfare-requests/{id}/cancel", welfare.getId())
                        .cookie(tokenCookie(attacker)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/schedules/{id}", schedule.getId())
                        .cookie(tokenCookie(attacker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "scheduleType": "BUSINESS_TRIP", "dates": ["%s"], "memo": "수정 시도" }
                                """.formatted(futureDate(31))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/schedules/{id}", schedule.getId())
                        .cookie(tokenCookie(attacker)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/leaves/{id}/histories", leave.getId())
                        .cookie(tokenCookie(attacker)))
                .andExpect(status().isForbidden());
    }

    // ==================== 헬퍼 ====================

    /**
     * @param onboarded 온보딩 확정 여부 — 판별 기준은 {@code hire_date}가 아니라 상태다 (리뷰 S-1)
     */
    private User saveUser(String name, Role role, boolean active, boolean onboarded) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                // 시연 데이터 시더 테스트가 @mlsoft.com 계정을 자기 것으로 세지 않게 도메인을 분리한다
                // (같은 H2를 공유하므로 실행 순서에 따라 개수 단언이 흔들렸다)
                .email(name + "-" + System.nanoTime() + "@integration.test")
                .role(role)
                .onboardingStatus(onboarded ? OnboardingStatus.COMPLETED : OnboardingStatus.NOT_STARTED)
                .hireDate(onboarded ? LocalDate.now().minusYears(1) : null)
                .birthDay(onboarded ? LocalDate.of(1990, 1, 1) : null)
                .lastResetDate(onboarded ? LocalDate.now().minusDays(1) : null)
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(active)
                .department((Department) null)
                .build());
    }

    private WelfarePolicy savedPolicy() {
        return welfarePolicyRepository.save(WelfarePolicy.create(
                "보안테스트-" + System.nanoTime(), WelfareTarget.SELF,
                new BigDecimal("1.0"), "증빙", "권한 검증용"));
    }

    private Cookie tokenCookie(User user) {
        return tokenCookie(jwtProvider.createToken(user.getId(), user.getEmail(), user.getRole()));
    }

    private Cookie tokenCookie(String token) {
        return new Cookie(JwtProvider.TOKEN_COOKIE_NAME, token);
    }

    private LocalDate futureDate(int plusDays) {
        return LocalDate.now().plusDays(plusDays);
    }
}
