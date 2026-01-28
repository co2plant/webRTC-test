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
        Long userId = userService.register(request.getUsername(), request.getPassword(), request.getEmail());
        return ResponseEntity.ok(userId);
    }

    @GetMapping("/{username}")
    public ResponseEntity<UserDto> getUser(@PathVariable String username) {
        User user = userService.findByUsername(username);
        return ResponseEntity.ok(new UserDto(user));
    }

    @Data
    static class RegisterRequest {
        private String username;
        private String password;
        private String email;
    }

    @Data
    static class UserDto {
        private Long id;
        private String username;
        private String email;

        public UserDto(User user) {
            this.id = user.getId();
            this.username = user.getUsername();
            this.email = user.getEmail();
        }
    }
}
