package io.github.repairticket;

import io.github.repairticket.domain.AppUser;
import io.github.repairticket.domain.RepairTicket;
import io.github.repairticket.domain.TicketComment;
import io.github.repairticket.domain.TicketStatus;
import io.github.repairticket.domain.TicketStatusHistory;
import io.github.repairticket.domain.UserRole;
import io.github.repairticket.repository.AppUserRepository;
import io.github.repairticket.repository.RepairTicketRepository;
import io.github.repairticket.repository.TicketCommentRepository;
import io.github.repairticket.repository.TicketStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketWorkflowIntegrationTest {
    private static final String TEST_PHONE = "19900000000";
    private static final String TEST_ADDRESS = "测试地址 3 幢 102 室";

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired RepairTicketRepository tickets;
    @Autowired TicketCommentRepository comments;
    @Autowired TicketStatusHistoryRepository history;
    @Autowired PasswordEncoder passwordEncoder;

    private AppUser customer;
    private AppUser anotherCustomer;
    private AppUser support;
    private AppUser technician;

    @BeforeEach
    void setUp() {
        comments.deleteAll();
        history.deleteAll();
        tickets.deleteAll();
        users.deleteAll();
        customer = saveUser("customer", "测试客户", UserRole.CUSTOMER);
        anotherCustomer = saveUser("other", "另一位客户", UserRole.CUSTOMER);
        support = saveUser("support", "测试客服", UserRole.SUPPORT);
        technician = saveUser("technician", "测试维修员", UserRole.TECHNICIAN);
    }

    @Test
    void registrationCreatesOnlyCustomerAndPostRequestsRequireCsrf() throws Exception {
        String email = "new-" + UUID.randomUUID() + "@example.test";
        String testPassword = "test-only-" + UUID.randomUUID();
        var request = post("/register")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", email)
                .param("fullName", "新客户")
                .param("phone", TEST_PHONE)
                .param("password", testPassword)
                .param("passwordConfirmation", testPassword);

        mvc.perform(request).andExpect(status().isForbidden());
        mvc.perform(post("/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", email)
                        .param("fullName", "新客户")
                        .param("phone", TEST_PHONE)
                        .param("password", testPassword)
                        .param("passwordConfirmation", testPassword))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        AppUser saved = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(saved.getPassword()).isNotEqualTo(testPassword);
        assertThat(saved.getPassword()).startsWith("$2a$");
    }

    @Test
    void customerCannotOpenAnotherCustomersTicket() throws Exception {
        RepairTicket ticket = createTicket(customer);

        mvc.perform(get("/tickets/{number}", ticket.getTicketNumber()).with(user(anotherCustomer)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/tickets/{number}", ticket.getTicketNumber()).with(user(technician)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/support/queue").with(user(customer)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/support/tickets/{number}/assign", ticket.getTicketNumber())
                        .with(user(customer)).with(csrf()).param("technicianId", technician.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void completeWorkflowStoresCommentsAndEveryStatusTransition() throws Exception {
        mvc.perform(post("/customer/tickets")
                        .with(user(customer)).with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("title", "水槽下方有漏水")
                        .param("category", "水电维修")
                        .param("description", "发现接口处有水滴，需要检查密封情况。")
                        .param("contactName", "测试客户")
                        .param("contactPhone", TEST_PHONE)
                        .param("serviceAddress", TEST_ADDRESS))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/tickets/RT-*"));

        RepairTicket ticket = tickets.findAll().getFirst();
        String number = ticket.getTicketNumber();
        mvc.perform(post("/support/tickets/{number}/assign", number)
                        .with(user(support)).with(csrf())
                        .param("technicianId", technician.getId().toString()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/technician/tickets/{number}/status", number)
                        .with(user(technician)).with(csrf())
                        .param("status", "IN_PROGRESS").param("note", "已联系客户，准备上门检查。"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/tickets/{number}/comments", number)
                        .with(user(technician)).with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("body", "上门前已与客户确认时间。"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/technician/tickets/{number}/status", number)
                        .with(user(technician)).with(csrf())
                        .param("status", "RESOLVED").param("note", "更换接口垫片，现场测试正常。"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/tickets/{number}/confirm", number)
                        .with(user(customer)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        RepairTicket saved = tickets.findByTicketNumber(number).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(history.findAllByTicket_IdOrderByChangedAtAscIdAsc(saved.getId()))
                .extracting(TicketStatusHistory::getToStatus)
                .containsExactly(TicketStatus.SUBMITTED, TicketStatus.ASSIGNED,
                        TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CLOSED);
        assertThat(comments.findAllByTicket_IdOrderByCreatedAtAscIdAsc(saved.getId()))
                .extracting(TicketComment::getBody)
                .containsExactly("上门前已与客户确认时间。");
    }

    @Test
    void contactInformationIsMaskedForSupportAndVisibleToAssignedTechnician() throws Exception {
        RepairTicket ticket = createTicket(customer);
        ticket.assignTo(technician);
        tickets.save(ticket);

        mvc.perform(get("/tickets/{number}", ticket.getTicketNumber()).with(user(support)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(TEST_PHONE))))
                .andExpect(content().string(not(containsString(TEST_ADDRESS))));
        mvc.perform(get("/tickets/{number}", ticket.getTicketNumber()).with(user(technician)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(TEST_PHONE)))
                .andExpect(content().string(containsString(TEST_ADDRESS)));
    }

    private AppUser saveUser(String prefix, String name, UserRole role) {
        String email = prefix + "-" + UUID.randomUUID() + "@example.test";
        String randomHash = passwordEncoder.encode("test-only-" + UUID.randomUUID());
        return users.save(new AppUser(email, randomHash, name, TEST_PHONE, role));
    }

    private RepairTicket createTicket(AppUser owner) {
        return tickets.save(new RepairTicket("RT-" + UUID.randomUUID().toString().substring(0, 10),
                owner, "水槽下方有漏水", "水电维修", "发现接口处有水滴，需要检查密封情况。",
                "测试客户", TEST_PHONE, TEST_ADDRESS));
    }
}
