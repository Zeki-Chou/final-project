package com.hand.demo.api.controller.v1;

import com.alibaba.fastjson.JSON;
import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.iam.ResourceLevel;
import io.choerodon.mybatis.pagehelper.annotation.SortDefault;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.choerodon.mybatis.pagehelper.domain.Sort;
import io.choerodon.swagger.annotation.Permission;
import io.swagger.annotations.ApiOperation;
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
                                                     @ApiIgnore @SortDefault(value = InvCountHeader.FIELD_COUNT_HEADER_ID,
                                                             direction = Sort.Direction.DESC) PageRequest pageRequest) {
        Page<InvCountHeaderDTO> list = invCountHeaderService.selectList(pageRequest, invCountHeader);
        return Results.success(list);
    }

    @ApiOperation(value = "Get Detail Header Exam")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping("/{countHeaderId}/detail")
    public ResponseEntity<InvCountHeader> detail(@PathVariable Long countHeaderId) {
        InvCountHeader invCountHeader = invCountHeaderRepository.selectByPrimary(countHeaderId);
        return Results.success(invCountHeader);
    }

    @ApiOperation(value = "Save Header Exam")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping
    public ResponseEntity<List<InvCountHeaderDTO>> save(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        validObject(invCountHeaders, InvCountHeaderDTO.class);
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);
        invCountHeaders.forEach(item -> item.setTenantId(organizationId));
        InvCountInfoDTO invCountInfoDTO = invCountHeaderService.manualSaveCheck(invCountHeaders);
        if (invCountInfoDTO.getErrMsg() != null) {
            throw new CommonException(JSON.toJSONString(invCountInfoDTO.getListErrMsg()));
        }
//        invCountHeaderService.saveData(invCountHeaders);
        return Results.success(invCountHeaders);
    }

    @ApiOperation(value = "Remove Header Exam")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @DeleteMapping
    public ResponseEntity<?> remove(@RequestBody List<InvCountHeader> invCountHeaders) {
        SecurityTokenHelper.validToken(invCountHeaders);
        invCountHeaderRepository.batchDeleteByPrimaryKey(invCountHeaders);
        return Results.success();
    }

}

