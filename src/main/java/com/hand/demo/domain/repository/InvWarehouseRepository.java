package com.hand.demo.domain.repository;

import org.hzero.mybatis.base.BaseRepository;
import com.hand.demo.domain.entity.InvWarehouse;

import java.util.List;

/**
 * (InvWarehouse)资源库
 *
 * @author Allan
 * @since 2024-11-25 13:59:17
 */
public interface InvWarehouseRepository extends BaseRepository<InvWarehouse> {
    /**
     * 查询
     *
     * @param invWarehouse 查询条件
     * @return 返回值
     */
    List<InvWarehouse> selectList(InvWarehouse invWarehouse);

    /**
     * 根据主键查询（可关联表）
     *
     * @param warehouseId 主键
     * @return 返回值
     */
    InvWarehouse selectByPrimary(Long warehouseId);
}
