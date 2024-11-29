package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvBatch;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.entity.InvMaterial;
import lombok.Getter;
import lombok.Setter;
import org.hzero.boot.workflow.dto.RunTaskHistory;

import javax.persistence.Transient;
import java.util.List;

@Getter
@Setter
public class InvCountHeaderDTO extends InvCountHeader {
    @Transient
    private List<UserDTO> counterList;
    @Transient
    private List<UserDTO> supervisorList;
    @Transient
    private List<InvMaterial> snapshotMaterialList;
    @Transient
    private List<InvBatch> snapshotBatchList;
    @Transient
    private String departmentCode;
    @Transient
    private Boolean isWMSWarehouse;
    @Transient
    private List<InvCountLineDTO> invCountLineDTOList;
    @Transient
    private String tenantNum;
    @Transient
    private List<RunTaskHistory> approvalHistoryList;


}
