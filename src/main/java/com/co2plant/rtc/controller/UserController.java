package com.co2plant.rtc.controller;

import com.co2plant.rtc.domain.User;
import com.co2plant.rtc.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<Long> register(@RequestBody RegisterRequest request) {
        Long id = userService.register(
                request.getUserId(),
                request.getPassword(),
                request.getName(),
                request.getDepartment(),
                request.getPosition(),
                request.getEmail()
        );
        return ResponseEntity.ok(id);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserDto> getUser(@PathVariable String userId) {
        User user = userService.findByUserId(userId);
        return ResponseEntity.ok(new UserDto(user));
    }

    @Data
    static class RegisterRequest {
        private String userId;
        private String password;
        private String name;
        private String department;
        private String position;
        private String email;
    }

    @Data
    static class UserDto {
        private Long id;
        private String userId;
        private String name;
        private String department;
        private String position;
        private String email;

        public UserDto(User user) {
            this.id = user.getId();
            this.userId = user.getUserId();
            this.name = user.getName();
            this.department = user.getDepartment();
            this.position = user.getPosition();
            this.email = user.getEmail();
        }
    }
}
