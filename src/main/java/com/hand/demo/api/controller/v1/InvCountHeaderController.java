package com.hand.demo.api.controller.v1;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import com.hand.demo.api.dto.WorkFlowEventDTO;
import com.hand.demo.infra.constant.Constants;
import com.hand.demo.infra.constant.InvCountHeaderConstants;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.iam.ResourceLevel;
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.mybatis.pagehelper.annotation.SortDefault;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.choerodon.mybatis.pagehelper.domain.Sort;
import io.choerodon.swagger.annotation.Permission;
import io.swagger.annotations.ApiOperation;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.boot.platform.lov.dto.LovAggregate;
import org.hzero.boot.platform.lov.dto.LovValueDTO;
import org.hzero.core.base.BaseConstants;
import org.hzero.core.base.BaseController;
import org.hzero.core.util.Results;
import org.hzero.mybatis.helper.SecurityTokenHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.hand.demo.app.service.InvCountHeaderService;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import springfox.documentation.annotations.ApiIgnore;

import java.util.*;
import java.util.stream.Collectors;

/**
 * InvCountHeader
 *
 * @author
 * @since 2024-11-25 09:59:39
 */

@RestController("invCountHeaderController.v1")
@RequestMapping("/v1/{organizationId}/inv-count-headers")
public class InvCountHeaderController extends BaseController {
    @Autowired
    private LovAdapter lovAdapter;


    @Autowired
    private InvCountHeaderRepository invCountHeaderRepository;

    @Autowired
    private InvCountHeaderService invCountHeaderService;

    @ApiOperation(value = "orderSave")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping
    public ResponseEntity<List<InvCountHeaderDTO>> orderSave(@RequestBody List<InvCountHeaderDTO> invCountHeaderDTOS) {
        // save validate object
        invCountHeaderDTOS.forEach(headerDTO-> validObject(headerDTO, InvCountHeader.Save.class));
        // save check
        InvCountInfoDTO invCountInfoDTO = invCountHeaderService.manualSaveCheck(invCountHeaderDTOS);
        if(invCountInfoDTO.getErrorList().stream().anyMatch(Objects::nonNull)){
            throw new CommonException("Validation error: "+invCountInfoDTO.getErrorList());
        }
        // security token
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaderDTOS);
        // save
        invCountHeaderService.manualSave(invCountHeaderDTOS);
        return Results.success(invCountHeaderDTOS);
    }

    @ApiOperation(value = "orderRemove")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @DeleteMapping
    public ResponseEntity<?> orderRemove(@RequestBody List<InvCountHeaderDTO> invCountHeaderDTOS) {
        // remove validate object
        invCountHeaderDTOS.forEach(headerDTO-> validObject(headerDTO, InvCountHeader.Remove.class));
        // remove check
        InvCountInfoDTO invCountInfoDTO = invCountHeaderService.checkAndRemove(invCountHeaderDTOS);
        if(invCountInfoDTO.getErrorList().stream().anyMatch(Objects::nonNull)){
            throw new CommonException("Validation error: "+invCountInfoDTO.getErrorList());
        }
        // security token
        SecurityTokenHelper.validToken(invCountHeaderDTOS);
        // remove
        invCountHeaderRepository.batchDeleteByPrimaryKey(invCountHeaderDTOS);
        return Results.success();
    }

    @ApiOperation(value = "list")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping
    public ResponseEntity<Page<InvCountHeaderDTO>> list(InvCountHeaderDTO invCountHeaderDTO,
                                                        @ApiIgnore @SortDefault(value = InvCountHeader.FIELD_CREATION_DATE,
                                                             direction = Sort.Direction.DESC) PageRequest pageRequest) {
        Page<InvCountHeaderDTO> list = invCountHeaderService.selectList(pageRequest, invCountHeaderDTO);
        return Results.success(list);
    }

    @ApiOperation(value = "detail")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping("/{countHeaderId}/detail")
    public ResponseEntity<InvCountHeader> detail(@PathVariable Long countHeaderId) {
        InvCountHeader invCountHeader = invCountHeaderRepository.selectByPrimary(countHeaderId);
        return Results.success(invCountHeader);
    }

    @ApiOperation(value = "orderExecution")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/execution")
    public ResponseEntity<List<InvCountHeaderDTO>> orderExecution(@RequestBody List<InvCountHeaderDTO> invCountHeaderDTOS) {
        // execute valid object
        invCountHeaderDTOS.forEach(headerDTO-> validObject(headerDTO, InvCountHeader.Execute.class));
        // execute check
        InvCountInfoDTO execInvCountInfoDTO = invCountHeaderService.executeCheck(invCountHeaderDTOS);
        if(execInvCountInfoDTO.getErrorList().stream().anyMatch(Objects::nonNull)){
            throw new CommonException("Validation error: "+execInvCountInfoDTO.getErrorList());
        }
        // security check
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaderDTOS);
        List<InvCountHeaderDTO> returnedInvCountHeaderDTOS = invCountHeaderService.execute(invCountHeaderDTOS);
        // sync wms
        InvCountInfoDTO  syncInvCountInfoDTO = invCountHeaderService.countSyncWMS(returnedInvCountHeaderDTOS);
        if(syncInvCountInfoDTO.getErrorList().stream().anyMatch(Objects::nonNull)){
            throw new CommonException("Validation error: "+syncInvCountInfoDTO.getErrorList());
        }
        return Results.success(returnedInvCountHeaderDTOS);
    }
    @ApiOperation(value = "orderSubmit")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("orderSubmit")
    public ResponseEntity<List<InvCountHeaderDTO>> orderSubmit(@RequestBody List<InvCountHeaderDTO> invCountHeaderDTOS) {
        // submit valid object
        invCountHeaderDTOS.forEach(headerDTO-> validObject(headerDTO, InvCountHeader.Submit.class));
        // submit check
        InvCountInfoDTO invCountInfoDTO = invCountHeaderService.submitCheck(invCountHeaderDTOS);
        if(invCountInfoDTO.getErrorList().stream().anyMatch(Objects::nonNull)){
            throw new CommonException("Validation error: "+invCountInfoDTO.getErrorList());
        }
        // security token
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaderDTOS);
        // submit
        List<InvCountHeaderDTO> submittedInvCountHeaderDTOS = invCountHeaderService.submit(invCountHeaderDTOS);
        return Results.success(submittedInvCountHeaderDTOS);
    }

    @ApiOperation(value = "countResultSync")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/countResultSync")
    public ResponseEntity<InvCountHeaderDTO> countResultSync(@RequestBody InvCountHeaderDTO invCountHeaderDTO) {
        // sync result valid object
        validObject(invCountHeaderDTO, InvCountHeader.CountSync.class);
        // sync result
        invCountHeaderService.countResultSync(invCountHeaderDTO);
        return Results.success(invCountHeaderDTO);
    }

    @ApiOperation(value = "Report")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("countingOrderReportDs")
    public ResponseEntity<List<InvCountHeaderDTO>> countingOrderReportDs(@RequestBody InvCountHeaderDTO invCountHeaderDTO) {
        return Results.success(invCountHeaderService.countingOrderReportDs(invCountHeaderDTO));
    }

    @ApiOperation(value = "Submit Approval")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("submitApproval")
    public ResponseEntity<InvCountHeaderDTO> submitApproval(@RequestBody WorkFlowEventDTO workflowEventDTO) {
        return Results.success(invCountHeaderService.submitApproval(workflowEventDTO));
    }
}

