package com.co2plant.rtc.repository;

import com.co2plant.rtc.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void saveAndFindUser() {
        // given
        User user = User.builder()
                .userId("testuser")
                .password("password")
                .name("Test User")
                .department("IT")
                .position("Developer")
                .email("test@example.com")
                .build();

        // when
        userRepository.save(user);

        // then
        User foundUser = userRepository.findByUserId("testuser").orElse(null);
        assertThat(foundUser).isNotNull();
        assertThat(foundUser.getUserId()).isEqualTo("testuser");
        assertThat(foundUser.getName()).isEqualTo("Test User");
        assertThat(foundUser.getEmail()).isEqualTo("test@example.com");
    }
}
