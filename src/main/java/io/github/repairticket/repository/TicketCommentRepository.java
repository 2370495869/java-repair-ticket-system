package io.github.repairticket.repository;

import io.github.repairticket.domain.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {
    List<TicketComment> findAllByTicket_IdOrderByCreatedAtAscIdAsc(Long ticketId);
}
