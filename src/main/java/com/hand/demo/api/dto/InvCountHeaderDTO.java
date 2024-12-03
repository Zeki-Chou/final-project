package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvBatch;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.entity.InvMaterial;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hzero.boot.workflow.dto.RunTaskHistory;
import org.hzero.core.cache.Cacheable;

import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
public class InvCountHeaderDTO extends InvCountHeader implements Cacheable {
    private String errMsg;
    private List<InvCountLine> countOrderLineList;
    private List<InvCountLineDTO> countOrderLineListDTO;
    private String countDimensionMeaning;
    private String countModeMeaning;
    private String countStatusMeaning;
    private String countTypeMeaning;
    private String supervisor;
    private List<IamDTO> counterList;
    private List<IamDTO> supervisorList;
    private List<InvMaterialDTO> snapshotMaterialList;
    private List<InvBatchDTO> snapshotBatchList;
    private boolean isWMSwarehouse;
//    private List<InvCountLine> countOrderLineList;
    private String namespace;
    private String serverCode;
    private String interfaceCode;
    private String employeeNumber;
    private String status;
    private String departmentName;
    private String warehouseName;
    private List<RunTaskHistory> approvalHistory;
    private String creatorName;
}
