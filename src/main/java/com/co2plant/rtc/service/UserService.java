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
    public Long register(String userId, String password, String name, String department, String position, String email) {
        // TODO: Password encoding needed
        User user = User.builder()
                .userId(userId)
                .password(password)
                .name(name)
                .department(department)
                .position(position)
                .email(email)
                .build();
        
        return userRepository.save(user).getId();
    }

    public User findByUserId(String userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }
}
