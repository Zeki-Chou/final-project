package com.hand.demo.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hand.demo.domain.entity.InvCountHeader;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hzero.core.cache.Cacheable;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class InvCountHeaderDTO extends InvCountHeader implements Cacheable {

//    @JsonIgnore
    private String supervisor;

    @ApiModelProperty(hidden = true)
    @JsonIgnore
    private List<String> errorMessageList = new ArrayList<>();

    @ApiModelProperty(hidden = true)
    private List<CounterDTO> counterList;

    @ApiModelProperty(hidden = true)
    private List<SupervisorDTO> supervisorList;

    @ApiModelProperty(hidden = true)
    private List<SnapshotMaterialDTO> snapshotMaterialList;

    @ApiModelProperty(hidden = true)
    private List<SnapshotBatchDTO> snapshotBatchList;

    @ApiModelProperty(hidden = true)
    private Integer isWMSwarehouse;

    @ApiModelProperty(hidden = true)
    private List<InvCountLineDTO> invCountLineDTOList;
}
