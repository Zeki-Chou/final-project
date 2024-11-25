package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountHeader;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvCountHeaderDTO extends InvCountHeader {
    private String errMsg;
}
