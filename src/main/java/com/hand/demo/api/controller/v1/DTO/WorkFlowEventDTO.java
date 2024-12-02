package com.hand.demo.api.controller.v1.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class WorkFlowEventDTO {
    private String businessKey;

    private String docStatus;

    private Long workflowId;

    private Date approvedTime;
}
