package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountLine;
import lombok.Data;

@Data
public class InvCountLineDTO extends InvCountLine {
    private String materialCode;
    private String materialName;
    private String batchCode;
    private String counter;
}