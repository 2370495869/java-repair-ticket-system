package io.github.repairticket.domain;

public enum TicketStatus {
    SUBMITTED("待分派"),
    ASSIGNED("已分派"),
    IN_PROGRESS("维修中"),
    WAITING_FOR_CUSTOMER("等待客户"),
    WAITING_FOR_PARTS("等待配件"),
    RESOLVED("已解决"),
    CLOSED("已完成");

    private final String label;

    TicketStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
