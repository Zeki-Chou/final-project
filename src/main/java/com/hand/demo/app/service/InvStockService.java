package com.hand.demo.app.service;

import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import com.hand.demo.domain.entity.InvStock;

import java.util.List;

/**
 * (InvStock)应用服务
 *
 * @author Allan
 * @since 2024-11-26 11:13:35
 */
public interface InvStockService {

    /**
     * 查询数据
     *
     * @param pageRequest 分页参数
     * @param invStocks   查询条件
     * @return 返回值
     */
    Page<InvStock> selectList(PageRequest pageRequest, InvStock invStocks);

    /**
     * 保存数据
     *
     * @param invStocks 数据
     */
    void saveData(List<InvStock> invStocks);

}
