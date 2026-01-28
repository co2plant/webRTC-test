package com.co2plant.rtc.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, name = "user_id")
    private String userId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column
    private String department;

    @Column
    private String position;

    // Optional email, can keep it or remove if strictly following diagram. Kept for utility.
    @Column
    private String email;

    @Builder
    public User(String userId, String password, String name, String department, String position, String email) {
        this.userId = userId;
        this.password = password;
        this.name = name;
        this.department = department;
        this.position = position;
        this.email = email;
    }
}
