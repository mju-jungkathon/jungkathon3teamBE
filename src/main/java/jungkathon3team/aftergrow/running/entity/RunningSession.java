package jungkathon3team.aftergrow.running.entity;

import jakarta.persistence.*;
import jungkathon3team.aftergrow.auth.entity.User;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * 러닝 중 수집한 GPS 트랙. 종료 시점에 배열 통째로 한 번 저장한다(러닝 중에는 전송하지 않는다).
     * <p>경로를 안 보낸 클라이언트도 있으므로 null이 될 수 있다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "route_path")
    private List<RoutePoint> routePath;

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

    public void end(LocalDateTime endedAt, Integer durationSec, Double distanceKm, Intensity intensity,
                    List<RoutePoint> routePath) {
        this.endedAt = endedAt;
        this.durationSec = durationSec;
        this.distanceKm = distanceKm;
        this.intensity = intensity;
        this.status = RunningStatus.ENDED;
        // 경로를 보내지 않은 클라이언트가 기존에 저장된 경로를 지우지 않도록 null이면 건드리지 않는다.
        if (routePath != null) {
            this.routePath = routePath;
        }
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
