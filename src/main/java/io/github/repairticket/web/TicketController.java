package io.github.repairticket.web;

import io.github.repairticket.domain.AppUser;
import io.github.repairticket.domain.TicketStatus;
import io.github.repairticket.domain.UserRole;
import io.github.repairticket.service.TicketService;
import io.github.repairticket.web.form.CommentForm;
import io.github.repairticket.web.view.TicketDetailView;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class TicketController {
    private final TicketService tickets;

    public TicketController(TicketService tickets) {
        this.tickets = tickets;
    }

    @GetMapping("/tickets/{ticketNumber}")
    public String detail(@PathVariable String ticketNumber, Authentication authentication, Model model) {
        TicketDetailView detail = tickets.detail(authentication.getName(), ticketNumber);
        AppUser principal = authentication.getPrincipal() instanceof AppUser user ? user : null;
        model.addAttribute("detail", detail);
        model.addAttribute("ticket", detail.ticket());
        model.addAttribute("canComment", detail.ticket().status() != TicketStatus.CLOSED);
        model.addAttribute("canClose", principal != null && principal.getRole() == UserRole.CUSTOMER
                && detail.ticket().status() == TicketStatus.RESOLVED);
        model.addAttribute("canUpdate", principal != null && principal.getRole() == UserRole.TECHNICIAN
                && List.of(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS,
                TicketStatus.WAITING_FOR_CUSTOMER, TicketStatus.WAITING_FOR_PARTS).contains(detail.ticket().status()));
        model.addAttribute("nextStatuses", nextStatuses(detail.ticket().status()));
        if (!model.containsAttribute("commentForm")) model.addAttribute("commentForm", new CommentForm());
        return "tickets/detail";
    }

    @PostMapping("/tickets/{ticketNumber}/comments")
    public String comment(@PathVariable String ticketNumber,
                          @Valid @ModelAttribute("commentForm") CommentForm form,
                          BindingResult errors, Authentication authentication,
                          RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            redirect.addFlashAttribute("errorMessage", "留言不能为空，且不能超过 1500 个字符。");
            return "redirect:/tickets/" + ticketNumber;
        }
        try {
            tickets.addComment(authentication.getName(), ticketNumber, form.getBody());
            redirect.addFlashAttribute("notice", "留言已添加。");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/tickets/" + ticketNumber;
    }

    @PostMapping("/tickets/{ticketNumber}/confirm")
    public String confirm(@PathVariable String ticketNumber, Authentication authentication,
                          RedirectAttributes redirect) {
        try {
            tickets.updateStatus(authentication.getName(), ticketNumber, TicketStatus.CLOSED, "客户确认维修完成");
            redirect.addFlashAttribute("notice", "工单已完成，感谢你的确认。");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/tickets/" + ticketNumber;
    }

    private List<TicketStatus> nextStatuses(TicketStatus current) {
        return switch (current) {
            case ASSIGNED -> List.of(TicketStatus.IN_PROGRESS);
            case IN_PROGRESS -> List.of(TicketStatus.WAITING_FOR_CUSTOMER,
                    TicketStatus.WAITING_FOR_PARTS, TicketStatus.RESOLVED);
            case WAITING_FOR_CUSTOMER, WAITING_FOR_PARTS -> List.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED);
            default -> List.of();
        };
    }
}
