package com.mlsoft.backend.domain.email.repository;

import com.mlsoft.backend.domain.email.entity.LeaveReminderDispatch;
import com.mlsoft.backend.domain.email.entity.ReminderCycle;
import com.mlsoft.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

/** 연차 소진 안내 자동 발송 업무 이력 저장소. */
public interface LeaveReminderDispatchRepository extends JpaRepository<LeaveReminderDispatch, Long> {

    /** 같은 사원·주기·기간 키가 이미 선점됐는지 확인한다 */
    boolean existsByUserAndCycleAndPeriodKey(User user, ReminderCycle cycle, String periodKey);
}
