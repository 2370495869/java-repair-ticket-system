package io.github.repairticket.service;

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
import io.github.repairticket.web.form.NewTicketForm;
import io.github.repairticket.web.view.CommentView;
import io.github.repairticket.web.view.HistoryView;
import io.github.repairticket.web.view.TechnicianOption;
import io.github.repairticket.web.view.TicketDetailView;
import io.github.repairticket.web.view.TicketSummary;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class TicketService {
    private static final Set<String> CATEGORIES = Set.of("水电维修", "门窗维修", "家电维修", "公共设施", "其他");
    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final AppUserRepository users;
    private final RepairTicketRepository tickets;
    private final TicketCommentRepository comments;
    private final TicketStatusHistoryRepository history;

    public TicketService(AppUserRepository users, RepairTicketRepository tickets,
                         TicketCommentRepository comments, TicketStatusHistoryRepository history) {
        this.users = users;
        this.tickets = tickets;
        this.comments = comments;
        this.history = history;
    }

    @Transactional
    public TicketSummary create(String email, NewTicketForm form) {
        AppUser customer = requireActor(email);
        requireRole(customer, UserRole.CUSTOMER);
        if (!CATEGORIES.contains(form.getCategory())) {
            throw new IllegalArgumentException("请选择有效的报修类型");
        }
        String number = "RT-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 10).toUpperCase(Locale.ROOT);
        RepairTicket ticket = tickets.save(new RepairTicket(number, customer,
                form.getTitle().trim(), form.getCategory(), form.getDescription().trim(),
                form.getContactName().trim(), form.getContactPhone().trim(), form.getServiceAddress().trim()));
        history.save(new TicketStatusHistory(ticket, null, TicketStatus.SUBMITTED, customer, "客户提交工单"));
        return summary(ticket, customer);
    }

    @Transactional(readOnly = true)
    public List<TicketSummary> customerTickets(String email) {
        AppUser actor = requireActor(email);
        requireRole(actor, UserRole.CUSTOMER);
        return tickets.findAllByCustomer_IdOrderByUpdatedAtDesc(actor.getId()).stream()
                .map(ticket -> summary(ticket, actor)).toList();
    }

    @Transactional(readOnly = true)
    public List<TicketSummary> supportQueue(String email) {
        AppUser actor = requireActor(email);
        requireRole(actor, UserRole.SUPPORT);
        return tickets.findAllByOrderByUpdatedAtDesc().stream().map(ticket -> summary(ticket, actor)).toList();
    }

    @Transactional(readOnly = true)
    public List<TicketSummary> technicianJobs(String email) {
        AppUser actor = requireActor(email);
        requireRole(actor, UserRole.TECHNICIAN);
        return tickets.findAllByAssignedTechnician_IdOrderByUpdatedAtDesc(actor.getId()).stream()
                .map(ticket -> summary(ticket, actor)).toList();
    }

    @Transactional(readOnly = true)
    public List<TechnicianOption> availableTechnicians(String email) {
        AppUser actor = requireActor(email);
        requireRole(actor, UserRole.SUPPORT);
        return users.findAllByRoleOrderByFullNameAsc(UserRole.TECHNICIAN).stream()
                .map(user -> new TechnicianOption(user.getId(), user.getFullName())).toList();
    }

    @Transactional(readOnly = true)
    public TicketDetailView detail(String email, String ticketNumber) {
        AppUser actor = requireActor(email);
        RepairTicket ticket = findTicket(ticketNumber);
        requireCanView(actor, ticket);
        List<CommentView> commentViews = comments.findAllByTicket_IdOrderByCreatedAtAscIdAsc(ticket.getId()).stream()
                .map(comment -> new CommentView(commentAuthorName(comment.getAuthor(), actor),
                        comment.getAuthor().getRole(), comment.getBody(), format(comment.getCreatedAt())))
                .toList();
        List<HistoryView> historyViews = history.findAllByTicket_IdOrderByChangedAtAscIdAsc(ticket.getId()).stream()
                .map(change -> new HistoryView(
                        change.getFromStatus() == null ? "新工单" : change.getFromStatus().getLabel(),
                        change.getToStatus().getLabel(), historyActorName(change.getActor(), actor),
                        change.getNote(), format(change.getChangedAt())))
                .toList();
        return new TicketDetailView(summary(ticket, actor), commentViews, historyViews);
    }

    @Transactional
    public void assign(String email, String ticketNumber, Long technicianId) {
        AppUser support = requireActor(email);
        requireRole(support, UserRole.SUPPORT);
        RepairTicket ticket = findTicket(ticketNumber);
        if (ticket.getStatus() != TicketStatus.SUBMITTED) {
            throw new IllegalArgumentException("只有待分派工单可以分派");
        }
        AppUser technician = users.findById(technicianId)
                .filter(user -> user.getRole() == UserRole.TECHNICIAN)
                .orElseThrow(() -> new IllegalArgumentException("请选择有效的维修人员"));
        ticket.assignTo(technician);
        history.save(new TicketStatusHistory(ticket, TicketStatus.SUBMITTED,
                TicketStatus.ASSIGNED, support, "客服已分派维修人员"));
    }

    @Transactional
    public void addComment(String email, String ticketNumber, String body) {
        AppUser actor = requireActor(email);
        RepairTicket ticket = findTicket(ticketNumber);
        requireCanView(actor, ticket);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new IllegalArgumentException("已完成工单不能继续留言");
        }
        comments.save(new TicketComment(ticket, actor, body.trim()));
    }

    @Transactional
    public void updateStatus(String email, String ticketNumber, TicketStatus next, String note) {
        AppUser actor = requireActor(email);
        RepairTicket ticket = findTicket(ticketNumber);
        TicketStatus previous = ticket.getStatus();

        if (next == TicketStatus.CLOSED) {
            requireRole(actor, UserRole.CUSTOMER);
            if (!ticket.getCustomer().getId().equals(actor.getId()) || previous != TicketStatus.RESOLVED) {
                throw new IllegalArgumentException("只能确认自己已解决的工单");
            }
        } else {
            requireRole(actor, UserRole.TECHNICIAN);
            if (ticket.getAssignedTechnician() == null
                    || !ticket.getAssignedTechnician().getId().equals(actor.getId())) {
                throw new AccessDeniedException("只能更新分派给自己的工单");
            }
            if (!allowedTechnicianTransitions(previous).contains(next)) {
                throw new IllegalArgumentException("该工单不能从“" + previous.getLabel()
                        + "”流转到“" + next.getLabel() + "”");
            }
        }

        String detail = note == null ? "" : note.trim();
        if (detail.length() > 500) {
            throw new IllegalArgumentException("进度说明不能超过 500 个字符");
        }
        ticket.changeStatus(next);
        String message = detail.isEmpty() ? "状态更新为" + next.getLabel() : detail;
        history.save(new TicketStatusHistory(ticket, previous, next, actor, message));
    }

    private Set<TicketStatus> allowedTechnicianTransitions(TicketStatus current) {
        return switch (current) {
            case ASSIGNED -> EnumSet.of(TicketStatus.IN_PROGRESS);
            case IN_PROGRESS -> EnumSet.of(TicketStatus.WAITING_FOR_CUSTOMER,
                    TicketStatus.WAITING_FOR_PARTS, TicketStatus.RESOLVED);
            case WAITING_FOR_CUSTOMER, WAITING_FOR_PARTS -> EnumSet.of(
                    TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED);
            default -> EnumSet.noneOf(TicketStatus.class);
        };
    }

    private TicketSummary summary(RepairTicket ticket, AppUser viewer) {
        boolean owner = ticket.getCustomer().getId().equals(viewer.getId());
        boolean assignedTechnician = ticket.getAssignedTechnician() != null
                && ticket.getAssignedTechnician().getId().equals(viewer.getId());
        boolean revealContact = owner || assignedTechnician;
        String customerName = revealContact ? ticket.getContactName() : "客户";
        String phone = revealContact ? ticket.getContactPhone() : maskPhone(ticket.getContactPhone());
        String address = revealContact ? ticket.getServiceAddress() : maskAddress(ticket.getServiceAddress());
        String customerDisplay = revealContact ? ticket.getCustomer().getFullName() : "客户";
        String technicianName = ticket.getAssignedTechnician() == null
                ? "待分派" : ticket.getAssignedTechnician().getFullName();
        return new TicketSummary(ticket.getTicketNumber(), ticket.getTitle(), ticket.getCategory(),
                ticket.getDescription(), customerName, phone, address, customerDisplay,
                technicianName, ticket.getStatus(), format(ticket.getCreatedAt()), format(ticket.getUpdatedAt()));
    }

    private String commentAuthorName(AppUser author, AppUser viewer) {
        return viewer.getRole() == UserRole.SUPPORT && author.getRole() == UserRole.CUSTOMER
                ? "客户" : author.getFullName();
    }

    private String historyActorName(AppUser actor, AppUser viewer) {
        return viewer.getRole() == UserRole.SUPPORT && actor.getRole() == UserRole.CUSTOMER
                ? "客户" : actor.getFullName();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 8) return "已隐藏";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String maskAddress(String address) {
        if (address == null || address.length() < 5) return "具体地址已隐藏";
        return address.substring(0, Math.min(5, address.length())) + "…";
    }

    private AppUser requireActor(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AccessDeniedException("登录状态无效"));
    }

    private RepairTicket findTicket(String ticketNumber) {
        return tickets.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "工单不存在"));
    }

    private void requireCanView(AppUser actor, RepairTicket ticket) {
        boolean owner = actor.getRole() == UserRole.CUSTOMER
                && ticket.getCustomer().getId().equals(actor.getId());
        boolean support = actor.getRole() == UserRole.SUPPORT;
        boolean assigned = actor.getRole() == UserRole.TECHNICIAN
                && ticket.getAssignedTechnician() != null
                && ticket.getAssignedTechnician().getId().equals(actor.getId());
        if (!(owner || support || assigned)) {
            throw new AccessDeniedException("无权访问此工单");
        }
    }

    private void requireRole(AppUser actor, UserRole role) {
        if (actor.getRole() != role) {
            throw new AccessDeniedException("当前账号无权执行此操作");
        }
    }

    private String format(Instant time) {
        return DISPLAY_TIME.format(time);
    }
}
