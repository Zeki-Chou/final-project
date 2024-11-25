package com.hand.demo.api.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Setter
@Getter
@Accessors(chain = true)
public class SnapshotBatchDTO {
    private Long id;
    private String batchCode;
}
