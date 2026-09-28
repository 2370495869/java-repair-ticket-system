package io.github.repairticket.web;

import io.github.repairticket.service.AccountService;
import io.github.repairticket.web.form.RegistrationForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;

@Controller
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        if (!model.containsAttribute("form")) model.addAttribute("form", new RegistrationForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form,
                           BindingResult errors, RedirectAttributes redirect) {
        if (!errors.hasFieldErrors("passwordConfirmation")
                && form.getPassword() != null && !form.getPassword().equals(form.getPasswordConfirmation())) {
            errors.rejectValue("passwordConfirmation", "mismatch", "两次输入的密码不一致");
        }
        if (!errors.hasFieldErrors("email") && accounts.emailInUse(form.getEmail())) {
            errors.rejectValue("email", "duplicate", "这个邮箱已注册，请直接登录");
        }
        if (!errors.hasFieldErrors("password")
                && form.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            errors.rejectValue("password", "tooLong", "密码不能超过 72 个 UTF-8 字节");
        }
        if (errors.hasErrors()) return "register";
        accounts.registerCustomer(form);
        redirect.addFlashAttribute("registrationComplete", true);
        return "redirect:/login";
    }
}
