package com.hand.demo.api.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hzero.core.cache.Cacheable;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
public class CountingDetailInfo implements Cacheable {
    private String itemCode;
    private String itemName;
    private String unitCode;
    private String batchCode;
    private BigDecimal snapshotUnitQty;
    private BigDecimal unitQty;
    private BigDecimal unitDiffQty;
    private List<CounterDTO> counters;
    private String remark;
}
