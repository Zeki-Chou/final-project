package com.hand.demo.api.controller.v1.DTO;

import com.hand.demo.domain.entity.InvStock;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InvStockDTO extends InvStock {
    private BigDecimal snapshotUnitQty;
}