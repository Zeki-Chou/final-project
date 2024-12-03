package com.hand.demo.api.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.Map;

@Getter
@Setter
public class WorkFlowEventDTO {
    private String businessKey;
    private Long workflowId;
    private  String docStatus;
    private Date approvedTime;
//    private String dimension;
//    private String starter;
//    private String workflowKey;
//    private Map<String, Object> variabelMap;
}
