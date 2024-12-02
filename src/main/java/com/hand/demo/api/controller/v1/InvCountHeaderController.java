package com.hand.demo.api.controller.v1;

import com.hand.demo.api.dto.*;
import io.choerodon.core.domain.Page;
import io.choerodon.core.iam.ResourceLevel;
import io.choerodon.mybatis.pagehelper.annotation.SortDefault;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.choerodon.mybatis.pagehelper.domain.Sort;
import io.choerodon.swagger.annotation.Permission;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Parameter;
import org.hzero.core.base.BaseController;
import org.hzero.core.cache.ProcessCacheValue;
import org.hzero.core.util.Results;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.hand.demo.app.service.InvCountHeaderService;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import springfox.documentation.annotations.ApiIgnore;

import java.util.List;

/**
 * (InvCountHeader)表控制层
 *
 * @author
 * @since 2024-11-25 10:19:44
 */

@RestController("invCountHeaderController.v1")
@RequestMapping("/v1/{organizationId}/inv-count-headers")
public class InvCountHeaderController extends BaseController {

    @Autowired
    private InvCountHeaderRepository invCountHeaderRepository;

    @Autowired
    private InvCountHeaderService invCountHeaderService;

    @ApiOperation(value = "List")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping
    public ResponseEntity<Page<InvCountHeaderDTO>> list(
            InvCountHeaderDTO invCountHeader,
            @PathVariable Long organizationId,
            @ApiIgnore
            @Parameter(hidden = true)
            @SortDefault(
                    value = InvCountHeaderDTO.FIELD_CREATION_DATE,
                    direction = Sort.Direction.DESC
            ) PageRequest pageRequest) {
        Page<InvCountHeaderDTO> list = invCountHeaderService.selectList(pageRequest, invCountHeader);
        return Results.success(list);
    }

    @ApiOperation(value = "Detail")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @ProcessCacheValue
    @GetMapping("/{countHeaderId}/detail")
    public ResponseEntity<InvCountHeaderDTO> detail(
            @PathVariable Long organizationId,
            @PathVariable Long countHeaderId) {
        InvCountHeaderDTO invCountHeader = invCountHeaderService.detail(countHeaderId);
        return Results.success(invCountHeader);
    }

    @ApiOperation(value = "Save")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping
    public ResponseEntity<List<InvCountHeaderDTO>> save(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        invCountHeaders.forEach(invCountHeader -> validObject(invCountHeader, ValidateHeaderSave.class));
//        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);
        invCountHeaders.forEach(item -> item.setTenantId(organizationId));
        invCountHeaderService.saveData(invCountHeaders);
        return Results.success(invCountHeaders);
    }

    @ApiOperation(value = "Execute")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/execute")
    public ResponseEntity<List<InvCountHeaderDTO>> execute(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        invCountHeaders.forEach(invCountHeader -> validObject(invCountHeader, ValidateOrderExecute.class));
//        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);
        invCountHeaders.forEach(item -> item.setTenantId(organizationId));
        invCountHeaderService.execute(invCountHeaders);
        return Results.success(invCountHeaders);
    }

    @ApiOperation(value = "Result sync")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/result-sync")
    public ResponseEntity<InvCountHeaderDTO> sync (
            @PathVariable Long organizationId,
            @RequestBody InvCountHeaderDTO invCountHeader
    ) {
        validObject(invCountHeader, ValidateResultSync.class);
//        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);
        invCountHeader.setTenantId(organizationId);
        invCountHeaderService.countResultSync(invCountHeader);
        return Results.success(invCountHeader);
    }

    @ApiOperation(value = "Count sync wms")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/count-sync-wms")
    public ResponseEntity<InvCountInfoDTO> syncWMS (
            @PathVariable Long organizationId,
            @RequestBody List<InvCountHeaderDTO> headerDTOList
    ) {
        headerDTOList.forEach(invCountHeader -> validObject(invCountHeader, ValidateResultSync.class));
//        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);
        headerDTOList.forEach(invCountHeader -> invCountHeader.setTenantId(organizationId));

        return Results.success(invCountHeaderService.countSyncWms(headerDTOList));
    }

    @ApiOperation(value = "Counting Order Report")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping("/report")
    public ResponseEntity<List<InvCountHeaderDTO>> report (
            @PathVariable Long organizationId,
            InvCountHeaderDTO headerDTO
    ) {
//        validObject(invCountHeader, ValidateResultSync.class);
//        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);
        headerDTO.setTenantId(organizationId);

        return Results.success(invCountHeaderService.countingOrderReportDs(headerDTO));
    }

    @ApiOperation(value = "Submit Workflow")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/submit-workflow")
    public ResponseEntity<List<InvCountHeaderDTO>> submit(
            @PathVariable Long organizationId,
            @RequestBody List<InvCountHeaderDTO> headerDTOList
    ) {
//        SecurityTokenHelper.validToken(invCountHeaders);
        return Results.success(invCountHeaderService.submit(headerDTOList));
    }

    @ApiOperation(value = "Approval Callback")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/approval-callback")
    public ResponseEntity<InvCountHeaderDTO> approvalCallback(
            @PathVariable Long organizationId,
            @RequestBody WorkFlowEventDTO eventDTO
    ) {
//        SecurityTokenHelper.validToken(invCountHeaders);
        return Results.success(invCountHeaderService.approvalCallback(eventDTO));
    }

}

