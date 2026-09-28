package io.github.repairticket.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class NewTicketForm {
    @NotBlank(message = "请填写问题标题")
    @Size(max = 120, message = "标题不能超过 120 个字符")
    private String title;

    @NotBlank(message = "请选择报修类型")
    private String category;

    @NotBlank(message = "请描述遇到的问题")
    @Size(max = 1800, message = "描述不能超过 1800 个字符")
    private String description;

    @NotBlank(message = "请填写联系人")
    @Size(max = 80, message = "联系人不能超过 80 个字符")
    private String contactName;

    @NotBlank(message = "请填写手机号")
    @Pattern(regexp = "1[3-9]\\d{9}", message = "请输入有效的 11 位手机号")
    private String contactPhone;

    @NotBlank(message = "请填写维修地址")
    @Size(max = 300, message = "地址不能超过 300 个字符")
    private String serviceAddress;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getServiceAddress() { return serviceAddress; }
    public void setServiceAddress(String serviceAddress) { this.serviceAddress = serviceAddress; }
}
