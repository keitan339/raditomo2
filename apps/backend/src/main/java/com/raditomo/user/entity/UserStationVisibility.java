package com.raditomo.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "user_station_visibility")
@IdClass(UserStationVisibility.Pk.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStationVisibility {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "station_id", length = 32)
    private String stationId;

    @Column(name = "is_visible", nullable = false)
    private boolean visible;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pk implements Serializable {
        private Long userId;
        private String stationId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(userId, pk.userId) && Objects.equals(stationId, pk.stationId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, stationId);
        }
    }
}
