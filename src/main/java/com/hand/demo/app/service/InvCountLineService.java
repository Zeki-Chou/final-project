package com.hand.demo.app.service;

import com.hand.demo.api.dto.InvCountLineDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import com.hand.demo.domain.entity.InvCountLine;

import java.util.List;

/**
 * (InvCountLine)应用服务
 *
 * @author
 * @since 2024-11-26 08:19:51
 */
public interface InvCountLineService {

    /**
     * 查询数据
     *
     * @param pageRequest   分页参数
     * @param invCountLineDTOS 查询条件
     * @return 返回值
     */
    Page<InvCountLineDTO> selectList(PageRequest pageRequest, InvCountLineDTO invCountLineDTOS);

    /**
     * 保存数据
     *
     * @param invCountLineDTOS 数据
     */
    void saveData(List<InvCountLineDTO> invCountLineDTOS);

}

