package com.academique.backend.repository;

import com.academique.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdAndUserRoleOrderByCreatedAtDesc(Long userId, String userRole);
    List<Notification> findByUserIdInAndUserRoleOrderByCreatedAtDesc(Collection<Long> userIds, String userRole);
    long countByUserIdAndUserRoleAndIsReadFalse(Long userId, String userRole);
    long countByUserIdInAndUserRoleAndIsReadFalse(Collection<Long> userIds, String userRole);
}
