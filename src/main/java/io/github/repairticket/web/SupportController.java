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
public class SupportController {
    private final TicketService tickets;

    public SupportController(TicketService tickets) {
        this.tickets = tickets;
    }

    @GetMapping("/support/queue")
    public String queue(Authentication authentication, Model model) {
        List<TicketSummary> queue = tickets.supportQueue(authentication.getName());
        model.addAttribute("tickets", queue);
        model.addAttribute("technicians", tickets.availableTechnicians(authentication.getName()));
        model.addAttribute("newCount", queue.stream().filter(t -> t.status() == TicketStatus.SUBMITTED).count());
        model.addAttribute("activeCount", queue.stream().filter(t -> t.status() != TicketStatus.CLOSED
                && t.status() != TicketStatus.RESOLVED).count());
        model.addAttribute("resolvedCount", queue.stream().filter(t -> t.status() == TicketStatus.RESOLVED).count());
        return "support/queue";
    }

    @PostMapping("/support/tickets/{ticketNumber}/assign")
    public String assign(@PathVariable String ticketNumber, @RequestParam Long technicianId,
                         Authentication authentication, RedirectAttributes redirect) {
        try {
            tickets.assign(authentication.getName(), ticketNumber, technicianId);
            redirect.addFlashAttribute("notice", "工单已分派给维修人员。");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/support/queue";
    }
}
