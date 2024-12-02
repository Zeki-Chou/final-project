package com.hand.demo.api.controller.v1;

import com.alibaba.fastjson.JSON;
import com.hand.demo.api.controller.v1.DTO.InvCountHeaderDTO;
import com.hand.demo.api.controller.v1.DTO.InvCountInfoDTO;
import com.hand.demo.api.controller.v1.DTO.InvCountLineDTO;
import com.hand.demo.api.controller.v1.DTO.WorkFlowEventDTO;
import com.hand.demo.domain.entity.InvCountLine;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.iam.ResourceLevel;
import io.choerodon.mybatis.pagehelper.annotation.SortDefault;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.choerodon.mybatis.pagehelper.domain.Sort;
import io.choerodon.swagger.annotation.Permission;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.hzero.boot.platform.lov.annotation.ProcessLovValue;
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

import java.util.List;

/**
 * (InvCountHeader)表控制层
 *
 * @author
 * @since 2024-11-25 08:42:19
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
    @GetMapping("/listQuery")
    public ResponseEntity<Page<List<InvCountHeaderDTO>>> selectList(InvCountHeaderDTO invCountHeaderDTO, @PathVariable Long organizationId,
                                                                    @ApiParam(hidden = true) @ApiIgnore @SortDefault(value = InvCountHeaderDTO.FIELD_CREATION_DATE,
                                                                            direction = Sort.Direction.DESC) PageRequest pageRequest) {
        return Results.success(invCountHeaderService.list(pageRequest, invCountHeaderDTO));
    }

    @ApiOperation(value = "orderExecution")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/execute")
    public ResponseEntity<List<InvCountHeaderDTO>> execute(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeadersDTO) {
        validObject(invCountHeadersDTO);
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeadersDTO);
        invCountHeadersDTO.forEach(item -> item.setTenantId(organizationId));

//      countingOrderSaveVerification
        InvCountInfoDTO invCountInfoDTO = invCountHeaderService.manualSaveCheck(invCountHeadersDTO);
        if(invCountInfoDTO.getErrorMessage() != null && invCountInfoDTO.getErrorMessage().size() > 0) {
            throw new CommonException(JSON.toJSONString(invCountInfoDTO));
        }

//      Counting order save
        invCountHeaderService.manualSave(invCountHeadersDTO);

//      Counting order execute verification
        InvCountInfoDTO invCountInfoDTOExecute = invCountHeaderService.executeCheck(invCountHeadersDTO);
        if(invCountInfoDTO.getErrorMessage() != null && invCountInfoDTO.getErrorMessage().size() > 0) {
            throw new CommonException(JSON.toJSONString(invCountInfoDTO));
        }

//      Counting order execute
        invCountHeaderService.execute(invCountHeadersDTO);

//      Counting order synchronization
        invCountHeaderService.countSyncWms(invCountHeadersDTO);
        return Results.success(invCountHeadersDTO);
    }

    @ApiOperation(value = "orderSave")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/orderSave")
    public ResponseEntity<InvCountInfoDTO> save(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeadersDTO) {
        validObject(invCountHeadersDTO);
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeadersDTO);
        invCountHeadersDTO.forEach(item -> item.setTenantId(organizationId));

//      countingOrderSaveVerification
        InvCountInfoDTO invCountInfoDTO = invCountHeaderService.manualSaveCheck(invCountHeadersDTO);
        if(invCountInfoDTO.getErrorMessage() != null && invCountInfoDTO.getErrorMessage().size() > 0) {
            throw new CommonException(JSON.toJSONString(invCountInfoDTO));
        }

//      set error message into null, if there is no error message
        invCountInfoDTO.setErrorMessage(null);

//      countingOrderSave
        List<InvCountHeaderDTO> invCountHeaderDTOList = invCountHeaderService.manualSave(invCountHeadersDTO);
        invCountInfoDTO.setInvCountHeaderDTOList(invCountHeaderDTOList);
        return Results.success(invCountInfoDTO);
    }

    @ApiOperation(value = "orderRemove")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @DeleteMapping("/orderRemove")
    public ResponseEntity<InvCountInfoDTO> remove(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        SecurityTokenHelper.validToken(invCountHeaders);
        return Results.success(invCountHeaderService.checkAndRemove(invCountHeaders));
    }

    @ApiOperation(value = "detail")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping("/detail")
    public ResponseEntity<InvCountHeaderDTO> detail(@PathVariable Long organizationId, Long countHeaderId) {
        return Results.success(invCountHeaderService.detail(countHeaderId));
    }

    @ApiOperation(value = "countingOrderReportDs")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping("/invCountHeaderDTO")
    @ProcessLovValue(targetField = BaseConstants.FIELD_BODY)
    public ResponseEntity<List<InvCountHeaderDTO>> countingOrderReport(@PathVariable Long organizationId, InvCountHeaderDTO invCountHeaderDTO) {
        return Results.success(invCountHeaderService.countingOrderReportDs(invCountHeaderDTO));
    }

    @ApiOperation(value = "countResultSync")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PutMapping("/resultSync")
    public ResponseEntity<InvCountHeaderDTO> resultSync(@PathVariable Long organizationId, @RequestBody InvCountHeaderDTO invCountHeaderDTO) {
        SecurityTokenHelper.validToken(invCountHeaderDTO);
        return Results.success(invCountHeaderService.countResultSync(invCountHeaderDTO));
    }

    @ApiOperation(value = "countOrderCallBack")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/callback")
    public ResponseEntity<InvCountHeaderDTO> countOrderCallBack(@PathVariable Long organizationId, @RequestBody WorkFlowEventDTO workFlowEventDTO) {
        return Results.success(invCountHeaderService.countingOrderCallBack(workFlowEventDTO));
    }

    @ApiOperation(value = "countOrderSubmit")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PutMapping("/orderSubmit")
    public ResponseEntity<List<InvCountHeaderDTO>> countOrderSubmit(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeadersDTO) {
        SecurityTokenHelper.validToken(invCountHeadersDTO);

        //  countingOrderSaveVerification
        InvCountInfoDTO invCountInfoDTO = invCountHeaderService.manualSaveCheck(invCountHeadersDTO);
        if(invCountInfoDTO.getErrorMessage() != null && invCountInfoDTO.getErrorMessage().size() > 0) {
            throw new CommonException(JSON.toJSONString(invCountInfoDTO));
        }

        //  Counting order save
        invCountHeaderService.manualSave(invCountHeadersDTO);

        //  countingOrderSubmitVerification
        InvCountInfoDTO invCountInfoDTOSubmit = invCountHeaderService.submitCheck(invCountHeadersDTO);
        if(invCountInfoDTOSubmit.getErrorMessage() != null && invCountInfoDTOSubmit.getErrorMessage().size() > 0) {
            throw new CommonException(JSON.toJSONString(invCountInfoDTOSubmit));
        }

        //  Counting order submit
        return Results.success(invCountHeaderService.submit(invCountHeadersDTO));
    }
}