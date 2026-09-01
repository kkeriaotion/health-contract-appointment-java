package com.example.health;

public final class ContractWorkflowExample {
    public static void main(String[] args) throws Exception {
        AppointmentWorkflow workflow = new AppointmentWorkflow();
        var appointment = workflow.prepareForSignature(new AppointmentWorkflow.Appointment("apt-204", "patient@example.org", AppointmentWorkflow.Status.CONFIRMED));
        String markdown = "# Appointment agreement\n\nAppointment: " + appointment.id() + "\nPlease review before signing.";
        String envelope = new InfraiPdfClient().generateContract(markdown);
        System.out.println(appointment.status() + " | " + workflow.notification(appointment));
        System.out.println("Infrai envelope: " + envelope);
    }
}
