package com.hand.demo.app.service;

import com.hand.demo.api.dto.InvCountLineDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import com.hand.demo.domain.entity.InvCountLine;

import java.util.List;

/**
 * (InvCountLine)应用服务
 *
 * @author Allan
 * @since 2024-11-25 10:30:32
 */
public interface InvCountLineService {

    /**
     * 查询数据
     *
     * @param pageRequest   分页参数
     * @param invCountLine 查询条件
     * @return 返回值
     */
    Page<InvCountLine> selectList(PageRequest pageRequest, InvCountLineDTO invCountLine);

    /**
     * 保存数据
     *
     * @param invCountLines 数据
     */
    void saveData(List<InvCountLineDTO> invCountLines);
}

