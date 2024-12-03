package com.hand.demo.api.dto;

import lombok.Data;

import java.util.Date;

@Data
public class WorkFlowEventDTO {
    private String businessKey;
    private String docStatus;
    private Long workflowId;
    private Date approvedTime;
}
