package com.hand.demo.api.controller.v1;

import com.alibaba.fastjson.JSON;
import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import com.hand.demo.api.dto.WorkFlowEventDTO;
import com.hand.demo.infra.state.ExecuteState;
import com.hand.demo.infra.state.InitState;
import com.hand.demo.infra.state.SubmitState;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.iam.ResourceLevel;
import io.choerodon.mybatis.pagehelper.annotation.SortDefault;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.choerodon.mybatis.pagehelper.domain.Sort;
import io.choerodon.swagger.annotation.Permission;
import io.lettuce.core.dynamic.CommandCreationException;
import io.swagger.annotations.ApiOperation;
import org.hzero.boot.platform.lov.annotation.ProcessLovValue;
import org.hzero.core.base.BaseConstants;
import org.hzero.core.base.BaseController;
import org.hzero.core.cache.ProcessCacheValue;
import org.hzero.core.util.Results;
import org.hzero.mybatis.helper.SecurityTokenHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.hand.demo.app.service.InvCountHeaderService;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import springfox.documentation.annotations.ApiIgnore;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

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

    public InvCountHeaderController(InvCountHeaderRepository invCountHeaderRepository, InvCountHeaderService invCountHeaderService) {
        this.invCountHeaderRepository = invCountHeaderRepository;
        this.invCountHeaderService = invCountHeaderService;
    }

    @ApiOperation(value = "list")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping
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
    public ResponseEntity<InvCountHeaderDTO> detail(@PathVariable Long countHeaderId) {
        InvCountHeaderDTO invCountHeader = invCountHeaderService.detail(countHeaderId);
        return Results.success(invCountHeader);
    }

    @ApiOperation(value = "order save")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping
    public ResponseEntity<List<InvCountHeaderDTO>> orderSave(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        invCountHeaders.forEach(item -> item.setTenantId(organizationId));
        invCountHeaders.forEach(header -> validObject(header, InitState.class));
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);

        InvCountInfoDTO invCountInfoDTO = invCountHeaderService.manualSaveCheck(invCountHeaders);
        if (invCountInfoDTO.getErrSize() > 0) {
            throw new CommonException(JSON.toJSONString(invCountInfoDTO));
        }

        List<InvCountHeaderDTO> invCountHeaderDTOS = invCountInfoDTO.getValidHeaderDTOS();

        return Results.success(invCountHeaderService.manualSave(invCountHeaderDTOS));
    }

    @ApiOperation(value = "order remove")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @DeleteMapping
    public ResponseEntity<?> orderRemove(@RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        SecurityTokenHelper.validToken(invCountHeaders);
        InvCountInfoDTO countInfoDTO = invCountHeaderService.checkAndRemove(invCountHeaders);
        if (countInfoDTO.getErrSize() > 0) {
            return Results.error(countInfoDTO);
        }
        List<InvCountHeader> headers = new ArrayList<>(invCountHeaders);
        invCountHeaderRepository.batchDeleteByPrimaryKey(headers);
        return Results.success();
    }

    @ApiOperation(value = "counting order execute")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/execute")
    public ResponseEntity<?> orderExecution(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        invCountHeaders.forEach(header -> validObject(header, ExecuteState.class));
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);
        invCountHeaders.forEach(item -> item.setTenantId(organizationId));

        InvCountInfoDTO saveInvCountInfoDTO = invCountHeaderService.manualSaveCheck(invCountHeaders);
        if (saveInvCountInfoDTO.getErrSize() > 0) {
            return Results.error(saveInvCountInfoDTO);
        }

        List<InvCountHeaderDTO> invCountHeaderDTOS = saveInvCountInfoDTO.getValidHeaderDTOS();
        List<InvCountHeaderDTO> saveResult = invCountHeaderService.manualSave(invCountHeaderDTOS);

        InvCountInfoDTO executeInvCountInfoDTO = invCountHeaderService.executeCheck(saveResult);
        if (executeInvCountInfoDTO.getErrSize() > 0) {
            return Results.error(executeInvCountInfoDTO);
        }

        return Results.success(invCountHeaderService.execute(executeInvCountInfoDTO.getValidHeaderDTOS()));
    }

    @ApiOperation(value = "Count order Submit")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/submit")
    public ResponseEntity<?> orderSubmit(@RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        invCountHeaders.forEach(header -> validObject(header, SubmitState.class));
        InvCountInfoDTO saveInvCountInfoDTO = invCountHeaderService.manualSaveCheck(invCountHeaders);
        if (saveInvCountInfoDTO.getErrSize() > 0) {
            return Results.error(saveInvCountInfoDTO);
        }

        List<InvCountHeaderDTO> saveResult = invCountHeaderService.manualSave(saveInvCountInfoDTO.getValidHeaderDTOS());
        InvCountInfoDTO submitInvCountInfoDTO = invCountHeaderService.submitCheck(saveResult);
        if (submitInvCountInfoDTO.getErrSize() > 0) {
            return Results.error(submitInvCountInfoDTO);
        }

        return Results.success(invCountHeaderService.submit(submitInvCountInfoDTO.getValidHeaderDTOS()));
    }

    @ApiOperation(value = "Count order approval callback")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PutMapping("/approval-callback")
    public ResponseEntity<InvCountHeaderDTO> orderApprovalCallback(@RequestBody WorkFlowEventDTO workFlowEventDTO, @PathVariable Long organizationId) {
        return Results.success(invCountHeaderService.updateApprovalCallback(workFlowEventDTO));
    }

    @ApiOperation(value = "Count order withdraw callback")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PutMapping("/withdraw-callback")
    public ResponseEntity<InvCountHeaderDTO> orderWithdrawCallback(@RequestBody WorkFlowEventDTO workFlowEventDTO, @PathVariable Long organizationId) {
        invCountHeaderService.withdrawWorkflow(organizationId, workFlowEventDTO);
        return Results.success();
    }

    @ApiOperation(value = "Count result sync")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PutMapping("/result-sync")
    public ResponseEntity<InvCountHeaderDTO> countResultSync(@RequestBody InvCountHeaderDTO invCountHeader) {
        return Results.success(invCountHeaderService.countResultSync(invCountHeader));
    }

    @ApiOperation(value = "Count order report")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @ProcessLovValue(targetField = BaseConstants.FIELD_BODY)
    @GetMapping("/report-data")
    public ResponseEntity<List<InvCountHeaderDTO>> countReportData(InvCountHeaderDTO invCountHeader) {
        return Results.success(invCountHeaderService.countingOrderReportDs(invCountHeader));
    }

    @ExceptionHandler(CommonException.class)
    public InvCountInfoDTO getCountInfo(HttpServletRequest req, CommonException ex) {
        return JSON.parseObject(ex.getMessage(),InvCountInfoDTO.class);
    }

}

