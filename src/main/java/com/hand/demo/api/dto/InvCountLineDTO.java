package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountLine;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class InvCountLineDTO extends InvCountLine {

    @ApiModelProperty(hidden = true)
    private String itemCode;

    @ApiModelProperty(hidden = true)
    private String itemName;

    @ApiModelProperty(hidden = true)
    String batchCode;


}
