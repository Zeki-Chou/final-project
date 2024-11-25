package com.hand.demo.api.controller.v1;

import com.alibaba.fastjson.JSON;
import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.entity.InvWarehouse;
import com.hand.demo.domain.repository.InvCountLineRepository;
import com.hand.demo.domain.repository.InvWarehouseRepository;
import com.hand.demo.infra.constant.Constants;
import com.hand.demo.infra.util.Utils;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.iam.ResourceLevel;
import io.choerodon.mybatis.pagehelper.annotation.SortDefault;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.choerodon.mybatis.pagehelper.domain.Sort;
import io.choerodon.swagger.annotation.Permission;
import io.swagger.annotations.ApiOperation;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.boot.platform.lov.dto.LovValueDTO;
import org.hzero.core.base.BaseConstants;
import org.hzero.core.base.BaseController;
import org.hzero.core.util.Results;
import org.hzero.mybatis.helper.SecurityTokenHelper;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.hand.demo.app.service.InvCountHeaderService;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import springfox.documentation.annotations.ApiIgnore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * (InvCountHeader)表控制层
 *
 * @author Allan
 * @since 2024-11-25 08:19:19
 */

@RestController("invCountHeaderController.v1")
@RequestMapping("/v1/{organizationId}/inv-count-headers")
public class InvCountHeaderController extends BaseController {

    private final InvCountHeaderRepository invCountHeaderRepository;
    private final InvCountHeaderService invCountHeaderService;
    private final IamRemoteService iamRemoteService;
    private final InvWarehouseRepository invWarehouseRepository;
    private final LovAdapter lovAdapter;

    // temporary
    enum UpdateStatus {
        DRAFT,
        INCOUNTING,
        WITHDRAWN,
        REJECTED,
    }

    public InvCountHeaderController(
            InvCountHeaderRepository invCountHeaderRepository,
            InvCountHeaderService invCountHeaderService,
            IamRemoteService iamRemoteService,
            InvWarehouseRepository invWarehouseRepository, LovAdapter lovAdapter
    ) {
        this.invCountHeaderRepository = invCountHeaderRepository;
        this.invCountHeaderService = invCountHeaderService;
        this.iamRemoteService = iamRemoteService;
        this.invWarehouseRepository = invWarehouseRepository;
        this.lovAdapter = lovAdapter;
    }

    @ApiOperation(value = "列表")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping
    public ResponseEntity<Page<InvCountHeader>> list(InvCountHeader invCountHeader, @PathVariable Long organizationId,
                                                     @ApiIgnore @SortDefault(value = InvCountHeader.FIELD_COUNT_HEADER_ID,
                                                             direction = Sort.Direction.DESC) PageRequest pageRequest) {
        Page<InvCountHeader> list = invCountHeaderService.selectList(pageRequest, invCountHeader);
        return Results.success(list);
    }

    @ApiOperation(value = "明细")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping("/{countHeaderId}/detail")
    public ResponseEntity<InvCountHeader> detail(@PathVariable Long countHeaderId) {
        InvCountHeader invCountHeader = invCountHeaderRepository.selectByPrimary(countHeaderId);
        return Results.success(invCountHeader);
    }

    @ApiOperation(value = "创建或更新")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping
    public ResponseEntity<List<InvCountHeaderDTO>> save(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        InvCountInfoDTO invCountInfoDTO = manualSaveCheck(invCountHeaders);
        if (invCountInfoDTO.getErrSize() > 0) {
            throw new CommonException(JSON.toJSONString(invCountInfoDTO));
        }
        List<InvCountHeaderDTO> invCountHeaderDTOS = invCountInfoDTO.getValidHeaderDTOS();
        invCountHeaderDTOS.forEach(item -> item.setTenantId(organizationId));
        List<InvCountHeaderDTO> result = invCountHeaderService.manualSave(invCountHeaderDTOS);
        return Results.success(result);
    }

    @ApiOperation(value = "删除")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @DeleteMapping
    public ResponseEntity<?> remove(@RequestBody List<InvCountHeader> invCountHeaders) {
        SecurityTokenHelper.validToken(invCountHeaders);
        invCountHeaderRepository.batchDeleteByPrimaryKey(invCountHeaders);
        return Results.success();
    }

    private InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        // not null and not blank validation
        validObject(invCountHeaderDTOS, InvCountHeader.class);
        // token validation when update
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaderDTOS);

        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        InvWarehouse warehouseRecord = new InvWarehouse();
        warehouseRecord.setIsWmsWarehouse(BaseConstants.Flag.YES);

        List<Long> warehouseWMSIds = invWarehouseRepository
                                        .selectList(warehouseRecord)
                                        .stream()
                                        .map(InvWarehouse::getWarehouseId)
                                        .collect(Collectors.toList());

        List<String> validUpdateStatuses = lovAdapter.queryLovValue(Constants.InvCountHeader.STATUS_LOV_CODE,BaseConstants.DEFAULT_TENANT_ID)
                                    .stream()
                                    .map(LovValueDTO::getValue)
                                    .collect(Collectors.toList());

        String draftValue = UpdateStatus.DRAFT.name();

        List<String> validUpdateStatusSupervisorWMS = validUpdateStatuses
                .stream()
                .filter(status -> !status.equals(draftValue))
                .collect(Collectors.toList());

        List<InvCountHeaderDTO> invalidHeaderDTOS = new ArrayList<>();
        List<InvCountHeaderDTO> validHeaderDTOS = new ArrayList<>();

        JSONObject iamJSONObject = Utils.getIamJSONObject(iamRemoteService);
        Long userId = iamJSONObject.getLong("id");

        for (InvCountHeaderDTO invCountHeaderDTO: invCountHeaderDTOS) {

            List<Long> headerCounterIds = Arrays.stream(invCountHeaderDTO.getSupervisorIds().split(","))
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

            List<Long> supervisorIds = Arrays.stream(invCountHeaderDTO.getCounterIds().split(","))
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

            if (invCountHeaderDTO.getCountHeaderId() == null) {
                validHeaderDTOS.add(invCountHeaderDTO);
            } else if (!validUpdateStatuses.contains(invCountHeaderDTO.getCountStatus())) {

                invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.UPDATE_STATUS_INVALID);
                invalidHeaderDTOS.add(invCountHeaderDTO);

            } else if (draftValue.equals(invCountHeaderDTO.getCountStatus()) &&
                    userId.equals(invCountHeaderDTO.getCreatedBy())) {

                invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.UPDATE_ACCESS_INVALID);
                invalidHeaderDTOS.add(invCountHeaderDTO);

            } else if (validUpdateStatusSupervisorWMS.contains(invCountHeaderDTO.getCountStatus())) {

                if (warehouseWMSIds.contains(invCountHeaderDTO.getWarehouseId()) && !supervisorIds.contains(userId)) {
                    invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.WAREHOUSE_SUPERVISOR_INVALID);
                    invalidHeaderDTOS.add(invCountHeaderDTO);
                }

                if (!headerCounterIds.contains(userId) &&
                    !supervisorIds.contains(userId) &&
                    !invCountHeaderDTO.getCreatedBy().equals(userId))
                {
                    invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.ACCESS_UPDATE_STATUS_INVALID);
                    invalidHeaderDTOS.add(invCountHeaderDTO);
                }

            } else {
                validHeaderDTOS.add(invCountHeaderDTO);
            }
        }

        invCountInfoDTO.setInvalidHeaderDTOS(invalidHeaderDTOS);
        invCountInfoDTO.setValidHeaderDTOS(validHeaderDTOS);
        invCountInfoDTO.setErrSize(invalidHeaderDTOS.size());
        return invCountInfoDTO;
    }

}

