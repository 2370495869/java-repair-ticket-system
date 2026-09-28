package io.github.repairticket.repository;

import io.github.repairticket.domain.TicketStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketStatusHistoryRepository extends JpaRepository<TicketStatusHistory, Long> {
    List<TicketStatusHistory> findAllByTicket_IdOrderByChangedAtAscIdAsc(Long ticketId);
}
