package io.github.repairticket.repository;

import io.github.repairticket.domain.RepairTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RepairTicketRepository extends JpaRepository<RepairTicket, Long> {
    Optional<RepairTicket> findByTicketNumber(String ticketNumber);

    List<RepairTicket> findAllByOrderByUpdatedAtDesc();

    List<RepairTicket> findAllByCustomer_IdOrderByUpdatedAtDesc(Long customerId);

    List<RepairTicket> findAllByAssignedTechnician_IdOrderByUpdatedAtDesc(Long technicianId);
}
