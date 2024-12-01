package com.hand.demo.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hand.demo.domain.entity.InvCountHeader;
import io.swagger.annotations.ApiModelProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hzero.boot.workflow.dto.RunTaskHistory;
import org.hzero.core.cache.Cacheable;

import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class InvCountHeaderDTO extends InvCountHeader implements Cacheable {

//    @JsonIgnore
    @Transient
    private String supervisor;

    @ApiModelProperty(hidden = true)
    @Transient
    @JsonIgnore
    private List<String> errorMessageList = new ArrayList<>();

    @ApiModelProperty(hidden = true)
    @Transient
    private List<CounterDTO> counterList;

    @ApiModelProperty(hidden = true)
    @Transient
    private List<SupervisorDTO> supervisorList;

    @ApiModelProperty(hidden = true)
    @Transient
    private List<SnapshotMaterialDTO> snapshotMaterialList;

    @ApiModelProperty(hidden = true)
    @Transient
    private List<SnapshotBatchDTO> snapshotBatchList;

    @ApiModelProperty(hidden = true)
    @Transient
    private Integer isWMSwarehouse;

    @ApiModelProperty(hidden = true)
    @Transient
    private List<InvCountLineDTO> countOrderLineList;

    @ApiModelProperty(hidden = true)
    @Transient
    private Long employeeNumber;

    @ApiModelProperty(hidden = true)
    @Transient
    private String errorMsg;

    @ApiModelProperty(hidden = true)
    @Transient
    private String status;

    @ApiModelProperty(hidden = true)
    @Transient
    private List<RunTaskHistory> approvalHistory;
}
