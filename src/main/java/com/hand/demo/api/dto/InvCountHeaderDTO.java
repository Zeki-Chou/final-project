package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.entity.InvCountLine;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.hzero.boot.workflow.dto.RunTaskHistory;
import org.hzero.common.HZeroCacheKey;
import org.hzero.core.cache.CacheValue;
import org.hzero.core.cache.Cacheable;

import java.util.List;

@Data
public class InvCountHeaderDTO extends InvCountHeader implements Cacheable {
    @ApiModelProperty(hidden = true)
    private List<InvCountLine> invCountLineList;

    @ApiModelProperty(hidden = true)
    private String errorMessage;

    @ApiModelProperty(hidden = true)
    private String status;

    @ApiModelProperty(value = "Type String, pass user id, support single, not multi")
    private String supervisor;

    @ApiModelProperty(hidden = true)
    private List<UserInfoDTO> counterList;

    @ApiModelProperty(hidden = true)
    private List<UserInfoDTO> supervisorList;

    @ApiModelProperty(hidden = true)
    private List<MaterialInfoDTO> snapshotMaterialList;

    @ApiModelProperty(hidden = true)
    private List<BatchInfoDTO> snapshotBatchList;

    @ApiModelProperty(hidden = true)
    private List<InvCountLineDTO> countOrderLineList;

    @ApiModelProperty(hidden = true)
    private Integer isWmsWarehouse;

    @ApiModelProperty(hidden = true)
    private String countDimensionMeaning;

    @ApiModelProperty(hidden = true)
    private String countTypeMeaning;

    @ApiModelProperty(hidden = true)
    private String countStatusMeaning;

    @ApiModelProperty(hidden = true)
    @CacheValue(key = HZeroCacheKey.USER, primaryKey = "createdBy", searchKey = "realName", structure = CacheValue.DataStructure.MAP_OBJECT)
    private String realName;

    @ApiModelProperty(hidden = true)
    private String warehouseCode;

    @ApiModelProperty(hidden = true)
    private String departmentCode;

    @ApiModelProperty(hidden = true)
    private List<RunTaskHistory> approvalHistory;

    private String token; // temp
}
