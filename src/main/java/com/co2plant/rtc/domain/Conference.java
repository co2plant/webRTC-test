package com.co2plant.rtc.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "conference")
public class Conference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conference_category_id", nullable = false)
    private ConferenceCategory conferenceCategory;

    @Column(name = "call_start_time")
    private LocalDateTime callStartTime;

    @Column(name = "call_end_time")
    private LocalDateTime callEndTime;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Builder
    public Conference(User owner, ConferenceCategory conferenceCategory, LocalDateTime callStartTime, LocalDateTime callEndTime, String thumbnailUrl, String title, String description, Boolean isActive) {
        this.owner = owner;
        this.conferenceCategory = conferenceCategory;
        this.callStartTime = callStartTime;
        this.callEndTime = callEndTime;
        this.thumbnailUrl = thumbnailUrl;
        this.title = title;
        this.description = description;
        this.isActive = isActive != null ? isActive : true;
    }
}
