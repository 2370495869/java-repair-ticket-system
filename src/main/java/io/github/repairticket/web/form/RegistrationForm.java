package io.github.repairticket.web.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegistrationForm {
    @NotBlank(message = "请填写邮箱")
    @Email(message = "邮箱格式不正确")
    @Size(max = 190, message = "邮箱不能超过 190 个字符")
    private String email;

    @NotBlank(message = "请填写姓名")
    @Size(max = 60, message = "姓名不能超过 60 个字符")
    private String fullName;

    @NotBlank(message = "请填写手机号")
    @Pattern(regexp = "1[3-9]\\d{9}", message = "请输入有效的 11 位手机号")
    private String phone;

    @NotBlank(message = "请设置密码")
    @Size(min = 12, max = 72, message = "密码长度须为 12 至 72 个字符")
    private String password;

    @NotBlank(message = "请再次输入密码")
    private String passwordConfirmation;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getPasswordConfirmation() { return passwordConfirmation; }
    public void setPasswordConfirmation(String passwordConfirmation) { this.passwordConfirmation = passwordConfirmation; }
}
