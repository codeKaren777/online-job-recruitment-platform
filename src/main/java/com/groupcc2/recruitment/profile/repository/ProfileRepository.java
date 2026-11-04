package com.groupcc2.recruitment.profile.repository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.groupcc2.recruitment.profile.entity.Profile;
public interface ProfileRepository extends JpaRepository<Profile,UUID> {
 java.util.List<Profile> findAllByUserIdIn(java.util.Collection<UUID> userIds);
}
