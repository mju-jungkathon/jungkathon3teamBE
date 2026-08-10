package jungkathon3team.aftergrow.profile.repository;

import jungkathon3team.aftergrow.profile.entity.IntegrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IntegrationStatusRepository extends JpaRepository<IntegrationStatus, UUID> {
}
