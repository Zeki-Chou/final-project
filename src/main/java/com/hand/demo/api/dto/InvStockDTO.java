package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvStock;
import lombok.Data;

import java.util.List;

@Data
public class InvStockDTO extends InvStock {
    List<Long> materialIds;
}
