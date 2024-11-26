package com.hand.demo.domain.repository;

import org.hzero.mybatis.base.BaseRepository;
import com.hand.demo.domain.entity.InvBatch;

import java.util.List;

/**
 * (InvBatch)资源库
 *
 * @author
 * @since 2024-11-25 13:43:35
 */
public interface InvBatchRepository extends BaseRepository<InvBatch> {
    /**
     * 查询
     *
     * @param invBatch 查询条件
     * @return 返回值
     */
    List<InvBatch> selectList(InvBatch invBatch);

    /**
     * 根据主键查询（可关联表）
     *
     * @param batchId 主键
     * @return 返回值
     */
    InvBatch selectByPrimary(Long batchId);
}