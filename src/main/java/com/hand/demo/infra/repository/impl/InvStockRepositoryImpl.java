package com.hand.demo.infra.repository.impl;

import com.hand.demo.api.dto.InvStockDTO;
import com.hand.demo.domain.entity.InvCountHeader;
import org.hzero.mybatis.base.impl.BaseRepositoryImpl;
import org.springframework.stereotype.Component;
import com.hand.demo.domain.entity.InvStock;
import com.hand.demo.domain.repository.InvStockRepository;
import com.hand.demo.infra.mapper.InvStockMapper;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

/**
 * (InvStock)资源库
 *
 * @author Allan
 * @since 2024-11-26 11:13:35
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
        if (invStocks.isEmpty()) {
            return null;
        }
        return invStocks.get(0);
    }

    @Override
    public List<BigDecimal> getSumOnHandQty(InvStockDTO invStockDTO) {
        return invStockMapper.getSumOnHandQty(invStockDTO);
    }

    @Override
    public List<InvStock> selectStocksByHeaders(List<InvCountHeader> headers) {
        return invStockMapper.selectStocksByHeaders(headers);
    }

}

