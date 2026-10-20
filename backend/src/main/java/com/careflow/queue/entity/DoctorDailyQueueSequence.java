package com.careflow.queue.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "doctor_daily_queue_sequence")
public class DoctorDailyQueueSequence {

    @EmbeddedId
    private DoctorDailyQueueSequenceId id;

    @Column(name = "last_queue_number", nullable = false)
    private int lastQueueNumber = 0;

    public DoctorDailyQueueSequence() {
    }

    public DoctorDailyQueueSequence(Long doctorId, LocalDate queueDate, int lastQueueNumber) {
        this.id = new DoctorDailyQueueSequenceId(doctorId, queueDate);
        this.lastQueueNumber = lastQueueNumber;
    }

    public DoctorDailyQueueSequenceId getId() {
        return id;
    }

    public void setId(DoctorDailyQueueSequenceId id) {
        this.id = id;
    }

    public int getLastQueueNumber() {
        return lastQueueNumber;
    }

    public void setLastQueueNumber(int lastQueueNumber) {
        this.lastQueueNumber = lastQueueNumber;
    }

    @Embeddable
    public static class DoctorDailyQueueSequenceId implements Serializable {
        @Column(name = "doctor_id", nullable = false)
        private Long doctorId;

        @Column(name = "queue_date", nullable = false)
        private LocalDate queueDate;

        public DoctorDailyQueueSequenceId() {
        }

        public DoctorDailyQueueSequenceId(Long doctorId, LocalDate queueDate) {
            this.doctorId = doctorId;
            this.queueDate = queueDate;
        }

        public Long getDoctorId() {
            return doctorId;
        }

        public void setDoctorId(Long doctorId) {
            this.doctorId = doctorId;
        }

        public LocalDate getQueueDate() {
            return queueDate;
        }

        public void setQueueDate(LocalDate queueDate) {
            this.queueDate = queueDate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DoctorDailyQueueSequenceId that = (DoctorDailyQueueSequenceId) o;
            return Objects.equals(doctorId, that.doctorId) && Objects.equals(queueDate, that.queueDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(doctorId, queueDate);
        }
    }
}
