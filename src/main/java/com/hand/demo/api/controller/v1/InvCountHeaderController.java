package com.hand.demo.api.controller.v1;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import com.hand.demo.api.dto.WorkFlowEventDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.core.iam.ResourceLevel;
import io.choerodon.mybatis.pagehelper.annotation.SortDefault;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.choerodon.mybatis.pagehelper.domain.Sort;
import io.choerodon.swagger.annotation.Permission;
import io.swagger.annotations.ApiOperation;
import org.hzero.boot.platform.lov.annotation.ProcessLovValue;
import org.hzero.core.base.BaseConstants;
import org.hzero.core.base.BaseController;
import org.hzero.core.cache.ProcessCacheValue;
import org.hzero.core.util.Results;
import org.hzero.mybatis.helper.SecurityTokenHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.hand.demo.app.service.InvCountHeaderService;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import springfox.documentation.annotations.ApiIgnore;

import java.util.List;

/**
 * (InvCountHeader)表控制层
 *
 * @author azhar.naufal@hand-global.com
 * @since 2024-11-25 11:15:49
 */

@RestController("invCountHeaderController.v1")
@RequestMapping("/v1/{organizationId}/inv-count-headers")
public class InvCountHeaderController extends BaseController {

    @Autowired
    private InvCountHeaderRepository invCountHeaderRepository;

    @Autowired
    private InvCountHeaderService invCountHeaderService;

    @ApiOperation(value = "list")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping
    @ProcessLovValue(targetField = BaseConstants.FIELD_BODY)
    public ResponseEntity<Page<InvCountHeaderDTO>> list(InvCountHeaderDTO invCountHeader, @PathVariable Long organizationId,
                                                     @ApiIgnore @SortDefault(value = InvCountHeader.FIELD_COUNT_HEADER_ID,
                                                             direction = Sort.Direction.DESC) PageRequest pageRequest) {
        Page<InvCountHeaderDTO> list = invCountHeaderService.selectList(pageRequest, invCountHeader);
        return Results.success(list);
    }

    @ApiOperation(value = "detail")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping("/{countHeaderId}/detail")
    @ProcessCacheValue
    @ProcessLovValue(targetField = BaseConstants.FIELD_BODY)
    public ResponseEntity<InvCountHeaderDTO> detail(@PathVariable Long countHeaderId) {
        InvCountHeaderDTO invCountHeader = invCountHeaderService.detail(countHeaderId);
        return Results.success(invCountHeader);
    }

    @ApiOperation(value = "orderSave")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping
    public ResponseEntity<List<InvCountHeaderDTO>> orderSave(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        validObject(invCountHeaders, InvCountHeader.save.class);
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);
        invCountHeaders.forEach(item -> item.setTenantId(organizationId));
        invCountHeaderService.manualSave(invCountHeaders);
        return Results.success(invCountHeaders);
    }

    @ApiOperation(value = "orderRemove")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @DeleteMapping
    public ResponseEntity<?> orderRemove(@RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        SecurityTokenHelper.validToken(invCountHeaders);
        return Results.success(invCountHeaderService.checkAndRemove(invCountHeaders));
    }

    @ApiOperation(value = "orderExecution")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/orderExecution")
    public ResponseEntity<?> orderExecution(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        validObject(invCountHeaders);
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);
        invCountHeaders.forEach(item -> item.setTenantId(organizationId));
        List<InvCountHeaderDTO> returnedHeaderDTO = invCountHeaderService.execute(invCountHeaders);
        InvCountInfoDTO infoDTO = invCountHeaderService.countSyncWms(invCountHeaders);
        return Results.success(returnedHeaderDTO);
    }

    @ApiOperation(value = "countResultSync")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/countResultSync")
    public ResponseEntity<?> countResultSync(@PathVariable Long organizationId, @RequestBody InvCountHeaderDTO invCountHeaderDTO) {
        validObject(invCountHeaderDTO);
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaderDTO);
        invCountHeaderDTO.setTenantId(organizationId);
        return Results.success(invCountHeaderService.countResultSync(invCountHeaderDTO));
    }

    @ApiOperation(value = "orderSubmit")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/orderSubmit")
    public ResponseEntity<?> orderSubmit(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeaderDTOS) {
        validObject(invCountHeaderDTOS);
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaderDTOS);
        invCountHeaderDTOS.forEach(dto -> dto.setTenantId(organizationId));
        return Results.success(invCountHeaderService.submit(invCountHeaderDTOS));
    }

    @ApiOperation(value = "approvalCallback")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/approvalCallback")
    public ResponseEntity<InvCountHeader> approvalCallback(@PathVariable Long organizationId,
                                                           @RequestBody WorkFlowEventDTO workFlowEventDTO){
        return Results.success(invCountHeaderService.approvalCallback(organizationId, workFlowEventDTO));
    }

    @ApiOperation(value = "countingOrderReportDs")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping("/countingOrderReportDs")
    @ProcessLovValue(targetField = BaseConstants.FIELD_BODY)
    public ResponseEntity<?> countingOrderReportDs(InvCountHeaderDTO invCountHeaderDTO, @PathVariable Long organizationId) {
        return Results.success(invCountHeaderService.countingOrderReportDs(invCountHeaderDTO));
    }

}

