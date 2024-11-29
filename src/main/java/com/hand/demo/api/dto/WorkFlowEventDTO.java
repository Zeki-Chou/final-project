package com.hand.demo.api.dto;

import lombok.Data;

import java.util.Date;

@Data
public class WorkFlowEventDTO {
    /**
     * business key
     */
    private String businessKey;

    /**
     * document status
     */
    private String docStatus;

    /**
     * workflow ID
     */
    private Long workflowId;

    /**
     * approved time
     */
    private Date approvedTime;
}

