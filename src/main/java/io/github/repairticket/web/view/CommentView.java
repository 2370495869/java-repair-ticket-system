package io.github.repairticket.web.view;

import io.github.repairticket.domain.UserRole;

public record CommentView(String authorName, UserRole authorRole, String body, String createdAt) {
}
