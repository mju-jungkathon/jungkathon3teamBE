package jungkathon3team.aftergrow.profile.repository;

import jungkathon3team.aftergrow.profile.entity.UserGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserGoalRepository extends JpaRepository<UserGoal, UUID> {
}
