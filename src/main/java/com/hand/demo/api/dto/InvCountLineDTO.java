package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountLine;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class InvCountLineDTO extends InvCountLine {
    private String itemCode;
    private String itemName;
    private String baseUnitCode;
    private String batchCode;
    private String counters;
}
