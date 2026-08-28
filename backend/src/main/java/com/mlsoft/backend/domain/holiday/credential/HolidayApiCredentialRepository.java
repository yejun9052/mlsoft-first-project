package com.mlsoft.backend.domain.holiday.credential;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HolidayApiCredentialRepository extends JpaRepository<HolidayApiCredential, Long> {

    Optional<HolidayApiCredential> findByProviderAndActiveTrue(String provider);

    Optional<HolidayApiCredential> findByProvider(String provider);
}
