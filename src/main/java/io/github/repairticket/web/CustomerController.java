package io.github.repairticket.web;

import io.github.repairticket.domain.TicketStatus;
import io.github.repairticket.service.TicketService;
import io.github.repairticket.web.form.NewTicketForm;
import io.github.repairticket.web.view.TicketSummary;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class CustomerController {
    private static final List<String> CATEGORIES = List.of("水电维修", "门窗维修", "家电维修", "公共设施", "其他");
    private final TicketService tickets;

    public CustomerController(TicketService tickets) {
        this.tickets = tickets;
    }

    @GetMapping("/customer/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        List<TicketSummary> mine = tickets.customerTickets(authentication.getName());
        model.addAttribute("tickets", mine);
        model.addAttribute("totalCount", mine.size());
        model.addAttribute("activeCount", mine.stream().filter(t -> t.status() != TicketStatus.CLOSED).count());
        model.addAttribute("completedCount", mine.stream().filter(t -> t.status() == TicketStatus.CLOSED).count());
        return "customer/dashboard";
    }

    @GetMapping("/customer/tickets/new")
    public String newTicket(Model model) {
        if (!model.containsAttribute("form")) model.addAttribute("form", new NewTicketForm());
        model.addAttribute("categories", CATEGORIES);
        return "customer/new-ticket";
    }

    @PostMapping("/customer/tickets")
    public String create(@Valid @ModelAttribute("form") NewTicketForm form, BindingResult errors,
                         Authentication authentication, Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            model.addAttribute("categories", CATEGORIES);
            return "customer/new-ticket";
        }
        try {
            TicketSummary ticket = tickets.create(authentication.getName(), form);
            redirect.addFlashAttribute("notice", "工单已提交，客服会尽快分派维修人员。");
            return "redirect:/tickets/" + ticket.ticketNumber();
        } catch (IllegalArgumentException exception) {
            errors.rejectValue("category", "invalid", exception.getMessage());
            model.addAttribute("categories", CATEGORIES);
            return "customer/new-ticket";
        }
    }
}
