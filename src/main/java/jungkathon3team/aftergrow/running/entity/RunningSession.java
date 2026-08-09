package jungkathon3team.aftergrow.running.entity;

import jakarta.persistence.*;
import jungkathon3team.aftergrow.auth.entity.User;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "running_sessions")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RunningSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "running_session_id")
    private UUID runningSessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "distance_km")
    private Double distanceKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "intensity", length = 20)
    private Intensity intensity;

    @Column(name = "uv_index_at_start")
    private Integer uvIndexAtStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RunningStatus status;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lng")
    private Double lng;

    public static RunningSession start(User user, LocalDateTime startedAt, Double lat, Double lng, Integer uvIndexAtStart) {
        return RunningSession.builder()
                .user(user)
                .startedAt(startedAt)
                .lat(lat)
                .lng(lng)
                .uvIndexAtStart(uvIndexAtStart)
                .status(RunningStatus.IN_PROGRESS)
                .build();
    }

    /**
     * 3.4 GET /running-sessions/{id}/live 폴링에서 클라이언트가 함께 보내는
     * 현재 거리/강도로 진행 중 스냅샷을 갱신한다.
     * (원 명세에는 별도 갱신 엔드포인트가 없어, GET 폴링에 upsert-on-read로 반영하는 방식으로 채운 지점)
     */
    public void updateLiveSnapshot(Double distanceKm, Intensity intensity) {
        if (this.status != RunningStatus.IN_PROGRESS) {
            return;
        }
        if (distanceKm != null) {
            this.distanceKm = distanceKm;
        }
        if (intensity != null) {
            this.intensity = intensity;
        }
    }

    public void end(LocalDateTime endedAt, Integer durationSec, Double distanceKm, Intensity intensity) {
        this.endedAt = endedAt;
        this.durationSec = durationSec;
        this.distanceKm = distanceKm;
        this.intensity = intensity;
        this.status = RunningStatus.ENDED;
    }

    public void complete() {
        this.status = RunningStatus.COMPLETED;
    }

    public boolean isInProgress() {
        return this.status == RunningStatus.IN_PROGRESS;
    }

    public boolean isOwnedBy(UUID userId) {
        return this.user.getUserId().equals(userId);
    }
}
