package com.hand.demo.app.service.impl;

import com.hand.demo.infra.util.Utils;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.hzero.core.base.BaseConstants;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvWarehouseService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvWarehouse;
import com.hand.demo.domain.repository.InvWarehouseRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * (InvWarehouse)应用服务
 *
 * @author Allan
 * @since 2024-11-25 13:59:17
 */
@Service
public class InvWarehouseServiceImpl implements InvWarehouseService {
    @Autowired
    private InvWarehouseRepository invWarehouseRepository;

    @Override
    public Page<InvWarehouse> selectList(PageRequest pageRequest, InvWarehouse invWarehouse) {
        return PageHelper.doPageAndSort(pageRequest, () -> invWarehouseRepository.selectList(invWarehouse));
    }

    @Override
    public void saveData(List<InvWarehouse> invWarehouses) {
        List<InvWarehouse> insertList = invWarehouses.stream().filter(line -> line.getWarehouseId() == null).collect(Collectors.toList());
        List<InvWarehouse> updateList = invWarehouses.stream().filter(line -> line.getWarehouseId() != null).collect(Collectors.toList());
        invWarehouseRepository.batchInsertSelective(insertList);
        invWarehouseRepository.batchUpdateByPrimaryKeySelective(updateList);
    }

    @Override
    public List<Long> findByIds(List<Long> ids) {
        String warehouseIds = Utils.generateStringIds(ids);
        List<InvWarehouse> warehouses = invWarehouseRepository.selectByIds(warehouseIds);
        return warehouses.stream().map(InvWarehouse::getWarehouseId).collect(Collectors.toList());
    }

    @Override
    public List<Long> getWMSWarehouseIds(){
        InvWarehouse warehouse = new InvWarehouse();
        warehouse.setIsWmsWarehouse(BaseConstants.Flag.YES);
        return invWarehouseRepository
                .selectList(warehouse)
                .stream()
                .map(InvWarehouse::getWarehouseId)
                .collect(Collectors.toList());
    }

}

