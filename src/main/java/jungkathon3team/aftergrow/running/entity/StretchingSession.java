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
@Table(name = "stretching_sessions")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StretchingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "stretching_session_id")
    private UUID stretchingSessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 50)
    private StretchingType type;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    public static StretchingSession start(User user, StretchingType type) {
        return StretchingSession.builder()
                .user(user)
                .type(type)
                .startedAt(LocalDateTime.now())
                .build();
    }
}
