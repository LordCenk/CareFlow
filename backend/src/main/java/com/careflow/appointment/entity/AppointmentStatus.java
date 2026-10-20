package com.careflow.appointment.entity;

public enum AppointmentStatus {
    BOOKED,
    CHECKED_IN,
    IN_CONSULTATION,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    public boolean blocksSlot() {
        return this != CANCELLED;
    }
}
