package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.entity.InvCountLine;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.hzero.core.cache.Cacheable;

import java.util.List;

@Data
public class InvCountHeaderDTO extends InvCountHeader implements Cacheable {
    List<InvCountLine> invCountLineList;
    String errorMessage;

    @ApiModelProperty(value = "Type String, pass user id, support single, not multi")
    String supervisor;

    List<UserInfoDTO> counterList;
    List<UserInfoDTO> supervisorList;
    List<MaterialInfoDTO> snapshotMaterialList;
    List<BatchInfoDTO> snapshotBatchList;
    List<InvCountLineDTO> invCountLineDTOList;

    Integer isWmsWarehouse;
}
