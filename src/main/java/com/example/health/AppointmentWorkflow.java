package com.example.health;

public final class AppointmentWorkflow {
    public enum Status { CONFIRMED, READY_FOR_SIGNATURE }

    public record Appointment(String id, String patientEmail, Status status) {}

    public Appointment prepareForSignature(Appointment appointment) {
        if (appointment.patientEmail() == null || appointment.patientEmail().isBlank()) {
            throw new IllegalArgumentException("patient email is required");
        }
        if (appointment.status() != Status.CONFIRMED) {
            throw new IllegalStateException("appointment must be confirmed");
        }
        return new Appointment(appointment.id(), appointment.patientEmail(), Status.READY_FOR_SIGNATURE);
    }

    public String notification(Appointment appointment) {
        return "Your appointment " + appointment.id() + " has a contract ready for review.";
    }
}
