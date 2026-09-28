package io.github.repairticket.web;

import io.github.repairticket.domain.TicketStatus;
import io.github.repairticket.service.TicketService;
import io.github.repairticket.web.view.TicketSummary;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class TechnicianController {
    private final TicketService tickets;

    public TechnicianController(TicketService tickets) {
        this.tickets = tickets;
    }

    @GetMapping("/technician/jobs")
    public String jobs(Authentication authentication, Model model) {
        List<TicketSummary> assigned = tickets.technicianJobs(authentication.getName());
        model.addAttribute("tickets", assigned);
        model.addAttribute("activeCount", assigned.stream().filter(t -> t.status() != TicketStatus.CLOSED
                && t.status() != TicketStatus.RESOLVED).count());
        model.addAttribute("resolvedCount", assigned.stream().filter(t -> t.status() == TicketStatus.RESOLVED).count());
        return "technician/jobs";
    }

    @PostMapping("/technician/tickets/{ticketNumber}/status")
    public String updateStatus(@PathVariable String ticketNumber, @RequestParam TicketStatus status,
                               @RequestParam(required = false) String note,
                               Authentication authentication, RedirectAttributes redirect) {
        try {
            tickets.updateStatus(authentication.getName(), ticketNumber, status, note);
            redirect.addFlashAttribute("notice", "工单进度已更新。");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/tickets/" + ticketNumber;
    }
}
