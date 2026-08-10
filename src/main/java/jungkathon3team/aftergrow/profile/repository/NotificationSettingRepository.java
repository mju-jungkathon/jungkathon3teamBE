package jungkathon3team.aftergrow.profile.repository;

import jungkathon3team.aftergrow.profile.entity.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, UUID> {
}
