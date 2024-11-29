package com.hand.demo.app.service;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import com.hand.demo.domain.entity.InvCountHeader;

import java.util.List;

/**
 * (InvCountHeader)应用服务
 *
 * @author azhar.naufal@hand-global.com
 * @since 2024-11-25 11:15:49
 */
public interface InvCountHeaderService {

    /**
     * 查询数据
     *
     * @param pageRequest 分页参数
     * @param invCountHeaders 查询条件
     * @return 返回值
     */
    Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeaders);

    InvCountHeaderDTO detail(Long countHeaderId);

    InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaders);

    /**
     * 保存数据
     * @param invCountHeaders 数据
     */
    List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> invCountHeaders);

    InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> invCountHeaderDTOS);

    InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> invCountHeaderDTOS);

    List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> invCountHeaderDTOS);

    InvCountInfoDTO countSyncWms(List<InvCountHeaderDTO> invCountHeaderDTOS);

    InvCountHeaderDTO countResultSync(InvCountHeaderDTO invCountHeaderDTO);

    InvCountInfoDTO submitCheck(List<InvCountHeaderDTO> invCountHeaderDTOList);
}

