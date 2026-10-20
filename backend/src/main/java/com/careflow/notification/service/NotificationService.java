package com.careflow.notification.service;

import com.careflow.appointment.entity.Appointment;
import com.careflow.notification.entity.Notification;
import com.careflow.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void sendBookingConfirmation(Appointment appointment) {
        String patientEmail = extractPatientEmail(appointment);
        String patientName = extractPatientName(appointment);
        String doctorName = extractDoctorName(appointment);

        String subject = "Appointment Confirmation - CareFlow [" + appointment.getAppointmentReference() + "]";
        String message = String.format(
                "Dear %s,\n\nYour appointment with %s is confirmed for %s.\nAppointment Reference: %s\n\nThank you,\nCareFlow Hospital",
                patientName, doctorName, appointment.getStartDateTime(), appointment.getAppointmentReference()
        );

        Notification notification = new Notification(
                patientEmail, patientName, "APPOINTMENT_BOOKED", subject, message, appointment.getId(), LocalDateTime.now()
        );
        notification.setStatus("SENT");
        notification.setSentAt(Instant.now());
        notificationRepository.save(notification);

        log.info("Simulated dispatch: Booking confirmation sent to {} for appointment {}", patientEmail, appointment.getAppointmentReference());
    }

    @Transactional
    public void sendCancellationNotice(Appointment appointment, String reason) {
        String patientEmail = extractPatientEmail(appointment);
        String patientName = extractPatientName(appointment);

        String subject = "Appointment Cancelled - CareFlow [" + appointment.getAppointmentReference() + "]";
        String message = String.format(
                "Dear %s,\n\nYour appointment with reference %s scheduled for %s has been cancelled.\nReason: %s\n\nCareFlow Hospital",
                patientName, appointment.getAppointmentReference(), appointment.getStartDateTime(), reason
        );

        Notification notification = new Notification(
                patientEmail, patientName, "APPOINTMENT_CANCELLED", subject, message, appointment.getId(), LocalDateTime.now()
        );
        notification.setStatus("SENT");
        notification.setSentAt(Instant.now());
        notificationRepository.save(notification);

        log.info("Simulated dispatch: Cancellation notice sent to {} for appointment {}", patientEmail, appointment.getAppointmentReference());
    }

    @Transactional
    public void sendRescheduleNotice(Appointment appointment) {
        String patientEmail = extractPatientEmail(appointment);
        String patientName = extractPatientName(appointment);
        String doctorName = extractDoctorName(appointment);

        String subject = "Appointment Rescheduled - CareFlow [" + appointment.getAppointmentReference() + "]";
        String message = String.format(
                "Dear %s,\n\nYour appointment has been rescheduled with %s to %s.\nReference: %s\n\nCareFlow Hospital",
                patientName, doctorName, appointment.getStartDateTime(), appointment.getAppointmentReference()
        );

        Notification notification = new Notification(
                patientEmail, patientName, "APPOINTMENT_RESCHEDULED", subject, message, appointment.getId(), LocalDateTime.now()
        );
        notification.setStatus("SENT");
        notification.setSentAt(Instant.now());
        notificationRepository.save(notification);

        log.info("Simulated dispatch: Reschedule notice sent to {} for appointment {}", patientEmail, appointment.getAppointmentReference());
    }

    @Transactional
    public void scheduleReminder(Appointment appointment, LocalDateTime scheduledFor) {
        if (notificationRepository.existsByAppointmentIdAndNotificationType(appointment.getId(), "APPOINTMENT_REMINDER")) {
            return;
        }

        String patientEmail = extractPatientEmail(appointment);
        String patientName = extractPatientName(appointment);
        String doctorName = extractDoctorName(appointment);

        String subject = "Upcoming Appointment Reminder - CareFlow";
        String message = String.format(
                "Dear %s,\n\nReminder: You have an upcoming appointment with %s at %s.\nReference: %s\n\nCareFlow Hospital",
                patientName, doctorName, appointment.getStartDateTime(), appointment.getAppointmentReference()
        );

        Notification notification = new Notification(
                patientEmail, patientName, "APPOINTMENT_REMINDER", subject, message, appointment.getId(), scheduledFor
        );
        notificationRepository.save(notification);
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void dispatchPendingNotifications() {
        List<Notification> pending = notificationRepository.findByStatusAndScheduledForLessThanEqual("PENDING", LocalDateTime.now());
        for (Notification notification : pending) {
            log.info("Dispatching scheduled reminder notification ID {} to {}", notification.getId(), notification.getRecipientEmail());
            notification.setStatus("SENT");
            notification.setSentAt(Instant.now());
        }
    }

    private String extractPatientEmail(Appointment appointment) {
        if (appointment.getPatient() != null && appointment.getPatient().getUser() != null) {
            return appointment.getPatient().getUser().getEmail();
        }
        return "patient@careflow.com";
    }

    private String extractPatientName(Appointment appointment) {
        if (appointment.getPatient() != null && appointment.getPatient().getUser() != null) {
            return appointment.getPatient().getUser().getName();
        }
        return "Patient";
    }

    private String extractDoctorName(Appointment appointment) {
        if (appointment.getDoctor() != null && appointment.getDoctor().getUser() != null) {
            return appointment.getDoctor().getUser().getName();
        }
        return "Doctor";
    }
}
