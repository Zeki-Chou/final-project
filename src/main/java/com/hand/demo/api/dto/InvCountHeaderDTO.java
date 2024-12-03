package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.entity.InvCountLine;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hzero.boot.workflow.dto.RunTaskHistory;
import org.hzero.core.cache.Cacheable;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvCountHeaderDTO extends InvCountHeader implements Cacheable {
    private List<InvCountLine> countOrderLineList;
    private String errorMsg;
    private int lineNumber;
    private List<IamUserDTO> counterList;
    private List<IamUserDTO> supervisorList;
    private List<SnapshotMaterialDTO> snapshotMaterialList;
    private List<SnapshotBatchDTO> snapshotBatchList;
    private Integer isWmsWarehouse;
    private String status;
    private String employeeNumber;

    private String tenantCode;
    private String departmentName;
    private String warehouseCode;
    private String countStatusMeaning;
    private String countDimensionMeaning;
    private String countModeMeaning;
    private String countTypeMeaning;
    private String creator;
    private String counters;
    private String supervisors;
    private String materials;
    private String batches;
    private List<RunTaskHistory> approvalHistory;
    private List<InvCountLineDTO> countOrderLineListDTO;

}
