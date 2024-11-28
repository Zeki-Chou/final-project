package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvStock;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Getter
@Setter
@Accessors(chain = true)
public class InvStockDTO extends InvStock {
//    private String countingDimension;

    private BigDecimal totalUnitQuantity;

//    private Object snapshotMaterialIds;
//
//    private Object snapshotBatchIds;
}
