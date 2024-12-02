package com.hand.demo.api.controller.v1;

import com.alibaba.fastjson.JSON;
import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import com.hand.demo.api.dto.WorkFlowEventDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.iam.ResourceLevel;
import io.choerodon.mybatis.pagehelper.annotation.SortDefault;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.choerodon.mybatis.pagehelper.domain.Sort;
import io.choerodon.swagger.annotation.Permission;
import io.swagger.annotations.ApiOperation;
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * (InvCountHeader)表控制层
 *
 * @author
 * @since 2024-11-25 08:22:37
 */

@RestController("invCountHeaderController.v1")
@RequestMapping("/v1/{organizationId}/inv-count-headers-exam")
public class InvCountHeaderController extends BaseController {

    @Autowired
    private InvCountHeaderRepository invCountHeaderRepository;

    @Autowired
    private InvCountHeaderService invCountHeaderService;

    @ApiOperation(value = "Get List Header Exam")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping
    public ResponseEntity<Page<InvCountHeaderDTO>> list(InvCountHeaderDTO invCountHeader, @PathVariable Long organizationId,
                                                     @ApiIgnore @SortDefault(sort = InvCountHeader.FIELD_CREATED_BY,
                                                             value = InvCountHeader.FIELD_COUNT_HEADER_ID,
                                                             direction = Sort.Direction.DESC) PageRequest pageRequest) {
        Page<InvCountHeaderDTO> list = invCountHeaderService.selectList(pageRequest, invCountHeader);
        return Results.success(list);
    }

    @ApiOperation(value = "Get Detail Header Exam")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping("/{countHeaderId}/detail")
    @ProcessCacheValue
    public ResponseEntity<InvCountHeaderDTO> detail(@PathVariable Long countHeaderId) {
//        InvCountHeader invCountHeader = invCountHeaderRepository.selectByPrimary(countHeaderId);
        InvCountHeaderDTO invCountHeader = invCountHeaderService.detail(countHeaderId);
        return Results.success(invCountHeader);
    }

    @ApiOperation(value = "Save Header Exam")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping
    public ResponseEntity<List<InvCountHeaderDTO>> orderExecution(@PathVariable Long organizationId,
                                                                  @RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        validObject(invCountHeaders);
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);
        invCountHeaders.forEach(item -> item.setTenantId(organizationId));
        InvCountInfoDTO invCountInfoSaveVerif = invCountHeaderService.manualSaveCheck(invCountHeaders);
        if (!invCountInfoSaveVerif.getErrMsg().isEmpty()) {
            throw new CommonException(JSON.toJSONString(invCountInfoSaveVerif.getListErrMsg()));
        }
        List<InvCountHeaderDTO> manualSaveDTO =  invCountHeaderService.manualSave(invCountHeaders);

        InvCountInfoDTO invCountExecuteVerif = invCountHeaderService.executeCheck(manualSaveDTO);
        if (!invCountExecuteVerif.getErrMsg().isEmpty()) {
            throw new CommonException(JSON.toJSONString(invCountExecuteVerif.getErrMsg()));
        }

        List<InvCountHeaderDTO> executeDTO =  invCountHeaderService.execute(invCountExecuteVerif.getListSuccessMsg());

        InvCountInfoDTO countSyncWms = invCountHeaderService.countSyncWms(executeDTO);
        if (!countSyncWms.getErrMsg().isEmpty()) {
            throw new CommonException(JSON.toJSONString(invCountExecuteVerif.getListErrMsg()));
        }

        return Results.success(executeDTO);
    }


    @ApiOperation(value = "Submit counting result")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/submit")
    public ResponseEntity<List<InvCountHeaderDTO>> orderSubmit(@PathVariable Long organizationId,
                                                                  @RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        validObject(invCountHeaders);
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);
        invCountHeaders.forEach(item -> item.setTenantId(organizationId));
        InvCountInfoDTO invCountInfoSaveVerif = invCountHeaderService.manualSaveCheck(invCountHeaders);
        if (!invCountInfoSaveVerif.getErrMsg().isEmpty()) {
            throw new CommonException(JSON.toJSONString(invCountInfoSaveVerif.getListErrMsg()));
        }

        List<InvCountHeaderDTO> manualSaveDTO =  invCountHeaderService.manualSave(invCountHeaders);

        InvCountInfoDTO invCountExecuteVerif = invCountHeaderService.submitCheck(manualSaveDTO);
        if (!invCountExecuteVerif.getErrMsg().isEmpty()) {
            throw new CommonException(JSON.toJSONString(invCountExecuteVerif.getErrMsg()));
        }

        List<InvCountHeaderDTO> executeDTO =  invCountHeaderService.submit(invCountExecuteVerif.getListSuccessMsg());

        return Results.success(executeDTO);
    }

    @ApiOperation(value = "Submit Call Back")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/submit-callback")
    public ResponseEntity<InvCountHeader> submitCallback(@PathVariable Long organizationId,
                                                           @RequestBody WorkFlowEventDTO workFlowEventDTO) {
        return Results.success(invCountHeaderService.submitCallback(organizationId, workFlowEventDTO));
    }

    @ApiOperation(value = "countResultSync")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/countResultSync")
    public ResponseEntity<InvCountHeader> countResultSync(@PathVariable Long organizationId,
                                                         @RequestBody InvCountHeaderDTO invCountHeaderDTO) {
        return Results.success(invCountHeaderService.countResultSync(invCountHeaderDTO));
    }

    @ApiOperation(value = "Remove Header Exam")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @DeleteMapping
    public ResponseEntity<?> remove(@RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        SecurityTokenHelper.validToken(invCountHeaders);
        InvCountInfoDTO invCountInfoDTO = invCountHeaderService.checkAndRemove(invCountHeaders);
        if (!invCountInfoDTO.getErrMsg().isEmpty()) {
            throw new CommonException(JSON.toJSONString(invCountInfoDTO.getListErrMsg()));
        }
        List<InvCountHeader> headers = invCountHeaders.stream().collect(Collectors.toList());
        invCountHeaderRepository.batchDeleteByPrimaryKey(headers);
        return Results.success();
    }
}

