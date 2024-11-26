package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.entity.InvCountLine;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
public class InvCountHeaderDTO extends InvCountHeader {
    List<InvCountLine> invCountLineList;
    String errorMessage;

    @ApiModelProperty(value = "Type String, pass user id, support single, not multi")
    String supervisor;
}
