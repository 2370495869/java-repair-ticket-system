package io.github.repairticket.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CommentForm {
    @NotBlank(message = "留言不能为空")
    @Size(max = 1500, message = "留言不能超过 1500 个字符")
    private String body;

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
