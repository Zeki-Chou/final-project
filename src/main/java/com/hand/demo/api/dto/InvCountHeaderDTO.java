package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.entity.InvCountLine;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
public class InvCountHeaderDTO extends InvCountHeader {
    private String errMsg;
    private List<InvCountLine> lines;
    private String countDimensionMeaning;
    private String countModeMeaning;
    private String countStatusMeaning;
    private String countTypeMeaning;
}
