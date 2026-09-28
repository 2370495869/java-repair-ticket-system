package io.github.repairticket.web.view;

import java.util.List;

public record TicketDetailView(TicketSummary ticket, List<CommentView> comments, List<HistoryView> history) {
}
