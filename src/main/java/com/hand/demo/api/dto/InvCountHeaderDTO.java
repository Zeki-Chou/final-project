package com.hand.demo.api.dto;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hand.demo.domain.entity.InvBatch;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.entity.InvMaterial;
import io.choerodon.mybatis.annotation.ModifyAudit;
import io.choerodon.mybatis.annotation.VersionAudit;
import io.swagger.annotations.ApiModel;
import lombok.Getter;
import lombok.Setter;
import org.hzero.boot.workflow.dto.RunTaskHistory;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.util.List;

@Getter
@Setter
@ApiModel("")
@VersionAudit
@ModifyAudit
@JsonInclude(value = JsonInclude.Include.NON_NULL)
@Table(name = "fexam_inv_count_header")
public class InvCountHeaderDTO extends InvCountHeader {
    // detail
    @Transient
    private List<InvMaterial> snapshotMaterialList;
    @Transient
    private List<InvBatch> snapshotBatchList;
    @Transient
    private Boolean isWMSWarehouse;
    @Transient
    private List<UserDTO> counterList;
    @Transient
    private List<UserDTO> supervisorList;
    @Transient
    @JsonAlias("countOrderLineList")
    private List<InvCountLineDTO> invCountLineDTOList;
    @Transient
    private List<RunTaskHistory> approvalHistoryList;
    @Transient
    private String employeeNumber;
    @Transient
    private String ids;
    @Transient
    private Boolean isTenantAdmin;
    // Report
    @Transient
    private String tenantNum;
    @Transient
    private String creatorName;
    @Transient
    private String departmentCode;
    @Transient
    private String warehouseCode;
    @Transient
    private String countDimensionMeaning;
    @Transient
    private String countModeMeaning;
    @Transient
    private String countTypeMeaning;
    @Transient
    private String countStatusMeaning;
    @Transient
    private String counterNamesString;
    @Transient
    private String supervisorNamesString;
    @Transient
    private String materialCodesString;
    @Transient
    private String batchCodesString;

    // interface
    @Transient
    private String status;
    @Transient
    private String errorMsg ;
}
