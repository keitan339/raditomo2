package com.raditomo.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettings {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "current_area_id", nullable = false, length = 8)
    private String currentAreaId;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
