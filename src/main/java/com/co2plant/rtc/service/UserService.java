package com.co2plant.rtc.service;

import com.co2plant.rtc.domain.User;
import com.co2plant.rtc.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public Long register(String username, String password, String email) {
        // TODO: Password encoding needed
        User user = User.builder()
                .username(username)
                .password(password)
                .email(email)
                .build();
        
        return userRepository.save(user).getId();
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }
}
