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
 * @since 2024-11-25 10:19:43
 */
public interface InvCountHeaderService {

    List<InvCountHeaderDTO> orderExecution(List<InvCountHeaderDTO> headerDTOList);

    List<InvCountHeaderDTO> orderSubmit(List<InvCountHeaderDTO> headerDTOList);
    /**
     * 查询数据
     *
     * @param pageRequest     分页参数
     * @param invCountHeaders 查询条件
     * @return 返回值
     */
    Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeaders);

    /**
     * 保存数据
     *
     * @param invCountHeaders 数据
     */
    List<InvCountHeaderDTO> saveData(List<InvCountHeaderDTO> invCountHeaders);


    InvCountHeaderDTO detail(Long countHeaderId);

    InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> headerDTOList);

    InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> headerDTOList);

    InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> headerDTOList);

    List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> headerDTOList);

    InvCountInfoDTO submitCheck(List<InvCountHeaderDTO> headerDTOList);

    List<InvCountHeaderDTO> submit(List<InvCountHeaderDTO> headerDTOList);

    InvCountHeaderDTO approvalCallback(WorkFlowEventDTO eventDTO);

    InvCountInfoDTO countSyncWms(List<InvCountHeaderDTO> countSyncWms);

    InvCountHeaderDTO countResultSync(InvCountHeaderDTO countHeaderDTO);

    List<InvCountHeaderDTO> countingOrderReportDs(InvCountHeaderDTO headerDTO);

}

