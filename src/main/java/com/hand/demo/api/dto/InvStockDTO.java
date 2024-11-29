package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvStock;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvStockDTO extends InvStock {
    private String materialIds;
    private String batchIds;
    private String countDimension;

}
