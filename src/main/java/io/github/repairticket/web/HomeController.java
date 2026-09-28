package io.github.repairticket.web;

import io.github.repairticket.domain.AppUser;
import io.github.repairticket.domain.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class HomeController {
    @GetMapping("/")
    public String home(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUser user)) {
            return "redirect:/login";
        }
        return switch (user.getRole()) {
            case CUSTOMER -> "redirect:/customer/dashboard";
            case SUPPORT -> "redirect:/support/queue";
            case TECHNICIAN -> "redirect:/technician/jobs";
        };
    }

    @GetMapping("/health")
    @ResponseBody
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
