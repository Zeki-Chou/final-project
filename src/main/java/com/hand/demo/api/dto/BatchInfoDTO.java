package com.hand.demo.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BatchInfoDTO {
    private final Long batchId;
    private final String batchCode;
}
