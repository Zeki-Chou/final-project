package com.hand.demo.app.service;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import com.hand.demo.api.dto.WorkFlowEventDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import com.hand.demo.domain.entity.InvCountHeader;

import java.util.List;

/**
 * (InvCountHeader)应用服务
 *
 * @author
 * @since 2024-11-25 09:59:38
 */
public interface InvCountHeaderService {

    InvCountInfoDTO  submitCheck(List<InvCountHeaderDTO> invCountHeaderDTOS);

    /**
     * 查询数据
     *
     * @param pageRequest     分页参数
     * @param invCountHeaderDTO 查询条件
     * @return 返回值
     */
    Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeaderDTO);

    InvCountHeader detail(Long countHeaderId);

    /**
     * 保存数据
     *
     * @param invCountHeaderDTOS 数据
     */
    List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> invCountHeaderDTOS);

    InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaderDTOS);

    InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> invCountHeaderDTOS);

    InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> invCountHeaderDTOS);

    InvCountInfoDTO countSyncWMS(List<InvCountHeaderDTO> invCountHeaderDTOS);

    InvCountHeaderDTO countResultSync(InvCountHeaderDTO invCountHeaderDTO);

    List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> invCountHeaderDTOS);

    List<InvCountHeaderDTO> executeAndCountSyncWMS(List<InvCountHeaderDTO> invCountHeaderDTOS);

    List<InvCountHeaderDTO> submit(List<InvCountHeaderDTO> invCountHeaderDTOS);

    InvCountHeaderDTO submitApproval(WorkFlowEventDTO workflowEventDTO);

    List<InvCountHeaderDTO> countingOrderReportDs(InvCountHeaderDTO invCountHeaderDTOS);
}

