package com.hand.demo.api.controller.v1;

import com.hand.demo.api.controller.v1.DTO.InvCountHeaderDTO;
import com.hand.demo.api.controller.v1.DTO.InvCountInfoDTO;
import io.choerodon.core.domain.Page;
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

//    @ApiOperation(value = "列表")
//    @Permission(level = ResourceLevel.ORGANIZATION)
//    @GetMapping
//    public ResponseEntity<Page<InvCountHeader>> list(InvCountHeader invCountHeader, @PathVariable Long organizationId,
//                                                     @ApiIgnore @SortDefault(value = InvCountHeader.FIELD_COUNT_HEADER_ID,
//                                                             direction = Sort.Direction.DESC) PageRequest pageRequest) {
//        Page<InvCountHeader> list = invCountHeaderService.selectList(pageRequest, invCountHeader);
//        return Results.success(list);
//    }
//
//    @ApiOperation(value = "明细")
//    @Permission(level = ResourceLevel.ORGANIZATION)
//    @GetMapping("/{countHeaderId}/detail")
//    public ResponseEntity<InvCountHeader> detail(@PathVariable Long countHeaderId) {
//        InvCountHeader invCountHeader = invCountHeaderRepository.selectByPrimary(countHeaderId);
//        return Results.success(invCountHeader);
//    }

    @ApiOperation(value = "List Query")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping("/listQuery")
    public ResponseEntity<Page<List<InvCountHeaderDTO>>> selectList(InvCountHeaderDTO invCountHeaderDTO, @PathVariable Long organizationId,
                                                                    @ApiParam(hidden = true) @ApiIgnore @SortDefault(value = InvCountHeaderDTO.FIELD_CREATION_DATE,
                                                                            direction = Sort.Direction.DESC) PageRequest pageRequest) {
        return Results.success(invCountHeaderService.queryList(pageRequest, invCountHeaderDTO));
    }

    @ApiOperation(value = "Counting Order Save")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping
    public ResponseEntity<InvCountInfoDTO> save(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeadersDTO) {
        validObject(invCountHeadersDTO);
//        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeadersDTO);
        invCountHeadersDTO.forEach(item -> item.setTenantId(organizationId));
        List<InvCountHeaderDTO> invCountHeaderDTOList = invCountHeaderService.saveData(invCountHeadersDTO);
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        invCountInfoDTO.setInvCountHeaderDTOList(invCountHeaderDTOList);
        return Results.success(invCountInfoDTO);
    }

    @ApiOperation(value = "Counting Order Remove")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @DeleteMapping
    public ResponseEntity<InvCountInfoDTO> remove(@RequestBody List<InvCountHeaderDTO> invCountHeaders) {
//        SecurityTokenHelper.validToken(invCountHeaders);
        return Results.success(invCountHeaderService.orderRemove(invCountHeaders));
    }
}

