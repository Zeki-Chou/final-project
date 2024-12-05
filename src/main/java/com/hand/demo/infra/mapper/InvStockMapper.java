package com.hand.demo.infra.mapper;

import com.hand.demo.api.controller.v1.DTO.InvCountHeaderDTO;
import com.hand.demo.api.controller.v1.DTO.InvStockDTO;
import io.choerodon.mybatis.common.BaseMapper;
import com.hand.demo.domain.entity.InvStock;

import java.util.List;

/**
 * (InvStock)应用服务
 *
 * @author
 * @since 2024-11-26 23:10:08
 */
public interface InvStockMapper extends BaseMapper<InvStock> {
    /**
     * 基础查询
     *
     * @param invStock 查询条件
     * @return 返回值
     */
    List<InvStock> selectList(InvStock invStock);
    List<InvStockDTO> checkOnHandQuantity(List<InvCountHeaderDTO> invCountHeaderDTOList);
    List<InvStockDTO> executeBySKU(InvCountHeaderDTO invCountHeaderDTO);
    List<InvStockDTO> executeByLOT(InvCountHeaderDTO invCountHeaderDTO);
    List<InvStockDTO> execute(List<InvCountHeaderDTO> invCountHeaders);
}

