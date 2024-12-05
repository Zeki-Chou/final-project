package com.hand.demo.infra.repository.impl;

import com.hand.demo.api.controller.v1.DTO.InvCountHeaderDTO;
import com.hand.demo.api.controller.v1.DTO.InvStockDTO;
import org.apache.commons.collections.CollectionUtils;
import org.hzero.mybatis.base.impl.BaseRepositoryImpl;
import org.springframework.stereotype.Component;
import com.hand.demo.domain.entity.InvStock;
import com.hand.demo.domain.repository.InvStockRepository;
import com.hand.demo.infra.mapper.InvStockMapper;

import javax.annotation.Resource;
import java.util.List;

/**
 * (InvStock)资源库
 *
 * @author
 * @since 2024-11-26 23:10:08
 */
@Component
public class InvStockRepositoryImpl extends BaseRepositoryImpl<InvStock> implements InvStockRepository {
    @Resource
    private InvStockMapper invStockMapper;

    @Override
    public List<InvStock> selectList(InvStock invStock) {
        return invStockMapper.selectList(invStock);
    }

    @Override
    public InvStock selectByPrimary(Long stockId) {
        InvStock invStock = new InvStock();
        invStock.setStockId(stockId);
        List<InvStock> invStocks = invStockMapper.selectList(invStock);
        if (invStocks.size() == 0) {
            return null;
        }
        return invStocks.get(0);
    }

    @Override
    public List<InvStockDTO> checkOnHandQuantity(List<InvCountHeaderDTO> invCountHeaderDTOList) {
        return invStockMapper.checkOnHandQuantity(invCountHeaderDTOList);
    }

    @Override
    public List<InvStockDTO> executeBySKU(InvCountHeaderDTO invCountHeaderDTO) {
        return invStockMapper.executeBySKU(invCountHeaderDTO);
    }

    @Override
    public List<InvStockDTO> executeByLOT(InvCountHeaderDTO invCountHeaderDTO) {
        return invStockMapper.executeByLOT(invCountHeaderDTO);
    }

    @Override
    public List<InvStockDTO> execute(List<InvCountHeaderDTO> invCountHeaders) {
        return invStockMapper.execute(invCountHeaders);
    }
}

