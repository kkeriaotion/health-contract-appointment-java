package com.example.health;

public final class AppointmentWorkflowTest {
    public static void main(String[] args) {
        AppointmentWorkflow workflow = new AppointmentWorkflow();
        var input = new AppointmentWorkflow.Appointment("apt-test", "learner@example.org", AppointmentWorkflow.Status.CONFIRMED);
        var result = workflow.prepareForSignature(input);
        if (result.status() != AppointmentWorkflow.Status.READY_FOR_SIGNATURE) throw new AssertionError("status decision");
        if (!workflow.notification(result).contains("apt-test")) throw new AssertionError("notification");
        System.out.println("AppointmentWorkflowTest passed");
    }
}
