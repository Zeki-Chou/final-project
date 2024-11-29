package com.hand.demo.api.controller.v1;

import com.alibaba.fastjson.JSON;
import com.hand.demo.api.controller.v1.DTO.InvCountHeaderDTO;
import com.hand.demo.api.controller.v1.DTO.InvCountInfoDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.iam.ResourceLevel;
import io.choerodon.mybatis.pagehelper.annotation.SortDefault;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.choerodon.mybatis.pagehelper.domain.Sort;
import io.choerodon.swagger.annotation.Permission;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
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
        return Results.success(invCountHeaderService.countingOrderQueryList(pageRequest, invCountHeaderDTO));
    }

    @ApiOperation(value = "orderExecution")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/execute")
    public ResponseEntity<List<InvCountHeaderDTO>> execute(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeadersDTO) {
        validObject(invCountHeadersDTO);
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeadersDTO);

        invCountHeadersDTO.forEach(item -> item.setTenantId(organizationId));

        InvCountInfoDTO invCountInfoDTO = invCountHeaderService.countingOrderSynchronizeWMS(invCountHeadersDTO);
        if(invCountInfoDTO.getErrorMessage() != null && invCountInfoDTO.getErrorMessage().size() > 0) {
            throw new CommonException(JSON.toJSONString(invCountInfoDTO));
        }

        List<InvCountHeaderDTO> invCountHeaderDTOList = invCountHeaderService.countingOrderExecute(invCountHeadersDTO);
        return Results.success(invCountHeaderDTOList);
    }

    @ApiOperation(value = "orderSave")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping
    public ResponseEntity<InvCountInfoDTO> save(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeadersDTO) {
        validObject(invCountHeadersDTO);
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeadersDTO);
        invCountHeadersDTO.forEach(item -> item.setTenantId(organizationId));
        List<InvCountHeaderDTO> invCountHeaderDTOList = invCountHeaderService.countingOrderSave(invCountHeadersDTO);
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        invCountInfoDTO.setInvCountHeaderDTOList(invCountHeaderDTOList);
        return Results.success(invCountInfoDTO);
    }

    @ApiOperation(value = "orderRemove")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @DeleteMapping
    public ResponseEntity<InvCountInfoDTO> remove(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        SecurityTokenHelper.validToken(invCountHeaders);
        return Results.success(invCountHeaderService.countingOrderRemove(invCountHeaders));
    }

    @ApiOperation(value = "detail")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping
    public ResponseEntity<InvCountHeaderDTO> detail(@PathVariable Long organizationId, Long countHeaderId) {
        return Results.success(invCountHeaderService.countingOrderQueryDetail(countHeaderId));
    }

    @ApiOperation(value = "countResultSync")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping("/resultSync")
    public ResponseEntity<InvCountHeaderDTO> resultSync(@PathVariable Long organizationId, InvCountHeaderDTO invCountHeaderDTO) {
        SecurityTokenHelper.validToken(invCountHeaderDTO);
        return Results.success(invCountHeaderService.countingResultSynchronous(invCountHeaderDTO));
    }
}