package org.classq.domain.notification.repository;

import org.classq.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("SELECT n FROM Notification n JOIN FETCH n.course WHERE n.student.id = :studentId ORDER BY n.createdAt DESC")
    List<Notification> findByStudentIdWithCourse(@Param("studentId") Long studentId);
}
