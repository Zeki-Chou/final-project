package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountLine;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvCountLineDTO extends InvCountLine {
    private String errMsg;
}
