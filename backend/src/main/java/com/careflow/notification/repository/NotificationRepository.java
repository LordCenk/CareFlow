package com.careflow.notification.repository;

import com.careflow.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByStatusAndScheduledForLessThanEqual(String status, LocalDateTime threshold);
    List<Notification> findByAppointmentId(Long appointmentId);
    boolean existsByAppointmentIdAndNotificationType(Long appointmentId, String notificationType);
}
