package com.hand.demo.app.service;

import com.hand.demo.api.controller.v1.DTO.InvCountHeaderDTO;
import com.hand.demo.api.controller.v1.DTO.InvCountInfoDTO;
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
    public Page<List<InvCountHeaderDTO>> countingOrderQueryList(PageRequest pageRequest, InvCountHeaderDTO invCountHeaderDTO);

    /**
     * 保存数据
     *
     //     * @param invCountHeaders 数据
     */
    InvCountHeaderDTO countingOrderQueryDetail(Long countHeaderId);
    List<InvCountHeaderDTO> countingOrderSave(List<InvCountHeaderDTO> invCountHeadersDTO);
    InvCountInfoDTO countingOrderRemove(List<InvCountHeaderDTO> invCountHeaders);
    List<InvCountHeaderDTO> countingOrderExecute(List<InvCountHeaderDTO> invCountHeaderDTOList);
    InvCountInfoDTO countingOrderSynchronizeWMS(List<InvCountHeaderDTO> invCountHeaderDTOList);
    InvCountHeaderDTO countingResultSynchronous(InvCountHeaderDTO invCountHeaderDTO);
}