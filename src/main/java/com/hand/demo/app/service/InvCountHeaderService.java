package com.hand.demo.app.service;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import com.hand.demo.api.dto.WorkFlowEventDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import com.hand.demo.domain.entity.InvCountHeader;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * (InvCountHeader)应用服务
 *
 * @author
 * @since 2024-11-25 09:59:38
 */
public interface InvCountHeaderService {

    /**
     * 查询数据
     *
     * @param pageRequest     分页参数
     * @param invCountHeaderDTO 查询条件
     * @return 返回值
     */
    Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeaderDTO);

    InvCountHeader detail(Long countHeaderId);


    @Transactional(rollbackFor = Exception.class)
    List<InvCountHeaderDTO> orderExecute(List<InvCountHeaderDTO> invCountHeaderDTOS);

    List<InvCountHeaderDTO> orderSave(List<InvCountHeaderDTO> invCountHeaderDTOS);

    List<InvCountHeaderDTO> orderRemove(List<InvCountHeaderDTO> invCountHeaderDTOS);

    List<InvCountHeaderDTO> orderSubmit(List<InvCountHeaderDTO> invCountHeaderDTOS);

    InvCountHeaderDTO countResultSync(InvCountHeaderDTO invCountHeaderDTO);

    InvCountHeaderDTO submitApproval(WorkFlowEventDTO workflowEventDTO);

    List<InvCountHeaderDTO> countingOrderReportDs(InvCountHeaderDTO invCountHeaderDTOS);
}

