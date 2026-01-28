package com.co2plant.rtc.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "conference_history")
public class ConferenceHistory {

    public enum Action {
        CREATE, JOIN, EXIT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conference_id", nullable = false)
    private Conference conference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.ORDINAL) // SmallInteger in diagram, so index (0, 1, 2) is appropriate or String
    @Column(nullable = false)
    private Action action;

    @CreatedDate
    @Column(name = "inserted_time", updatable = false)
    private LocalDateTime insertedTime;

    @Builder
    public ConferenceHistory(Conference conference, User user, Action action) {
        this.conference = conference;
        this.user = user;
        this.action = action;
    }
}
