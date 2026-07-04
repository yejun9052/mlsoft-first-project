package com.mlsoft.backend.config;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.department.repository.DepartmentRepository;
import com.mlsoft.backend.domain.policy.entity.LeavePolicy;
import com.mlsoft.backend.domain.policy.entity.LeavePolicyConfig;
import com.mlsoft.backend.domain.policy.repository.LeavePolicyConfigRepository;
import com.mlsoft.backend.domain.policy.repository.LeavePolicyRepository;
import com.mlsoft.backend.domain.welfare.entity.WelfarePolicy;
import com.mlsoft.backend.domain.welfare.entity.WelfareTarget;
import com.mlsoft.backend.domain.welfare.repository.WelfarePolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 초기 데이터 적재 — 이미 데이터가 있으면 스킵 (재기동 안전).
 * 1) 연차 정책 1~21년차: MIN(15 + (년차-1)/2, 25) (근로기준법 §60)
 * 2) 연차 시스템 설정 3키 (docs/02 3-11)
 * 3) 복리후생 초기 정책 (docs/02 3-6 주석)
 * 4) 미배정 부서 (신규 가입 기본 소속)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    /** 미배정 부서명 */
    public static final String UNASSIGNED_DEPARTMENT_NAME = "미배정";

    // 연차 시스템 설정 키
    public static final String CONFIG_ADVANCE_LEAVE_ENABLED = "advance_leave_enabled";
    public static final String CONFIG_REMINDER_LIST_DAYS = "reminder_list_days";
    public static final String CONFIG_REMINDER_AUTO_CYCLE = "reminder_auto_cycle";

    private final LeavePolicyRepository leavePolicyRepository;
    private final LeavePolicyConfigRepository leavePolicyConfigRepository;
    private final WelfarePolicyRepository welfarePolicyRepository;
    private final DepartmentRepository departmentRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initLeavePolicies();
        initLeavePolicyConfigs();
        initWelfarePolicies();
        initDefaultDepartment();
    }

    /**
     * 근속년수별 연차 정책 초기화 (1~21년차).
     * - 21년차가 상한 25일에 도달하므로 21년차 정책을 그 이상 근속에도 적용한다.
     */
    private void initLeavePolicies() {
        if (leavePolicyRepository.count() > 0) {
            return;
        }
        for (int year = 1; year <= 21; year++) {
            int days = Math.min(15 + (year - 1) / 2, 25);
            leavePolicyRepository.save(LeavePolicy.create(
                    year,
                    BigDecimal.valueOf(days).setScale(1),
                    year + "년차 연차 " + days + "일 (근로기준법 기준)"
            ));
        }
        log.info("[DataInitializer] 연차 정책 21건 초기화 완료");
    }

    /**
     * 연차 시스템 설정 초기화 — 키 단위로 존재 여부 확인 후 없는 것만 추가.
     */
    private void initLeavePolicyConfigs() {
        insertConfigIfAbsent(CONFIG_ADVANCE_LEAVE_ENABLED, "false"); // 당겨쓰기 허용 여부
        insertConfigIfAbsent(CONFIG_REMINDER_LIST_DAYS, "30");       // 소진 안내 리스트 표시 기준일
        insertConfigIfAbsent(CONFIG_REMINDER_AUTO_CYCLE, "NONE");    // 자동 발송 주기 (NONE/D30/D60/D90/QUARTER)
    }

    private void insertConfigIfAbsent(String name, String value) {
        if (leavePolicyConfigRepository.findByName(name).isEmpty()) {
            leavePolicyConfigRepository.save(LeavePolicyConfig.create(name, value));
            log.info("[DataInitializer] 설정 초기화: {} = {}", name, value);
        }
    }

    /**
     * 복리후생 초기 정책 (docs/02 3-6 주석 — 이전 프로젝트 초기값 계승).
     */
    private void initWelfarePolicies() {
        if (welfarePolicyRepository.count() > 0) {
            return;
        }
        List<WelfarePolicy> policies = List.of(
                // 결혼
                WelfarePolicy.create("결혼", WelfareTarget.SELF, days("7"), "청첩장 또는 혼인관계증명서", "본인 결혼"),
                WelfarePolicy.create("결혼", WelfareTarget.SIBLING, days("1"), "청첩장 및 가족관계증명서", "형제·자매 결혼"),
                // 회갑·칠순
                WelfarePolicy.create("회갑", WelfareTarget.PARENT, days("1"), "가족관계증명서", "부모 회갑(만 60세)"),
                WelfarePolicy.create("칠순", WelfareTarget.PARENT, days("1"), "가족관계증명서", "부모 칠순(만 70세)"),
                // 출산
                WelfarePolicy.create("출산", WelfareTarget.SPOUSE, days("5"), "출생증명서", "배우자 출산"),
                WelfarePolicy.create("출산", WelfareTarget.SELF, days("90"), "출생증명서", "본인 출산 (출산전후휴가)"),
                // 졸업
                WelfarePolicy.create("졸업", WelfareTarget.SELF, days("1"), "졸업증명서", "본인 졸업"),
                // 조의 — 7일 그룹
                WelfarePolicy.create("조의", WelfareTarget.PARENT, days("7"), "부고장 또는 가족관계증명서", "부모 조의"),
                WelfarePolicy.create("조의", WelfareTarget.SPOUSE, days("7"), "부고장 또는 가족관계증명서", "배우자 조의"),
                WelfarePolicy.create("조의", WelfareTarget.CHILD, days("7"), "부고장 또는 가족관계증명서", "자녀 조의"),
                WelfarePolicy.create("조의", WelfareTarget.SPOUSE_PARENT, days("7"), "부고장 또는 가족관계증명서", "배우자 부모 조의"),
                // 조의 — 3일 그룹
                WelfarePolicy.create("조의", WelfareTarget.SIBLING, days("3"), "부고장 또는 가족관계증명서", "형제·자매 조의"),
                WelfarePolicy.create("조의", WelfareTarget.GRANDPARENT, days("3"), "부고장 또는 가족관계증명서", "조부모 조의"),
                WelfarePolicy.create("조의", WelfareTarget.SPOUSE_GRANDPARENT, days("3"), "부고장 또는 가족관계증명서", "배우자 조부모 조의")
        );
        welfarePolicyRepository.saveAll(policies);
        log.info("[DataInitializer] 복리후생 정책 {}건 초기화 완료", policies.size());
    }

    /**
     * 미배정 부서 초기화 — 첫 로그인 자동 가입 사원의 기본 소속.
     * 팀장이 없으므로 승인자는 SYSTEM_ADMIN fallback (검증 Y-3).
     */
    private void initDefaultDepartment() {
        if (departmentRepository.existsByName(UNASSIGNED_DEPARTMENT_NAME)) {
            return;
        }
        departmentRepository.save(Department.create(
                UNASSIGNED_DEPARTMENT_NAME,
                "부서 배정 전 사원의 기본 소속",
                null
        ));
        log.info("[DataInitializer] 미배정 부서 초기화 완료");
    }

    // BigDecimal 일수 리터럴 헬퍼
    private BigDecimal days(String value) {
        return new BigDecimal(value).setScale(1);
    }
}
