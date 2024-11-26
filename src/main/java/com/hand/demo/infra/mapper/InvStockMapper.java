package com.hand.demo.infra.mapper;

import io.choerodon.mybatis.common.BaseMapper;
import com.hand.demo.domain.entity.InvStock;

import java.util.List;

/**
 * (InvStock)应用服务
 *
 * @author muhammad.azzam@hand-global.com
 * @since 2024-11-25 11:50:44
 */
public interface InvStockMapper extends BaseMapper<InvStock> {
    /**
     * 基础查询
     *
     * @param invStock 查询条件
     * @return 返回值
     */
    List<InvStock> selectList(InvStock invStock);
}

