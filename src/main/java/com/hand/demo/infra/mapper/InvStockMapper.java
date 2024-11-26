package com.hand.demo.infra.mapper;

import com.hand.demo.api.dto.InvStockDTO;
import io.choerodon.mybatis.common.BaseMapper;
import com.hand.demo.domain.entity.InvStock;

import java.util.List;

/**
 * (InvStock)应用服务
 *
 * @author
 * @since 2024-11-26 13:41:50
 */
public interface InvStockMapper extends BaseMapper<InvStockDTO> {
    /**
     * 基础查询
     *
     * @param invStockDTO 查询条件
     * @return 返回值
     */
    List<InvStockDTO> selectList(InvStockDTO invStockDTO);
}

