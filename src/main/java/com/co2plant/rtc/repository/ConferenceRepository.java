package com.co2plant.rtc.repository;

import com.co2plant.rtc.domain.Conference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConferenceRepository extends JpaRepository<Conference, Long> {
}
