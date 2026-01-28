package com.co2plant.rtc.repository;

import com.co2plant.rtc.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ConferenceSchemaTest {

    @Autowired private UserRepository userRepository;
    @Autowired private ConferenceRepository conferenceRepository;
    @Autowired private ConferenceCategoryRepository categoryRepository;
    @Autowired private ConferenceHistoryRepository historyRepository;

    @Test
    @DisplayName("Verify full flow: User -> Category -> Conference -> History")
    void testFullConferenceFlow() {
        // 1. Create User
        User owner = User.builder()
                .userId("owner-001")
                .password("secret")
                .name("Alice")
                .department("Engineering")
                .position("Team Lead")
                .email("alice@company.com")
                .build();
        userRepository.save(owner);

        // 2. Create Category
        ConferenceCategory category = new ConferenceCategory("Tech Talk");
        categoryRepository.save(category);

        // 3. Create Conference
        LocalDateTime now = LocalDateTime.now();
        Conference conference = Conference.builder()
                .owner(owner)
                .conferenceCategory(category)
                .title("Weekly Tech Sync")
                .description("Discussion about WebRTC")
                .callStartTime(now.plusHours(1))
                .callEndTime(now.plusHours(2))
                .isActive(true)
                .build();
        conferenceRepository.save(conference);

        // Verify Conference
        assertThat(conference.getId()).isNotNull();
        assertThat(conference.getOwner().getUserId()).isEqualTo("owner-001");
        assertThat(conference.getConferenceCategory().getName()).isEqualTo("Tech Talk");

        // 4. Create History (User JOINs)
        ConferenceHistory history = ConferenceHistory.builder()
                .conference(conference)
                .user(owner)
                .action(ConferenceHistory.Action.JOIN)
                .build();
        historyRepository.save(history);

        // Verify History
        assertThat(history.getId()).isNotNull();
        assertThat(history.getInsertedTime()).isNotNull(); // Check Auditing
        assertThat(history.getAction()).isEqualTo(ConferenceHistory.Action.JOIN);
    }
}
