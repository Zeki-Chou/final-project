package com.hand.demo.api.controller.v1;

import com.hand.demo.api.dto.InvCountLineDTO;
import io.choerodon.core.domain.Page;
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
import com.hand.demo.app.service.InvCountLineService;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.repository.InvCountLineRepository;
import springfox.documentation.annotations.ApiIgnore;

import java.util.List;

/**
 * (InvCountLine)表控制层
 *
 * @author
 * @since 2024-11-26 08:19:51
 */

@RestController("invCountLineController.v1")
@RequestMapping("/v1/{organizationId}/inv-count-lines")
public class InvCountLineController extends BaseController {

    @Autowired
    private InvCountLineRepository invCountLineRepository;

    @Autowired
    private InvCountLineService invCountLineService;

    @ApiOperation(value = "列表")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping
    public ResponseEntity<Page<InvCountLineDTO>> list(InvCountLineDTO invCountLineDTO, @PathVariable Long organizationId,
                                                   @ApiIgnore @SortDefault(value = InvCountLine.FIELD_COUNT_LINE_ID,
                                                           direction = Sort.Direction.DESC) PageRequest pageRequest) {
        Page<InvCountLineDTO> list = invCountLineService.selectList(pageRequest, invCountLineDTO);
        return Results.success(list);
    }

    @ApiOperation(value = "明细")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping("/{countLineId}/detail")
    public ResponseEntity<InvCountLineDTO> detail(@PathVariable Long countLineId) {
        InvCountLineDTO invCountLineDTO = invCountLineRepository.selectByPrimary(countLineId);
        return Results.success(invCountLineDTO);
    }

    @ApiOperation(value = "创建或更新")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping
    public ResponseEntity<List<InvCountLineDTO>> save(@PathVariable Long organizationId, @RequestBody List<InvCountLineDTO> invCountLineDTOS) {
        validObject(invCountLineDTOS);
        SecurityTokenHelper.validTokenIgnoreInsert(invCountLineDTOS);
        invCountLineDTOS.forEach(item -> item.setTenantId(organizationId));
        invCountLineService.saveData(invCountLineDTOS);
        return Results.success(invCountLineDTOS);
    }

    @ApiOperation(value = "删除")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @DeleteMapping
    public ResponseEntity<?> remove(@RequestBody List<InvCountLineDTO> invCountLineDTOS) {
        SecurityTokenHelper.validToken(invCountLineDTOS);
        invCountLineRepository.batchDeleteByPrimaryKey(invCountLineDTOS);
        return Results.success();
    }

}

