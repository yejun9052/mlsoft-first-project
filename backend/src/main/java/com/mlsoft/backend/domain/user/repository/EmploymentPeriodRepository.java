package com.mlsoft.backend.domain.user.repository;

import com.mlsoft.backend.domain.user.entity.EmploymentPeriod;
import com.mlsoft.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 종료된 근속 구간 저장소. */
public interface EmploymentPeriodRepository extends JpaRepository<EmploymentPeriod, Long> {

    /** 다음 구간 번호 계산을 위한 사원별 마지막 구간 조회. */
    Optional<EmploymentPeriod> findTopByUserOrderBySeqDesc(User user);

    /** 본인 근속 구간을 순번 오름차순으로 조회한다. */
    List<EmploymentPeriod> findByUserOrderBySeqAsc(User user);
}
