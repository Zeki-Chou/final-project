package com.hand.demo.api.controller.v1;

import com.alibaba.fastjson.JSON;
import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import com.hand.demo.api.dto.InvCountLineDTO;
import com.hand.demo.domain.entity.InvWarehouse;
import com.hand.demo.domain.repository.InvWarehouseRepository;
import com.hand.demo.infra.constant.Constants;
import com.hand.demo.infra.enums.Enums;
import com.hand.demo.infra.state.InitState;
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
import org.hzero.core.cache.ProcessCacheValue;
import org.hzero.core.util.Results;
import org.hzero.mybatis.helper.SecurityTokenHelper;
import org.json.JSONObject;
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
    public ResponseEntity<?> orderSave(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        invCountHeaders.forEach(header -> validObject(invCountHeaders, InitState.class));
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);
        invCountHeaders.forEach(item -> item.setTenantId(organizationId));

        InvCountInfoDTO invCountInfoDTO = invCountHeaderService.manualSaveCheck(invCountHeaders);
        if (invCountInfoDTO.getErrSize() > 0) {
            return Results.error(invCountInfoDTO);
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
    public ResponseEntity<List<InvCountHeaderDTO>> orderExecution(@RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        return Results.success();
    }

    @ApiOperation(value = "Test WMS sync")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/wms-sync")
    public ResponseEntity<InvCountInfoDTO> testWMSSync(@RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        return Results.success(invCountHeaderService.countSyncWms(invCountHeaders));
    }

    @ApiOperation(value = "Test Submit workflow")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/test-submit")
    public ResponseEntity<List<InvCountHeaderDTO>> testSubmit(@RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        return Results.success(invCountHeaderService.submit(invCountHeaders));
    }

    @ApiOperation(value = "Test Execute")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/test-execute")
    public ResponseEntity<List<InvCountHeaderDTO>> testOrderExecute(@RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        return Results.success(invCountHeaderService.execute(invCountHeaders));
    }

    @ApiOperation(value = "Test Execute Check")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping("/test-execute")
    public ResponseEntity<InvCountInfoDTO> testExecuteCheck(@RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        return Results.success(invCountHeaderService.executeCheck(invCountHeaders));
    }


}

