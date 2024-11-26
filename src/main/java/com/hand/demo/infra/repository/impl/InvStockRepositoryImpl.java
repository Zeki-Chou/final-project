package com.hand.demo.infra.repository.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvStockDTO;
import org.apache.commons.collections.CollectionUtils;
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
 * @author
 * @since 2024-11-26 13:41:51
 */
@Component
public class InvStockRepositoryImpl extends BaseRepositoryImpl<InvStockDTO> implements InvStockRepository {
    @Resource
    private InvStockMapper invStockMapper;
    @Override
    public List<InvStockDTO> selectList(InvStockDTO invStockDTO) {
        return invStockMapper.selectList(invStockDTO);
    }

    @Override
    public InvStockDTO selectByPrimary(Long stockId) {
        InvStockDTO invStockDTO = new InvStockDTO();
        invStockDTO.setStockId(stockId);
        List<InvStockDTO> invStockDTOS = invStockMapper.selectList(invStockDTO);
        if (invStockDTOS.size() == 0) {
            return null;
        }
        return invStockDTOS.get(0);
    }

    @Override
    public InvStockDTO selectByHeader(InvCountHeaderDTO invCountHeaderDTO) {
        InvStockDTO invStockDTO = new InvStockDTO();
        invStockDTO.setTenantId(invCountHeaderDTO.getTenantId());
        invStockDTO.setCompanyId(invCountHeaderDTO.getCompanyId());
        invStockDTO.setDepartmentId(invCountHeaderDTO.getDepartmentId());
        invStockDTO.setWarehouseId(invCountHeaderDTO.getWarehouseId());
        invStockDTO.setMaterialIds(invCountHeaderDTO.getSnapshotMaterialIds());
        invStockDTO.setUnitQuantity(new BigDecimal(1));
        List<InvStockDTO> invStockDTOS = invStockMapper.selectList(invStockDTO);
        if (invStockDTOS.size() == 0) {
            return null;
        }
        return invStockDTOS.get(0);
    }

}

