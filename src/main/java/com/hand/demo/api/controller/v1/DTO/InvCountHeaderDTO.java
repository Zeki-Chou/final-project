package com.hand.demo.api.controller.v1.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.entity.InvCountLine;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hzero.common.HZeroCacheKey;
import org.hzero.core.cache.CacheValue;
import org.hzero.core.cache.Cacheable;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InvCountHeaderDTO extends InvCountHeader implements Cacheable {
    private String supervisorId;

    private List<UserCacheDTO> counterList;

    private List<UserCacheDTO> supervisorList;

    private List<Map<String, Object>> snapshotMaterialList;

    private List<Map<String, Object>> snapshotBatchList;

    private Integer isWMSwarehouse;

    private List<InvCountLineDTO> invCountLinesList;

    private List<Long> materialList;

    private List<Long> batchIdList;
}