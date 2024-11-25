package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountHeader;
import lombok.Data;

@Data
public class InvCountHeaderDTO extends InvCountHeader {
    private String errorMessage;
}
