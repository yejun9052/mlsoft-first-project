package com.mlsoft.backend.schema;

import com.mlsoft.backend.domain.audit.entity.AdminAction;
import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.email.entity.EmailStatus;
import com.mlsoft.backend.domain.email.entity.EmailType;
import com.mlsoft.backend.domain.email.entity.ReminderCycle;
import com.mlsoft.backend.domain.email.entity.ReminderDispatchResult;
import com.mlsoft.backend.domain.leave.entity.LeaveType;
import com.mlsoft.backend.domain.schedule.entity.ScheduleType;
import com.mlsoft.backend.domain.user.entity.OnboardingStatus;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.welfare.entity.WelfareTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MySQL ENUM 컬럼과 Java enum이 서로 다른 값으로 배포되는 것을 막는다.
 *
 * <p>H2의 create-drop은 엔티티에서 스키마를 새로 만들기 때문에 이 검사는
 * 애플리케이션 컨텍스트만으로는 잡히지 않는 운영 스키마 누락을 직접 확인한다.
 * 매핑 목록은 현재 schema.sql의 모든 ENUM 컬럼을 명시한다.</p>
 */
class SchemaEnumConsistencyTest {

    private static final List<EnumMapping> ENUM_MAPPINGS = List.of(
            new EnumMapping("admin_audit_log", "action", AdminAction.class),
            new EnumMapping("email_history", "email_type", EmailType.class),
            new EnumMapping("email_history", "status", EmailStatus.class),
            new EnumMapping("leave_action_history", "action", RequestAction.class),
            new EnumMapping("leave_reminder_dispatch", "cycle", ReminderCycle.class),
            new EnumMapping("leave_reminder_dispatch", "result", ReminderDispatchResult.class),
            new EnumMapping("leave_requests", "leave_type", LeaveType.class),
            new EnumMapping("leave_requests", "status", RequestStatus.class),
            new EnumMapping("schedule_entries", "schedule_type", ScheduleType.class),
            new EnumMapping("users", "onboarding_status", OnboardingStatus.class),
            new EnumMapping("users", "role", Role.class),
            new EnumMapping("welfare_action_history", "action", RequestAction.class),
            new EnumMapping("welfare_policies", "target", WelfareTarget.class),
            new EnumMapping("welfare_requests", "status", RequestStatus.class),
            new EnumMapping("welfare_requests", "target", WelfareTarget.class));

    @Test
    @DisplayName("schema.sql의 모든 ENUM 값이 대응 Java enum과 일치한다")
    void schema의모든Enum값이JavaEnum과일치한다() throws IOException {
        String schema = Files.readString(findSchemaPath(), StandardCharsets.UTF_8);

        for (EnumMapping mapping : ENUM_MAPPINGS) {
            Set<String> schemaValues = parseSchemaValues(schema, mapping);
            Set<String> javaValues = Arrays.stream(mapping.enumType().getEnumConstants())
                    .map(Enum::name)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

            assertEquals(javaValues, schemaValues,
                    () -> mapping.tableName() + "." + mapping.columnName()
                            + " ENUM과 " + mapping.enumType().getSimpleName() + " 값이 다릅니다");
        }
    }

    private static Path findSchemaPath() {
        List<Path> candidates = List.of(
                Path.of("db", "schema.sql"),
                Path.of("..", "db", "schema.sql"));
        return candidates.stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new AssertionError("db/schema.sql을 찾을 수 없습니다"));
    }

    private static Set<String> parseSchemaValues(String schema, EnumMapping mapping) {
        String tablePattern = "(?is)CREATE TABLE\\s+"
                + Pattern.quote("`" + mapping.tableName() + "`")
                + "\\s*\\((.*?)\\)\\s*ENGINE";
        Matcher tableMatcher = Pattern.compile(tablePattern).matcher(schema);
        assertTrue(tableMatcher.find(), () -> mapping.tableName() + " 테이블을 schema.sql에서 찾을 수 없습니다");

        String columnPattern = "(?i)"
                + Pattern.quote("`" + mapping.columnName() + "`")
                + "\\s+enum\\(([^)]*)\\)";
        Matcher columnMatcher = Pattern.compile(columnPattern).matcher(tableMatcher.group(1));
        assertTrue(columnMatcher.find(), () -> mapping.tableName() + "." + mapping.columnName()
                + " ENUM 컬럼을 schema.sql에서 찾을 수 없습니다");

        Matcher valueMatcher = Pattern.compile("'([^']*)'").matcher(columnMatcher.group(1));
        Set<String> values = new LinkedHashSet<>();
        while (valueMatcher.find()) {
            values.add(valueMatcher.group(1));
        }
        return values;
    }

    private record EnumMapping(String tableName, String columnName,
                               Class<? extends Enum<?>> enumType) {
    }
}
