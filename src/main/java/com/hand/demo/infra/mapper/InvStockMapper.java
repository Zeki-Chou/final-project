package com.hand.demo.infra.mapper;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvStockDTO;
import com.hand.demo.domain.entity.InvCountHeader;
import io.choerodon.mybatis.common.BaseMapper;
import com.hand.demo.domain.entity.InvStock;

import java.math.BigDecimal;
import java.util.List;

/**
 * (InvStock)应用服务
 *
 * @author Allan
 * @since 2024-11-26 11:13:35
 */
public interface InvStockMapper extends BaseMapper<InvStock> {
    /**
     * 基础查询
     *
     * @param invStock 查询条件
     * @return 返回值
     */
    List<InvStock> selectList(InvStock invStock);

    BigDecimal getSumOnHandQty(InvStockDTO invStockDTO);

    List<InvStock> selectStocksByHeaders(List<InvCountHeaderDTO> headers);
}

