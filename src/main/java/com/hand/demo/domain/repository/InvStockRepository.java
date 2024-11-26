package com.hand.demo.domain.repository;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvStockDTO;
import org.hzero.mybatis.base.BaseRepository;
import com.hand.demo.domain.entity.InvStock;

import java.util.List;

/**
 * (InvStock)资源库
 *
 * @author
 * @since 2024-11-26 13:41:50
 */
public interface InvStockRepository extends BaseRepository<InvStockDTO> {
    /**
     * 查询
     *
     * @param invStockDTO 查询条件
     * @return 返回值
     */
    List<InvStockDTO> selectList(InvStockDTO invStockDTO);

    /**
     * 根据主键查询（可关联表）
     *
     * @param stockId 主键
     * @return 返回值
     */
    InvStockDTO selectByPrimary(Long stockId);

    InvStockDTO selectByHeader(InvCountHeaderDTO invCountHeaderDTO);
}
