package com.hand.demo.infra.mapper;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvStockDTO;
import io.choerodon.mybatis.common.BaseMapper;
import com.hand.demo.domain.entity.InvStock;

import java.math.BigDecimal;
import java.util.List;

/**
 * (InvStock)应用服务
 *
 * @author
 * @since 2024-11-25 13:43:59
 */
public interface InvStockMapper extends BaseMapper<InvStock> {
    /**
     * 基础查询
     *
     * @param invStock 查询条件
     * @return 返回值
     */
    List<InvStock> selectList(InvStock invStock);

    BigDecimal getTotalQuantity(InvCountHeaderDTO invCountHeaderDTO);

    List<BigDecimal> getQuantities(InvCountHeaderDTO invCountHeaderDTO);

    List<InvStock> getListForQuantity(InvCountHeaderDTO invCountHeaderDTO);
}

