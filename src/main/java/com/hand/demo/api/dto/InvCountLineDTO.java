package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountLine;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.hzero.common.HZeroCacheKey;
import org.hzero.core.cache.CacheValue;

@Data
public class InvCountLineDTO extends InvCountLine {

    @ApiModelProperty(hidden = true)
    private String materialCode;

    @ApiModelProperty(hidden = true)
    private String materialName;

    @ApiModelProperty(hidden = true)
    private String batchCode;

    @ApiModelProperty(hidden = true)
    private String supervisorIds;

    @ApiModelProperty(hidden = true)
    private String realName;
}
