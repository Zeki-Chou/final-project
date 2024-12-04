package com.hand.demo.api.controller.v1.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hand.demo.domain.entity.InvCountHeader;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hzero.boot.workflow.dto.RunTaskHistory;
import org.hzero.common.HZeroCacheKey;
import org.hzero.core.cache.CacheValue;
import org.hzero.core.cache.Cacheable;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InvCountHeaderDTO extends InvCountHeader implements Cacheable {
    @NotNull(groups = {ValidateExecuteCheck.class})
    private String supervisorId;

    private String tenantName;

    private String warehouseCode;

    private Integer isWMSwarehouse;

    private String status;

    private String authorization;

    private String employeeNumber;

    private String countTypeMeaning;

    private String countDimensionMeaning;

    private String countStatusMeaning;

    private String countModeMeaning;

    private String departmentName;

    private Long creatorId;

    @CacheValue(
            key = HZeroCacheKey.USER,
            primaryKey = "creatorId",
            searchKey = "realName",
            structure = CacheValue.DataStructure.MAP_OBJECT
    )
    private String createdName;

    private List<UserCacheDTO> supervisorList;

    private List<UserCacheDTO> counterList;

    private List<Map<String, Object>> snapshotMaterialList;

    private List<Map<String, Object>> snapshotBatchList;

    private List<InvCountLineDTO> countOrderLineList;

    private List<Long> materialList;

    private List<Long> batchIdList;

    private String materialCodes;

    private String supervisorNames;

    private String counterNames;

    private String batchCodes;

    private List<String> errorMsg;

    private List<RunTaskHistory> runHistoryList;
}