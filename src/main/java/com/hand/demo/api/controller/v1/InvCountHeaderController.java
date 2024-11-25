package com.hand.demo.api.controller.v1;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.entity.InvWarehouse;
import com.hand.demo.domain.repository.InvCountLineRepository;
import com.hand.demo.domain.repository.InvWarehouseRepository;
import com.hand.demo.infra.constant.Constants;
import com.hand.demo.infra.util.Utils;
import io.choerodon.core.domain.Page;
import io.choerodon.core.iam.ResourceLevel;
import io.choerodon.mybatis.pagehelper.annotation.SortDefault;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.choerodon.mybatis.pagehelper.domain.Sort;
import io.choerodon.swagger.annotation.Permission;
import io.swagger.annotations.ApiOperation;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
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


    private InvCountHeaderRepository invCountHeaderRepository;
    private InvCountHeaderService invCountHeaderService;
    private IamRemoteService iamRemoteService;
    private InvWarehouseRepository invWarehouseRepository;

    // TODO: add lov adapter

    public InvCountHeaderController(
            InvCountHeaderRepository invCountHeaderRepository,
            InvCountHeaderService invCountHeaderService,
            IamRemoteService iamRemoteService
    ) {
        this.invCountHeaderRepository = invCountHeaderRepository;
        this.invCountHeaderService = invCountHeaderService;
        this.iamRemoteService = iamRemoteService;
    }

    // temporary
    enum UpdateStatus {
        DRAFT,
        INCOUNTING,
        WITHDRAWN,
        REJECTED,
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
    public ResponseEntity<List<InvCountHeader>> save(@PathVariable Long organizationId, @RequestBody List<InvCountHeader> invCountHeaders) {
        validObject(invCountHeaders, InvCountHeader.class);
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);
        invCountHeaders.forEach(item -> item.setTenantId(organizationId));
        invCountHeaderService.saveData(invCountHeaders);
        return Results.success(invCountHeaders);
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
        validObject(invCountHeaderDTOS, InvCountHeader.class);
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();

        List<InvWarehouse> warehouses = invWarehouseRepository.selectAll();

        List<Long> warehouseWMSIds = warehouses
                                        .stream()
                                        .filter(warehouse -> warehouse.getIsWmsWarehouse() == 1)
                                        .map(InvWarehouse::getWarehouseId)
                                        .collect(Collectors.toList());

        // TODO: change with lov adapter
        List<String> validUpdateStatuses = Stream.of(UpdateStatus.values())
                                                .map(Enum::name)
                                                .collect(Collectors.toList());

        List<InvCountHeaderDTO> invalidHeaderDTOS = new ArrayList<>();
        List<InvCountHeaderDTO> validHeaderDTOS = new ArrayList<>();

        for (InvCountHeaderDTO invCountHeaderDTO: invCountHeaderDTOS) {

            if (invCountHeaderDTO.getCountHeaderId() == null) {
                validHeaderDTOS.add(invCountHeaderDTO);
                continue;
            }

            boolean valid = true;

            if (!validUpdateStatuses.contains(invCountHeaderDTO.getCountStatus())) {
                // set error message
                valid = false;
                invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.UPDATE_STATUS_INVALID);
                invalidHeaderDTOS.add(invCountHeaderDTO);
            }

            // TODO: change with lov adapter
            JSONObject iamJSONObject = Utils.getIamJSONObject(iamRemoteService);

            String draftValue = UpdateStatus.DRAFT.name();
            //TODO: use iam remote json object to get the value
            Long userId = 0L;

            if (draftValue.equals(invCountHeaderDTO.getCountStatus()) &&
                    userId.equals(invCountHeaderDTO.getCreatedBy())) {
                valid = false;
                invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.UPDATE_ACCESS_INVALID);
                invalidHeaderDTOS.add(invCountHeaderDTO);
            }

            List<String> validUpdateStatusSupervisorWMS = validUpdateStatuses
                    .stream()
                    .filter(status -> !status.equals(draftValue))
                    .collect(Collectors.toList());

            List<Long> headerCounterIds = Arrays.stream(invCountHeaderDTO.getSupervisorIds().split(","))
                                                .map(Long::valueOf)
                                                .collect(Collectors.toList());

            List<Long> supervisorIds = Arrays.stream(invCountHeaderDTO.getCounterIds().split(","))
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

            if (validUpdateStatusSupervisorWMS.contains(invCountHeaderDTO.getCountStatus())) {
                //TODO: find out how to find operator
                if (warehouseWMSIds.contains(invCountHeaderDTO.getWarehouseId()) && !supervisorIds.contains(userId)) {
                    valid = false;
                    invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.WAREHOUSE_SUPERVISOR_INVALID);
                    invalidHeaderDTOS.add(invCountHeaderDTO);
                }

                if (!headerCounterIds.contains(userId) &&
                    !supervisorIds.contains(userId) &&
                    !invCountHeaderDTO.getCreatedBy().equals(userId))
                {
                    valid = false;
                    invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.ACCESS_UPDATE_STATUS_INVALID);
                    invalidHeaderDTOS.add(invCountHeaderDTO);
                }
            }

            if (valid) {
                validHeaderDTOS.add(invCountHeaderDTO);
            }
        }

        invCountInfoDTO.setInvalidHeaderDTOS(invalidHeaderDTOS);
        invCountInfoDTO.setValidHeaderDTOS(validHeaderDTOS);
        invCountInfoDTO.setErrSize(invalidHeaderDTOS.size());
        return invCountInfoDTO;
    }

}

