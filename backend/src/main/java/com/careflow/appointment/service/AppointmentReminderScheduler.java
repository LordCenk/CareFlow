package com.careflow.appointment.service;

import com.careflow.appointment.entity.Appointment;
import com.careflow.appointment.entity.AppointmentStatus;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class AppointmentReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderScheduler.class);

    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;
    private final int reminderHoursAhead;

    public AppointmentReminderScheduler(
            AppointmentRepository appointmentRepository,
            NotificationService notificationService,
            @Value("${careflow.scheduling.reminder-hours-ahead:24}") int reminderHoursAhead
    ) {
        this.appointmentRepository = appointmentRepository;
        this.notificationService = notificationService;
        this.reminderHoursAhead = reminderHoursAhead;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void scanAndScheduleUpcomingReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowEnd = now.plusHours(reminderHoursAhead);

        log.debug("Scanning appointments scheduled between {} and {} for 24h reminders", now, windowEnd);
        List<Appointment> upcoming = appointmentRepository.findUpcomingAppointments(AppointmentStatus.BOOKED, now, windowEnd);

        for (Appointment appointment : upcoming) {
            notificationService.scheduleReminder(appointment, now);
        }
    }
}
