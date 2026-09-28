package io.github.repairticket.domain;

public enum UserRole {
    CUSTOMER("客户"),
    SUPPORT("客服"),
    TECHNICIAN("维修人员");

    private final String label;

    UserRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
