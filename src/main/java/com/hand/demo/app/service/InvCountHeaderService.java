package com.hand.demo.app.service;

import com.hand.demo.api.controller.v1.DTO.InvCountHeaderDTO;
import com.hand.demo.api.controller.v1.DTO.InvCountInfoDTO;
import com.hand.demo.api.controller.v1.DTO.WorkFlowEventDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import com.hand.demo.domain.entity.InvCountHeader;

import java.util.List;

/**
 * (InvCountHeader)应用服务
 *
 * @author
 * @since 2024-11-25 08:42:19
 */
public interface InvCountHeaderService {

    /**
     * 查询数据
     *
     * @param pageRequest     分页参数
    //     * @param invCountHeaders 查询条件
     * @return 返回值
     */
    public Page<List<InvCountHeaderDTO>> list(PageRequest pageRequest, InvCountHeaderDTO invCountHeaderDTO);

    /**
     * 保存数据
     *
     //     * @param invCountHeaders 数据
     */
    InvCountHeaderDTO detail(Long countHeaderId);
    InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> invCountHeaders);
    InvCountInfoDTO manualSaveCheck (List<InvCountHeaderDTO> invCountHeadersDTO);
    List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> invCountHeadersDTO);
    InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> invCountHeaderDTOList);
    List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> invCountHeaderDTOList);
    InvCountInfoDTO countSyncWms(List<InvCountHeaderDTO> invCountHeaderDTOList);
    InvCountHeaderDTO countResultSync(InvCountHeaderDTO invCountHeaderDTO);
    InvCountInfoDTO submitCheck(List<InvCountHeaderDTO> invCountHeaderDTOList);
    List<InvCountHeaderDTO> submit(List<InvCountHeaderDTO> invCountHeaderDTOList);
    List<InvCountHeaderDTO> countingOrderReportDs(InvCountHeaderDTO invCountHeaderDTO);
    InvCountHeaderDTO countingOrderCallBack(WorkFlowEventDTO workFlowEventRequestDTO);
}