package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountLine;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class InvCountLineDTO extends InvCountLine {

    @ApiModelProperty(hidden = true)
    private String materialCode;

    @ApiModelProperty(hidden = true)
    private String materialName;

    @ApiModelProperty(hidden = true)
    private String batchCode;


}
