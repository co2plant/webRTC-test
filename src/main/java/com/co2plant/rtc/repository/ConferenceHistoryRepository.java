package com.co2plant.rtc.repository;

import com.co2plant.rtc.domain.ConferenceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConferenceHistoryRepository extends JpaRepository<ConferenceHistory, Long> {
}
