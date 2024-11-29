package com.hand.demo.api.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class WorkFlowEventDTO {
    // business key
    private String businessKey;

    // document status
    private String docStatus;

    // workflow ID
    private Long workflowId;

    // approved time
    private Date approvedTime;

}
