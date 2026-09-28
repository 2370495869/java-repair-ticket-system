package io.github.repairticket.web.view;

import io.github.repairticket.domain.TicketStatus;

public record TicketSummary(
        String ticketNumber,
        String title,
        String category,
        String description,
        String contactName,
        String contactPhone,
        String serviceAddress,
        String customerDisplay,
        String technicianName,
        TicketStatus status,
        String createdAt,
        String updatedAt) {
}
