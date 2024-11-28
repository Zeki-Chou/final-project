package com.hand.demo.app.service;

import com.hand.demo.api.dto.InvCountHeaderDTO;
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

    /**
     * 保存数据
     * @param invCountHeaders 数据
     */
    void manualSave(List<InvCountHeaderDTO> invCountHeaders);

    void checkAndRemove(List<InvCountHeaderDTO> invCountHeaderDTOS);

    void manualSaveCheck(List<InvCountHeaderDTO> invCountHeaders);

    void executeCheck(List<InvCountHeaderDTO> invCountHeaderDTOS);
}

